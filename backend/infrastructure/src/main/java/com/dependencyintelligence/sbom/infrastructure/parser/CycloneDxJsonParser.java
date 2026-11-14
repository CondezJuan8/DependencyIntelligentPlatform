package com.dependencyintelligence.sbom.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.sbom.application.SbomLimits;
import com.dependencyintelligence.sbom.application.dto.ParsedSbom;
import com.dependencyintelligence.sbom.application.dto.SbomComponent;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import static com.dependencyintelligence.sbom.application.exception.InvalidSbomException.Reason.*;

/** Deliberately bounded CycloneDX 1.6/Maven subset for the local demo, not a full schema validator. */
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
        if (!"1.6".equals(root.path("specVersion").textValue())) {
            throw invalid(UNSUPPORTED_VERSION, "Only CycloneDX 1.6 is supported by this demo");
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
        for (JsonNode node : components) {
            if (!node.isObject() || !"library".equals(node.path("type").textValue()) || node.has("components")) {
                throw invalid(INVALID_COMPONENT, "Only flat library components are supported");
            }
            String reference = text(node, "bom-ref", 512);
            if (!references.add(reference)) { throw invalid(DUPLICATE_REFERENCE, "Component references must be unique, including the root"); }
            String group = text(node, "group", 255);
            String name = text(node, "name", 512);
            String version = text(node, "version", 255);
            String purl = text(node, "purl", 2048);
            if (!MAVEN_PART.matcher(group).matches() || !MAVEN_PART.matcher(name).matches()
                    || !MAVEN_VERSION.matcher(version).matches()
                    || !purl.equals("pkg:maven/" + group + "/" + name + "@" + version)) {
                throw invalid(INVALID_COMPONENT, "Expected a simple Maven PURL matching group, name and version");
            }
            parsed.add(new SbomComponent(reference, new PackageCoordinates("Maven", group, name, version, purl)));
        }
        return new ParsedSbom("CycloneDX", "1.6", serial, parsed);
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
