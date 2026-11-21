package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdxgenNpmLockfileReaderTest {
    @Test void recoversExactInventoryWhenCdxgenReturnsAnEmptyBom() throws Exception {
        CycloneDxGenerator emptyBom = (archive, ecosystem) -> """
            {"bomFormat":"CycloneDX","specVersion":"1.6",
             "metadata":{"component":{"type":"application","bom-ref":"root"},
               "properties":[{"name":"dependency-intelligence:source-manifest-count","value":"1"}]},
             "components":[],"dependencies":[]}
            """.getBytes(StandardCharsets.UTF_8);
        String lockfile = """
            {"lockfileVersion":3,"packages":{
              "":{"dependencies":{"@scope/widget":"^2.0.0"}},
              "node_modules/@scope/widget":{"name":"@scope/widget","version":"2.3.0","dependencies":{"left-pad":"^1.3.0"}},
              "node_modules/left-pad":{"version":"1.3.0"}
            }}
            """;

        var result = new CdxgenNpmLockfileReader(emptyBom, new CycloneDxManifestMapper(new com.fasterxml.jackson.databind.ObjectMapper()),
            new BoundedNpmLockReader()).read(zip(lockfile));

        assertEquals(2, result.dependencies().size());
        assertEquals(1, result.dependencies().stream().filter(item -> "DIRECT".equals(item.dependencyType())).count());
        assertEquals(1, result.dependencies().stream().filter(item -> "TRANSITIVE".equals(item.dependencyType())).count());
        assertEquals(1, result.edges().size());
        assertEquals("PARTIAL", result.coverageStatus());
        assertTrue(result.coverageMessage().contains("recuperó directamente de package-lock.json"));
        assertTrue(result.dependencies().stream().anyMatch(item -> item.coordinates().name().equals("@scope/widget")));
    }

    private static ByteArrayInputStream zip(String lockfile) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            archive.putNextEntry(new ZipEntry("package-lock.json"));
            archive.write(lockfile.getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
