package com.dependencyintelligence.sbom.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.sbom.application.SbomLimits;
import com.dependencyintelligence.sbom.application.dto.ParsedSbom;
import com.dependencyintelligence.sbom.application.dto.SbomComponent;
import com.dependencyintelligence.sbom.application.dto.SbomDependencyEdge;
import com.dependencyintelligence.sbom.application.exception.InvalidSbomException;
import com.dependencyintelligence.sbom.application.port.out.SbomParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import static com.dependencyintelligence.sbom.application.exception.InvalidSbomException.Reason.*;

/** Bounded CycloneDX 1.6/1.7 Maven and npm package reader. */
@Component
public final class CycloneDxJsonParser implements SbomParser {
    private static final Pattern MAVEN_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern MAVEN_VERSION = Pattern.compile("[A-Za-z0-9_.+-]+");
    private static final Pattern SERIAL = Pattern.compile("urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final ObjectMapper mapper = JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64)
            .maxStringLength(32768).maxNumberLength(32).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Override
    public ParsedSbom parse(InputStream content) {
        Objects.requireNonNull(content, "SBOM input is required");
        byte[] bytes;
        try {
            bytes = content.readNBytes(SbomLimits.MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new InvalidSbomException(READ_FAILED, "Unable to read SBOM", exception);
        }
        if (bytes.length > SbomLimits.MAX_BYTES) { throw invalid(LIMIT_EXCEEDED, "SBOM exceeds 5 MiB"); }
        JsonNode root;
        try {
            root = mapper.readTree(bytes);
        } catch (IOException exception) {
            throw new InvalidSbomException(INVALID_JSON, "SBOM must contain one valid JSON document", exception);
        }
        if (root == null || !root.isObject()) { throw invalid(INVALID_JSON, "SBOM must be a JSON object"); }
        if (!root.path("bomFormat").isTextual() || !"CycloneDX".equals(root.path("bomFormat").textValue())) {
            throw invalid(UNSUPPORTED_FORMAT, "Only CycloneDX JSON is supported");
        }
        String specVersion = root.path("specVersion").asText("");
        if (!Set.of("1.6", "1.7").contains(specVersion)) {
            throw invalid(UNSUPPORTED_VERSION, "Only CycloneDX 1.6 and 1.7 are supported");
        }
        if (root.has("version") && (!root.get("version").isIntegralNumber()
                || !root.get("version").canConvertToInt() || root.get("version").intValue() < 1)) {
            throw invalid(INVALID_JSON, "BOM version must be a positive integer");
        }
        String serial = null;
        if (root.has("serialNumber")) {
            serial = text(root, "serialNumber", 64);
            if (!SERIAL.matcher(serial).matches()) { throw invalid(INVALID_JSON, "Invalid BOM serial number"); }
        }
        Set<String> references = new HashSet<>();
        if (root.has("metadata")) {
            JsonNode metadata = root.get("metadata");
            if (!metadata.isObject()) { throw invalid(INVALID_JSON, "Metadata must be an object"); }
            if (metadata.has("component")) {
                JsonNode project = metadata.get("component");
                if (!project.isObject()) { throw invalid(INVALID_COMPONENT, "Root component must be an object"); }
                if (project.has("components")) { throw invalid(INVALID_COMPONENT, "Nested components are not supported"); }
                if (project.has("bom-ref")) { references.add(text(project, "bom-ref", 512)); }
            }
        }
        JsonNode components = root.get("components");
        if (components == null || !components.isArray()) { throw invalid(INVALID_COMPONENT, "Components must be an array"); }
        if (components.size() > SbomLimits.MAX_COMPONENTS) { throw invalid(LIMIT_EXCEEDED, "SBOM exceeds 500 components"); }
        var parsed = new ArrayList<SbomComponent>();
        Map<String, Integer> componentIndexes = new HashMap<>();
        for (JsonNode node : components) {
            if (!node.isObject() || node.has("components")) {
                throw invalid(INVALID_COMPONENT, "Nested or malformed components are not supported");
            }
            String reference = text(node, "bom-ref", 512);
            if (!references.add(reference)) { throw invalid(DUPLICATE_REFERENCE, "Component references must be unique, including the root"); }
            String name = text(node, "name", 512);
            String version = text(node, "version", 255);
            String purl = text(node, "purl", 2048);
            if (!MAVEN_VERSION.matcher(version).matches()) throw invalid(INVALID_COMPONENT, "Invalid package version");
            PackageCoordinates coordinates;
            if (purl.startsWith("pkg:maven/")) {
                String group = text(node, "group", 255);
                if (!MAVEN_PART.matcher(group).matches() || !MAVEN_PART.matcher(name).matches()
                        || !purl.equals("pkg:maven/" + group + "/" + name + "@" + version)) {
                    throw invalid(INVALID_COMPONENT, "Maven PURL must match its group, name and version");
                }
                coordinates = new PackageCoordinates("maven", group, name, version, purl);
            } else if (purl.startsWith("pkg:npm/")) {
                String packageName = npmName(purl, version);
                if (packageName == null || !packageName.equals(name)) throw invalid(INVALID_COMPONENT, "npm PURL must match the full package name and version");
                String namespace = packageName.startsWith("@") && packageName.contains("/") ? packageName.substring(0, packageName.indexOf('/')) : null;
                coordinates = new PackageCoordinates("npm", namespace, packageName, version, purl);
            } else {
                throw invalid(INVALID_COMPONENT, "Only Maven and npm package URLs are supported");
            }
            String scope = scope(node);
            componentIndexes.put(reference, parsed.size());
            parsed.add(new SbomComponent(reference, coordinates, "UNKNOWN", scope));
        }
        var rootReferences = new LinkedHashSet<String>();
        var edgeRefs = new ArrayList<SbomDependencyEdge>();
        JsonNode dependencyRows = root.path("dependencies");
        if (dependencyRows.isArray()) for (JsonNode relation : dependencyRows) {
            String parent = relation.path("ref").asText("");
            JsonNode children = relation.path("dependsOn");
            if (!children.isArray()) children = relation.path("depends-on");
            if (!children.isArray()) continue;
            for (JsonNode child : children) if (child.isTextual()) {
                String ref = child.asText();
                if (parent.equals(rootReference(root)) && componentIndexes.containsKey(ref)) rootReferences.add(ref);
                else if (references.contains(parent) && componentIndexes.containsKey(ref)) edgeRefs.add(new SbomDependencyEdge(parent, ref));
            }
        }
        var incoming = new HashSet<String>();
        edgeRefs.forEach(edge -> incoming.add(edge.childBomRef()));
        var classified = new ArrayList<SbomComponent>();
        for (SbomComponent component : parsed) {
            String kind = rootReferences.contains(component.bomRef()) ? "DIRECT" : incoming.contains(component.bomRef()) ? "TRANSITIVE" : "UNKNOWN";
            classified.add(new SbomComponent(component.bomRef(), component.coordinates(), kind, component.scope()));
        }
        return new ParsedSbom("CycloneDX", specVersion, serial, classified, edgeRefs);
    }

    private static String npmName(String purl, String version) {
        String value = purl.substring("pkg:npm/".length());
        int delimiter = value.lastIndexOf('@');
        if (delimiter <= 0) return null;
        try {
            String parsedVersion = java.net.URLDecoder.decode(value.substring(delimiter + 1).replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            return parsedVersion.equals(version) ? java.net.URLDecoder.decode(value.substring(0, delimiter).replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8) : null;
        } catch (IllegalArgumentException malformed) { return null; }
    }

    private static String rootReference(JsonNode root) { return root.path("metadata").path("component").path("bom-ref").asText(""); }

    private static String scope(JsonNode node) {
        String value = node.path("scope").asText("").toLowerCase();
        return switch (value) {
            case "optional" -> "optional";
            case "excluded" -> "unknown";
            default -> "runtime";
        };
    }

    private static String text(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maximum || !value.textValue().equals(value.textValue().strip())) {
            throw invalid(INVALID_COMPONENT, "Missing or invalid field: " + field);
        }
        return value.textValue();
    }

    private static InvalidSbomException invalid(InvalidSbomException.Reason reason, String message) {
        return new InvalidSbomException(reason, message);
    }
}
