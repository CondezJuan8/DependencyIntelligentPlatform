package com.dependencyintelligence.sbom.infrastructure.parser;

import com.dependencyintelligence.sbom.application.SbomLimits;
import com.dependencyintelligence.sbom.application.exception.InvalidSbomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static com.dependencyintelligence.sbom.application.exception.InvalidSbomException.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class CycloneDxJsonParserTest {
    private final CycloneDxJsonParser parser = new CycloneDxJsonParser();
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode document() throws Exception {
        return (ObjectNode) json.readTree("""
            {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,
             "serialNumber":"urn:uuid:33333333-3333-4333-8333-333333333333",
             "metadata":{"component":{"type":"application","bom-ref":"root","name":"demo"}},
             "components":[{"type":"library","bom-ref":"core","group":"org.example",
                 "name":"demo-core","version":"1.0.0","purl":"pkg:maven/org.example/demo-core@1.0.0"}]}
            """);
    }

    private InputStream input(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }

    private void rejects(String text, InvalidSbomException.Reason reason) {
        assertEquals(reason, assertThrows(InvalidSbomException.class, () -> parser.parse(input(text))).reason());
    }

    @Test void preservesCoordinatesAndSerialWithoutCountingRootOrMergingOccurrences() throws Exception {
        var document = document();
        var components = (ArrayNode) document.get("components");
        var duplicatePackage = components.get(0).deepCopy();
        ((ObjectNode) duplicatePackage).put("bom-ref", "another-occurrence");
        components.add(duplicatePackage);
        var result = parser.parse(input(document.toString()));
        assertEquals(2, result.components().size());
        var coordinates = result.components().getFirst().coordinates();
        assertEquals("maven", coordinates.ecosystem());
        assertEquals("org.example", coordinates.namespace());
        assertEquals("demo-core", coordinates.name());
        assertEquals("1.0.0", coordinates.version());
        assertEquals("pkg:maven/org.example/demo-core@1.0.0", coordinates.purl());
        assertEquals(coordinates, result.components().get(1).coordinates());
        assertEquals("urn:uuid:33333333-3333-4333-8333-333333333333", result.serialNumber());
        assertThrows(UnsupportedOperationException.class, () -> result.components().clear());
    }

    @Test void rejectsMalformedAmbiguousOrMultipleJsonDocuments() throws Exception {
        for (String value : new String[]{"", "{", "[]", document() + " {}",
                document().toString().replace("\"version\":1", "\"version\":1,\"version\":2")}) {
            rejects(value, INVALID_JSON);
        }
    }

    @Test void rejectsUnsupportedFormatAndVersion() throws Exception {
        rejects(document().put("bomFormat", "SPDX").toString(), UNSUPPORTED_FORMAT);
        rejects(document().put("specVersion", "1.5").toString(), UNSUPPORTED_VERSION);
    }

    @Test void refusesMissingCoordinatesAndContradictoryOrUnsupportedPurl() throws Exception {
        for (String field : new String[]{"name", "group", "version", "purl", "bom-ref"}) {
            var document = document();
            ((ObjectNode) document.get("components").get(0)).remove(field);
            rejects(document.toString(), INVALID_COMPONENT);
        }
        for (String purl : new String[]{"pkg:npm/other@1.0.0", "pkg:maven/org.example/demo-core@9.0.0",
                "pkg:maven/org.example/demo-core@1.0.0?type=jar"}) {
            var document = document();
            ((ObjectNode) document.get("components").get(0)).put("purl", purl);
            rejects(document.toString(), INVALID_COMPONENT);
        }
    }

    @Test void rejectsReferenceCollisionsIncludingRoot() throws Exception {
        var document = document();
        var components = (ArrayNode) document.get("components");
        components.add(components.get(0).deepCopy());
        rejects(document.toString(), DUPLICATE_REFERENCE);
        document = document();
        ((ObjectNode) document.get("components").get(0)).put("bom-ref", "root");
        rejects(document.toString(), DUPLICATE_REFERENCE);
    }

    @Test void importsScopedNpmPackagesAndClassifiesDirectAndTransitiveComponents() throws Exception {
        String bom = """
            {"bomFormat":"CycloneDX","specVersion":"1.7","metadata":{"component":{"bom-ref":"root"}},
             "components":[
               {"type":"library","bom-ref":"scoped","name":"@scope/widget","version":"2.3.0","purl":"pkg:npm/%40scope/widget@2.3.0","scope":"required"},
               {"type":"library","bom-ref":"axios","name":"axios","version":"1.7.9","purl":"pkg:npm/axios@1.7.9","scope":"optional"}],
             "dependencies":[{"ref":"root","dependsOn":["scoped"]},{"ref":"scoped","dependsOn":["axios"]}]}
            """;
        var parsed = parser.parse(input(bom));
        var scoped = parsed.components().getFirst();
        assertEquals("npm", scoped.coordinates().ecosystem());
        assertEquals("@scope", scoped.coordinates().namespace());
        assertEquals("@scope/widget", scoped.coordinates().name());
        assertEquals("DIRECT", scoped.dependencyType());
        assertEquals("optional", parsed.components().get(1).scope());
        assertEquals("TRANSITIVE", parsed.components().get(1).dependencyType());
    }

    @Test void enforcesByteAndComponentLimitsAndAllowsEmptyInventory() throws Exception {
        var document = document();
        var components = (ArrayNode) document.get("components");
        var template = components.get(0).deepCopy();
        components.removeAll();
        assertTrue(parser.parse(input(document.toString())).components().isEmpty());
        for (int i = 0; i < SbomLimits.MAX_COMPONENTS; i++) {
            var component = template.deepCopy();
            ((ObjectNode) component).put("bom-ref", "ref-" + i);
            components.add(component);
        }
        assertEquals(SbomLimits.MAX_COMPONENTS, parser.parse(input(document.toString())).components().size());
        components.add(template);
        rejects(document.toString(), LIMIT_EXCEEDED);
        var oversized = new ByteArrayInputStream(new byte[SbomLimits.MAX_BYTES + 1]);
        assertEquals(LIMIT_EXCEEDED, assertThrows(InvalidSbomException.class, () -> parser.parse(oversized)).reason());
    }

    @Test void refusesNestedComponentsAndExcessiveJsonDepth() throws Exception {
        var document = document();
        ((ObjectNode) document.get("components").get(0)).putArray("components");
        rejects(document.toString(), INVALID_COMPONENT);
        rejects("{\"extra\":" + "[".repeat(70) + "0" + "]".repeat(70) + "}", INVALID_JSON);
    }

    @Test void reportsStreamReadFailureWithStableReason() {
        InputStream unreadable = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("unavailable"); }
        };
        assertEquals(READ_FAILED, assertThrows(InvalidSbomException.class, () -> parser.parse(unreadable)).reason());
    }
}
