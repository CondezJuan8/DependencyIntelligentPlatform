package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Maps cdxgen's CycloneDX components and source-backed direct/transitive classifications to inventory. */
@Component
public final class CycloneDxManifestMapper {
    private static final int MAX_SBOM_BYTES = 25 * 1024 * 1024;
    private static final int MAX_COMPONENTS = 50_000;
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+!~-]{1,255}");
    private final ObjectMapper json;

    public CycloneDxManifestMapper(ObjectMapper json) { this.json = json; }

    public ManifestAnalysis map(byte[] bytes, String ecosystem) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SBOM_BYTES)
            throw new IllegalArgumentException("El SBOM generado supera el límite permitido o está vacío.");
        try {
            JsonNode root = json.readTree(bytes);
            if (!root.path("bomFormat").asText().equals("CycloneDX")
                    || !Set.of("1.6", "1.7").contains(root.path("specVersion").asText()))
                throw new IllegalArgumentException("cdxgen devolvió un SBOM CycloneDX no compatible.");
            JsonNode components = root.path("components");
            if (!components.isArray() || components.size() > MAX_COMPONENTS)
                throw new IllegalArgumentException("El SBOM contiene demasiados componentes o no tiene formato válido.");

            Map<String, Component> byRef = new LinkedHashMap<>();
            int unresolved = 0;
            for (JsonNode item : components) {
                String ref = item.path("bom-ref").asText("");
                if (ref.isBlank() || byRef.containsKey(ref)) { unresolved++; continue; }
                PackageCoordinates coordinates = coordinates(item, ecosystem);
                if (coordinates == null) { unresolved++; continue; }
                byRef.put(ref, new Component(ref, coordinates, scope(item)));
            }

            Map<String, Set<String>> outgoing = new LinkedHashMap<>();
            JsonNode relations = root.path("dependencies");
            if (relations.isArray()) for (JsonNode relation : relations) {
                String parent = relation.path("ref").asText("");
                JsonNode children = relation.path("dependsOn");
                if (!children.isArray()) children = relation.path("depends-on");
                if (children.isArray()) for (JsonNode child : children) {
                    if (child.isTextual()) outgoing.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(child.asText());
                }
            }

            String rootRef = root.path("metadata").path("component").path("bom-ref").asText("");
            Set<String> direct = outgoing.getOrDefault(rootRef, Set.of());
            Set<String> reachable = new LinkedHashSet<>();
            Set<String> unresolvedRefs = new HashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>(direct);
            while (!pending.isEmpty()) {
                String ref = pending.removeFirst();
                if (!byRef.containsKey(ref)) { unresolvedRefs.add(ref); continue; }
                if (!reachable.add(ref)) continue;
                outgoing.getOrDefault(ref, Set.of()).forEach(pending::addLast);
            }

            List<ManifestDependency> dependencies = new ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Component> entry : byRef.entrySet()) {
                String ref = entry.getKey();
                Component component = entry.getValue();
                String path = "sbom/component-" + (++index);
                String dependencyType = direct.contains(ref) ? "DIRECT"
                    : reachable.contains(ref) ? "TRANSITIVE" : "UNKNOWN";
                if ("UNKNOWN".equals(dependencyType)) unresolved++;
                dependencies.add(new ManifestDependency(path, component.coordinates(), component.scope(),
                    dependencyType));
            }
            unresolved += unresolvedRefs.size();
            int manifests = manifestCount(root);
            boolean mavenResolutionIsPartial = "MAVEN".equals(ecosystem);
            boolean missingManifest = manifests == 0;
            boolean emptyDetectedProject = manifests > 0 && dependencies.isEmpty();
            boolean partial = unresolved > 0 || mavenResolutionIsPartial || missingManifest || emptyDetectedProject;
            String message = emptyDetectedProject && components.isEmpty()
                ? "Se detectó un manifiesto, pero el SBOM de cdxgen contiene 0 componentes; no se marcará este inventario como completo."
                : emptyDetectedProject
                ? "cdxgen devolvió " + components.size() + " componentes, pero ninguno tuvo coordenadas y versión verificables; no se marcará este inventario como completo."
                : mavenResolutionIsPartial
                ? "Cobertura Maven parcial: el modo seguro lee POM y SBOM sin ejecutar Maven; no confirma todas las dependencias transitivas."
                : unresolved > 0 ? "Hay componentes del SBOM sin versión o vínculo verificable." : null;
            if (message == null && missingManifest)
                message = "No se encontró package-lock.json; no se pueden confirmar las versiones exactas ni clasificar transitivas de npm.";
            return new ManifestAnalysis(dependencies, manifests, unresolved,
                partial ? "PARTIAL" : "COMPLETE", message);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cdxgen devolvió JSON CycloneDX no válido.", failure);
        }
    }

    private static PackageCoordinates coordinates(JsonNode node, String ecosystem) {
        String purl = node.path("purl").asText("");
        String version = node.path("version").asText("");
        if (!VERSION.matcher(version).matches()) return null;
        if ("MAVEN".equals(ecosystem)) {
            String group = node.path("group").asText("");
            String name = node.path("name").asText("");
            String canonicalPurl = "pkg:maven/" + group + "/" + name + "@" + version;
            if (group.isBlank() || name.isBlank()
                    || (!purl.equals(canonicalPurl) && !purl.startsWith(canonicalPurl + "?")) || purl.contains("#")) return null;
            return new PackageCoordinates("maven", group, name, version, purl);
        }
        if (!purl.startsWith("pkg:npm/")) return null;
        String packagePart = purl.substring("pkg:npm/".length());
        // PURLs can append qualifiers and a subpath after the version. These
        // fields do not change the version in CycloneDX's component.version.
        int at = packagePart.lastIndexOf('@');
        if (at <= 0) return null;
        int qualifier = packagePart.indexOf('?', at + 1);
        int subpath = packagePart.indexOf('#', at + 1);
        int suffix = packagePart.length();
        if (qualifier >= 0) suffix = Math.min(suffix, qualifier);
        if (subpath >= 0) suffix = Math.min(suffix, subpath);
        String purlVersion = decode(packagePart.substring(at + 1, suffix));
        if (purlVersion.isBlank() || !purlVersion.equals(version)) return null;
        String name = decode(packagePart.substring(0, at));
        String componentName = node.path("name").asText("");
        String scopedLeaf = name.startsWith("@") && name.contains("/")
            ? name.substring(name.lastIndexOf('/') + 1) : name;
        // cdxgen 13.8.0 emits a scoped package's leaf in `name` and its full
        // identity only in `purl`; keep that verified identity for OSV/PURL.
        if (name.isBlank() || (!name.equals(componentName) && !scopedLeaf.equals(componentName))) return null;
        String namespace = null;
        if (name.startsWith("@") && name.contains("/")) namespace = name.substring(0, name.indexOf('/'));
        return new PackageCoordinates("npm", namespace, name, version, purl);
    }

    private static String decode(String value) {
        try { return java.net.URLDecoder.decode(value.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8); }
        catch (IllegalArgumentException malformed) { return ""; }
    }

    private static String scope(JsonNode node) {
        String scope = node.path("scope").asText("").toLowerCase();
        if (scope.equals("optional")) return "optional";
        JsonNode properties = node.path("properties");
        if (properties.isArray()) for (JsonNode property : properties) {
            String name = property.path("name").asText("").toLowerCase();
            String value = property.path("value").asText("").toLowerCase();
            if ((name.contains("dev") || name.contains("scope")) && value.equals("true")) return "development";
            if ((name.contains("dev") || name.contains("scope")) && value.equals("dev")) return "development";
            if (name.endsWith(":scope") && value.equals("optional")) return "optional";
        }
        return "runtime";
    }

    private static int manifestCount(JsonNode root) {
        JsonNode evidence = root.path("metadata").path("properties");
        if (evidence.isArray()) for (JsonNode property : evidence) {
            String name = property.path("name").asText("").toLowerCase();
            if (name.contains("manifest") && name.contains("count")) return Math.max(0, property.path("value").asInt(0));
        }
        return 0;
    }

    private record Component(String ref, PackageCoordinates coordinates, String scope) { }
}
