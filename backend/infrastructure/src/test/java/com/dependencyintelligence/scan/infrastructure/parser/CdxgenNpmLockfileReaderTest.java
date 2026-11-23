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
        assertEquals("PARTIAL", result.coverageStatus());
        assertTrue(result.coverageMessage().contains("recuperó directamente de package-lock.json"));
        assertTrue(result.dependencies().stream().anyMatch(item -> item.coordinates().name().equals("@scope/widget")));
    }

    @Test void retainsTransitivelyResolvedScopedPackagesMissingFromANonEmptyBom() throws Exception {
        CycloneDxGenerator partialBom = (archive, ecosystem) -> """
            {"bomFormat":"CycloneDX","specVersion":"1.6",
             "metadata":{"component":{"type":"application","bom-ref":"root"},
               "properties":[{"name":"dependency-intelligence:source-manifest-count","value":"2"}]},
             "components":[{"type":"library","bom-ref":"top","name":"top","version":"2.3.0","purl":"pkg:npm/top@2.3.0"}],
             "dependencies":[{"ref":"root","dependsOn":["top"]},{"ref":"top","dependsOn":[]}]}
            """.getBytes(StandardCharsets.UTF_8);
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            for (int file = 0; file < 5_001; file++) {
                archive.putNextEntry(new ZipEntry("src/file-" + file + ".txt"));
                archive.closeEntry();
            }
            write(archive, "first/package-lock.json", """
                {"lockfileVersion":3,"packages":{
                  "":{"dependencies":{"top":"^2.3.0"}},
                  "node_modules/top":{"version":"2.3.0","dependencies":{"@scope/leaf":"^1.2.0"}},
                  "node_modules/@scope/leaf":{"version":"1.2.0"}
                }}
                """);
            write(archive, "second/package-lock.json", """
                {"lockfileVersion":3,"packages":{"":{"dependencies":{"other":"1.0.0"}},"node_modules/other":{"version":"1.0.0"}}}
                """);
        }

        var result = new CdxgenNpmLockfileReader(partialBom, new CycloneDxManifestMapper(new com.fasterxml.jackson.databind.ObjectMapper()),
            new BoundedNpmLockReader()).read(new ByteArrayInputStream(bytes.toByteArray()));

        assertEquals(2, result.manifestCount());
        assertEquals(3, result.dependencies().size());
        assertTrue(result.dependencies().stream().anyMatch(item -> "@scope/leaf".equals(item.coordinates().name())
            && "TRANSITIVE".equals(item.dependencyType()) && item.manifestPath().startsWith("first/package-lock.json/")));
    }

    private static void write(ZipOutputStream archive, String path, String contents) throws Exception {
        archive.putNextEntry(new ZipEntry(path));
        archive.write(contents.getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
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
