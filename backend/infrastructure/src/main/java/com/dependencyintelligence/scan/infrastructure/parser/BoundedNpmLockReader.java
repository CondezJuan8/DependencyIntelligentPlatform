package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.dto.ManifestDependencyEdge;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Parses npm lockfile v2/v3 as data; never runs npm or project scripts. */
@Component
public final class BoundedNpmLockReader implements NpmLockfileReader {
    private static final int MAX_LOCK_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 5_000;
    private static final int MAX_PACKAGES = 30_000;
    private static final long MAX_EXPANDED_BYTES = 120L * 1024 * 1024;
    private static final List<String> ROOT_DEPENDENCY_SECTIONS = List.of("dependencies", "devDependencies", "optionalDependencies", "peerDependencies");
    private static final List<String> PACKAGE_DEPENDENCY_SECTIONS = List.of("dependencies", "optionalDependencies", "peerDependencies");

    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override public ManifestAnalysis read(InputStream input) {
        byte[] lock = readLock(input);
        if (lock == null) return new ManifestAnalysis(List.of(), 0, 0);
        try {
            JsonNode root = json.readTree(lock);
            int version = root.path("lockfileVersion").asInt(0);
            if (version < 2 || version > 3) throw invalid("Solo se admite package-lock v2 o v3.");
            JsonNode packages = root.path("packages");
            if (!packages.isObject() || packages.size() > MAX_PACKAGES) throw invalid("Mapa packages inválido o demasiado grande.");

            Map<String, PackageEntry> entries = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = packages.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String path = field.getKey();
                JsonNode node = field.getValue();
                if (path.isEmpty() || node.path("link").asBoolean(false) || !node.path("version").isTextual()) continue;
                String name = packageName(path, node);
                String versionText = node.path("version").asText();
                if (name.isBlank() || name.length() > 512 || versionText.isBlank() || versionText.length() > 255) continue;
                String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
                var coordinates = new PackageCoordinates("npm", null, name, versionText,
                    "pkg:npm/" + encodedName + "@" + versionText);
                String scope = node.path("optional").asBoolean(false) ? "optional"
                    : node.path("dev").asBoolean(false) ? "development" : "runtime";
                entries.put(path, new PackageEntry(path, node, coordinates, scope));
            }

            JsonNode rootPackage = packages.path("");
            Set<String> directPaths = new LinkedHashSet<>();
            Set<ManifestDependencyEdge> edges = new LinkedHashSet<>();
            int unresolved = connectDependencies("", rootPackage, entries, directPaths, edges);

            Deque<String> pending = new ArrayDeque<>(directPaths);
            Set<String> reachable = new HashSet<>();
            while (!pending.isEmpty()) {
                String parentPath = pending.removeFirst();
                if (!reachable.add(parentPath)) continue;
                PackageEntry parent = entries.get(parentPath);
                if (parent == null) continue;
                int before = edges.size();
                unresolved += connectDependencies(parentPath, parent.node(), entries, null, edges);
                if (edges.size() > before) {
                    for (ManifestDependencyEdge edge : edges) {
                        if (edge.parentManifestPath().equals(parentPath) && !reachable.contains(edge.childManifestPath()))
                            pending.addLast(edge.childManifestPath());
                    }
                }
            }

            List<ManifestDependency> dependencies = new ArrayList<>();
            for (String path : reachable) {
                PackageEntry entry = entries.get(path);
                if (entry == null) continue;
                dependencies.add(new ManifestDependency(path, entry.coordinates(), entry.scope(),
                    directPaths.contains(path) ? "DIRECT" : "TRANSITIVE"));
            }
            dependencies.sort((left, right) -> left.manifestPath().compareTo(right.manifestPath()));
            List<ManifestDependencyEdge> resolvedEdges = edges.stream()
                .filter(edge -> reachable.contains(edge.parentManifestPath()) && reachable.contains(edge.childManifestPath()))
                .toList();
            int unreachable = Math.max(0, entries.size() - reachable.size());
            return new ManifestAnalysis(dependencies, resolvedEdges, 1, unresolved + unreachable);
        } catch (IOException failure) {
            throw new IllegalArgumentException("package-lock.json no es JSON válido.", failure);
        }
    }

    private static int connectDependencies(String parentPath, JsonNode parent, Map<String, PackageEntry> entries,
            Set<String> directPaths, Set<ManifestDependencyEdge> edges) {
        Set<String> names = dependencyNames(parent, parentPath.isEmpty() ? ROOT_DEPENDENCY_SECTIONS : PACKAGE_DEPENDENCY_SECTIONS);
        int unresolved = 0;
        for (String name : names) {
            String targetPath = resolveInstalledPath(parentPath, name, entries);
            if (targetPath == null) { unresolved++; continue; }
            if (parentPath.isEmpty()) {
                if (directPaths != null) directPaths.add(targetPath);
            } else if (edges != null) {
                edges.add(new ManifestDependencyEdge(parentPath, targetPath));
            }
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

    private static byte[] readLock(InputStream input) {
        byte[] lock = null;
        long expanded = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ARCHIVE_ENTRIES) throw invalid("ZIP con demasiados archivos.");
                String path = entry.getName();
                if (path.startsWith("/") || path.contains("\\") || Arrays.asList(path.split("/", -1)).contains(".."))
                    throw invalid("Ruta no válida en ZIP.");
                boolean lockfile = !entry.isDirectory() && path.endsWith("package-lock.json");
                var bytes = lockfile ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                for (int count; (count = zip.read(buffer)) != -1;) {
                    expanded += count;
                    if (expanded > MAX_EXPANDED_BYTES || (lockfile && bytes.size() + count > MAX_LOCK_BYTES))
                        throw invalid("ZIP o package-lock supera el límite.");
                    if (lockfile) bytes.write(buffer, 0, count);
                }
                if (lockfile) {
                    if (lock != null) throw invalid("Más de un package-lock.json; analiza cada aplicación por separado.");
                    lock = bytes.toByteArray();
                }
                zip.closeEntry();
            }
        } catch (IOException failure) { throw new IllegalArgumentException("No se pudo leer el ZIP npm.", failure); }
        return lock;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    private record PackageEntry(String path, JsonNode node, PackageCoordinates coordinates, String scope) { }
}
