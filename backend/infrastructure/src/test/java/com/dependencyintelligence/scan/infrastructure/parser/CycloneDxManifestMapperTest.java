package com.dependencyintelligence.scan.infrastructure.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycloneDxManifestMapperTest {
    private final CycloneDxManifestMapper mapper = new CycloneDxManifestMapper(new ObjectMapper());

    @Test void mapsMavenMultiModuleManagedVersionsAndVerifiedTransitiveEdges() {
        String bom = """
            {"bomFormat":"CycloneDX","specVersion":"1.7","metadata":{"component":{"bom-ref":"root"},"properties":[{"name":"dependency-intelligence:source-manifest-count","value":"3"}]},
             "components":[
               {"type":"library","bom-ref":"org.example:api@1.2.0","group":"org.example","name":"api","version":"1.2.0","purl":"pkg:maven/org.example/api@1.2.0"},
               {"type":"library","bom-ref":"org.example:core@5.4.1","group":"org.example","name":"core","version":"5.4.1","purl":"pkg:maven/org.example/core@5.4.1?type=jar"}],
             "dependencies":[{"ref":"root","dependsOn":["org.example:api@1.2.0"]},
               {"ref":"org.example:api@1.2.0","dependsOn":["org.example:core@5.4.1"]}]}
            """;
        var analysis = mapper.map(bom.getBytes(java.nio.charset.StandardCharsets.UTF_8), "MAVEN");

        assertEquals(2, analysis.dependencies().size());
        assertEquals("5.4.1", analysis.dependencies().stream().filter(item -> item.coordinates().name().equals("core"))
            .findFirst().orElseThrow().coordinates().version());
        assertEquals(1, analysis.dependencies().stream().filter(item -> item.dependencyType().equals("DIRECT")).count());
        assertEquals(1, analysis.dependencies().stream().filter(item -> item.dependencyType().equals("TRANSITIVE")).count());
        assertEquals(1, analysis.edges().size());
        assertEquals(0, analysis.unresolvedCount());
        assertEquals("PARTIAL", analysis.coverageStatus());
    }

    @Test void preservesScopedNpmIdentityAndLockfileTransitiveRelationships() {
        String bom = """
            {"bomFormat":"CycloneDX","specVersion":"1.6","metadata":{"component":{"bom-ref":"pkg:npm/scanner-fixture@1.0.0"},"properties":[{"name":"dependency-intelligence:source-manifest-count","value":"1"}]},
             "components":[
               {"type":"library","bom-ref":"pkg:npm/@scope/widget@2.3.0","name":"widget","version":"2.3.0","purl":"pkg:npm/%40scope/widget@2.3.0","scope":"required"},
               {"type":"library","bom-ref":"pkg:npm/axios@1.7.9","name":"axios","version":"1.7.9","purl":"pkg:npm/axios@1.7.9","scope":"required"}],
             "dependencies":[{"ref":"pkg:npm/scanner-fixture@1.0.0","dependsOn":["pkg:npm/@scope/widget@2.3.0"]},
               {"ref":"pkg:npm/@scope/widget@2.3.0","dependsOn":["pkg:npm/axios@1.7.9"]}]}
            """;
        var analysis = mapper.map(bom.getBytes(java.nio.charset.StandardCharsets.UTF_8), "NPM");

        var scoped = analysis.dependencies().stream().filter(item -> item.coordinates().name().equals("@scope/widget"))
            .findFirst().orElseThrow();
        assertEquals("@scope", scoped.coordinates().namespace());
        assertEquals("pkg:npm/%40scope/widget@2.3.0", scoped.coordinates().purl());
        assertEquals("DIRECT", scoped.dependencyType());
        assertTrue(analysis.dependencies().stream().anyMatch(item -> item.coordinates().name().equals("axios")
            && item.dependencyType().equals("TRANSITIVE")));
        assertEquals(1, analysis.edges().size());
        assertEquals(0, analysis.unresolvedCount());
    }

    @Test void marksComponentsWithoutRootRelationshipAsPartialInsteadOfInventingEdges() {
        String bom = """
            {"bomFormat":"CycloneDX","specVersion":"1.6","metadata":{"component":{"bom-ref":"root"},"properties":[{"name":"dependency-intelligence:source-manifest-count","value":"2"}]},
             "components":[{"type":"library","bom-ref":"lib","group":"org.example","name":"lib","version":"1.0.0","purl":"pkg:maven/org.example/lib@1.0.0?type=jar"}],
             "dependencies":[]}
            """;
        var analysis = mapper.map(bom.getBytes(java.nio.charset.StandardCharsets.UTF_8), "MAVEN");
        assertEquals(1, analysis.dependencies().size());
        assertEquals("UNKNOWN", analysis.dependencies().getFirst().dependencyType());
        assertTrue(analysis.edges().isEmpty());
        assertEquals(1, analysis.unresolvedCount());
        assertEquals("PARTIAL", analysis.coverageStatus());
    }
}
