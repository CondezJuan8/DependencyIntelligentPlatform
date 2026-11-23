package com.dependencyintelligence.scan.infrastructure.parser;

import static com.dependencyintelligence.repository.application.RepositorySourceLimits.*;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Parses npm lockfile v2/v3 as data; never runs npm or project scripts. */
@Component
public final class BoundedNpmLockReader implements NpmLockfileReader {
    private static final int MAX_LOCK_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PACKAGES = 30_000;
    private static final int MAX_TOTAL_LOCK_BYTES = 25 * 1024 * 1024;
    private static final Pattern EXACT_VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");
    private static final List<String> ROOT_DEPENDENCY_SECTIONS = List.of("dependencies", "devDependencies", "optionalDependencies", "peerDependencies");
    private static final List<String> PACKAGE_DEPENDENCY_SECTIONS = List.of("dependencies", "optionalDependencies", "peerDependencies");

    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override public ManifestAnalysis read(InputStream input) {
        var locks = readLocks(input);
        var dependencies = new ArrayList<ManifestDependency>();
        int unresolved = 0;
        int valid = 0;
        IllegalArgumentException firstFailure = null;
        for (var lock : locks.entrySet()) {
            String prefix = locks.size() == 1 ? "" : lock.getKey() + "/";
            ManifestAnalysis analysis;
            try {
                analysis = parseLock(lock.getValue());
            } catch (IllegalArgumentException failure) {
                if (firstFailure == null) firstFailure = failure;
                unresolved++;
                continue;
            }
            valid++;
            if (analysis.dependencies().size() > MAX_PACKAGES - dependencies.size())
                throw invalid("Los lockfiles superan el límite total de 30.000 paquetes.");
            unresolved += analysis.unresolvedCount();
            analysis.dependencies().forEach(item -> dependencies.add(new ManifestDependency(prefix + item.manifestPath(),
                item.coordinates(), item.scope(), item.dependencyType())));
        }
        if (valid == 0 && firstFailure != null) throw firstFailure;
        return new ManifestAnalysis(dependencies, locks.size(), unresolved);
    }

    private ManifestAnalysis parseLock(byte[] lock) {
        try {
            JsonNode root = json.readTree(lock);
            int version = root.path("lockfileVersion").asInt(0);
            if (version < 2 || version > 3) throw invalid("Solo se admite package-lock v2 o v3.");
            JsonNode packages = root.path("packages");
            if (!packages.isObject() || packages.size() > MAX_PACKAGES) throw invalid("Mapa packages inválido o demasiado grande.");

            Map<String, PackageEntry> entries = new HashMap<>();
            int unresolvedPackages = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = packages.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String path = field.getKey();
                JsonNode node = field.getValue();
                if (!isInstalledPath(path) || node.path("link").asBoolean(false)) continue;
                String name = packageName(path, node);
                String versionText = node.path("version").asText();
                if (name.isBlank() || name.length() > 512 || versionText.length() > 255
                        || !EXACT_VERSION.matcher(versionText).matches()) {
                    unresolvedPackages++;
                    continue;
                }
                String encodedName = purlName(name);
                String namespace = name.startsWith("@") && name.contains("/") ? name.substring(0, name.indexOf('/')) : null;
                var coordinates = new PackageCoordinates("npm", namespace, name, versionText,
                    "pkg:npm/" + encodedName + "@" + versionText);
                String scope = node.path("optional").asBoolean(false) ? "optional"
                    : node.path("dev").asBoolean(false) ? "development" : "runtime";
                entries.put(path, new PackageEntry(path, node, coordinates, scope));
            }

            JsonNode rootPackage = packages.path("");
            Set<String> directPaths = new LinkedHashSet<>();
            int unresolved = unresolvedPackages + connectDependencies("", rootPackage, entries, directPaths);
            for (var workspace : packages.properties()) {
                if (!workspace.getKey().isEmpty() && !isInstalledPath(workspace.getKey()))
                    unresolved += connectDependencies(workspace.getKey(), workspace.getValue(), entries, directPaths);
            }

            Deque<String> pending = new ArrayDeque<>(directPaths);
            Set<String> reachable = new HashSet<>();
            while (!pending.isEmpty()) {
                String parentPath = pending.removeFirst();
                if (!reachable.add(parentPath)) continue;
                PackageEntry parent = entries.get(parentPath);
                if (parent == null) continue;
                for (String name : dependencyNames(parent.node(), PACKAGE_DEPENDENCY_SECTIONS)) {
                    String target = resolveInstalledPath(parentPath, name, entries);
                    if (target == null) { unresolved++; continue; }
                    if (!reachable.contains(target)) pending.addLast(target);
                }
            }

            List<ManifestDependency> dependencies = new ArrayList<>();
            for (PackageEntry entry : entries.values()) {
                String path = entry.path();
                dependencies.add(new ManifestDependency(path, entry.coordinates(), entry.scope(),
                    directPaths.contains(path) ? "DIRECT" : reachable.contains(path) ? "TRANSITIVE" : "UNKNOWN"));
            }
            dependencies.sort((left, right) -> left.manifestPath().compareTo(right.manifestPath()));
            int unreachable = Math.max(0, entries.size() - reachable.size());
            return new ManifestAnalysis(dependencies, 1, unresolved + unreachable);
        } catch (IOException failure) {
            throw new IllegalArgumentException("package-lock.json no es JSON válido.", failure);
        }
    }

    private static int connectDependencies(String parentPath, JsonNode parent, Map<String, PackageEntry> entries,
            Set<String> directPaths) {
        Set<String> names = dependencyNames(parent, ROOT_DEPENDENCY_SECTIONS);
        int unresolved = 0;
        for (String name : names) {
            String targetPath = resolveInstalledPath(parentPath, name, entries);
            if (targetPath == null) { unresolved++; continue; }
            directPaths.add(targetPath);
        }
        return unresolved;
    }

    private static Set<String> dependencyNames(JsonNode parent, List<String> sections) {
        Set<String> names = new LinkedHashSet<>();
        for (String section : sections) {
            JsonNode dependencies = parent.path(section);
            if (!dependencies.isObject()) continue;
            Iterator<String> fields = dependencies.fieldNames();
            while (fields.hasNext()) names.add(fields.next());
        }
        return names;
    }

    /** Resolve according to Node's nearest node_modules lookup from the owning package. */
    private static String resolveInstalledPath(String ownerPath, String dependencyName, Map<String, PackageEntry> entries) {
        String base = ownerPath;
        while (true) {
            String candidate = base.isEmpty() ? "node_modules/" + dependencyName
                : base + "/node_modules/" + dependencyName;
            if (entries.containsKey(candidate)) return candidate;
            int ancestor = base.lastIndexOf("/node_modules/");
            if (ancestor >= 0) base = base.substring(0, ancestor);
            else if (base.startsWith("node_modules/")) base = "";
            else if (!base.isEmpty()) {
                int slash = base.lastIndexOf('/');
                base = slash < 0 ? "" : base.substring(0, slash);
            }
            else return null;
        }
    }

    private static String packageName(String path, JsonNode node) {
        String explicit = node.path("name").asText("");
        if (!explicit.isBlank()) return explicit;
        int marker = path.lastIndexOf("node_modules/");
        if (marker < 0) return "";
        return path.substring(marker + "node_modules/".length());
    }

    private static String purlName(String name) {
        return Arrays.stream(name.split("/", -1))
            .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
            .reduce((left, right) -> left + "/" + right).orElse("");
    }

    private static boolean isInstalledPath(String path) {
        return path.startsWith("node_modules/") || path.contains("/node_modules/");
    }

    private static Map<String, byte[]> readLocks(InputStream input) {
        var locks = new LinkedHashMap<String, byte[]>();
        int lockBytes = 0;
        long expanded = 0;
        int entries = 0;
        int files = 0;
        byte[] buffer = new byte[16 * 1024];
        try (var zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ARCHIVE_ENTRIES || (!entry.isDirectory() && ++files > MAX_FILES))
                    throw invalid("El ZIP supera 50.000 archivos o 100.000 entradas totales para el análisis npm.");
                String path = entry.getName();
                if (path.length() > MAX_PATH_LENGTH || path.startsWith("/") || path.contains("\\")
                        || Arrays.asList(path.split("/", -1)).contains(".."))
                    throw invalid("Ruta no válida en ZIP.");
                boolean lockfile = !entry.isDirectory()
                    && (path.equals("package-lock.json") || path.endsWith("/package-lock.json"));
                var bytes = lockfile ? new ByteArrayOutputStream() : null;
                for (int count; (count = zip.read(buffer)) != -1;) {
                    expanded += count;
                    if (expanded > MAX_EXPANDED_BYTES) throw invalid("El ZIP supera 512 MiB descomprimidos.");
                    if (lockfile && (bytes.size() + count > MAX_LOCK_BYTES || lockBytes + bytes.size() + count > MAX_TOTAL_LOCK_BYTES))
                        throw invalid("Los lockfiles superan 5 MiB por archivo o 25 MiB en total.");
                    if (lockfile) bytes.write(buffer, 0, count);
                }
                if (lockfile) {
                    if (locks.size() >= 64) throw invalid("El ZIP supera 64 archivos package-lock.json.");
                    if (locks.putIfAbsent(path, bytes.toByteArray()) != null)
                        throw invalid("El ZIP contiene rutas de lockfile duplicadas.");
                    lockBytes += bytes.size();
                }
                zip.closeEntry();
            }
        } catch (IOException failure) { throw new IllegalArgumentException("No se pudo leer el ZIP npm.", failure); }
        return locks;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    private record PackageEntry(String path, JsonNode node, PackageCoordinates coordinates, String scope) { }
}
