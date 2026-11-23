package com.dependencyintelligence.scan.infrastructure.parser;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedNpmLockReaderTest {
    @Test void classifiesExactDirectAndTransitivePackagesFromLockfilePaths() throws Exception {
        String lock = """
            {"lockfileVersion":3,"packages":{
              "":{"dependencies":{"alpha":{"version":"1.0.0"}}},
              "node_modules/alpha":{"version":"1.0.0","dependencies":{"beta":"^2.0.0"}},
              "node_modules/alpha/node_modules/beta":{"version":"2.1.0","dependencies":{"gamma":"^3.0.0"}},
              "node_modules/alpha/node_modules/beta/node_modules/gamma":{"version":"3.2.0"}
            }}
            """;
        var analysis = new BoundedNpmLockReader().read(zip(lock));

        assertEquals(3, analysis.dependencies().size());
        assertEquals(1, analysis.dependencies().stream().filter(item -> item.dependencyType().equals("DIRECT")).count());
        assertEquals(2, analysis.dependencies().stream().filter(item -> item.dependencyType().equals("TRANSITIVE")).count());
        assertEquals(0, analysis.unresolvedCount());
    }

    @Test void preservesScopedPackageNamesAndCanonicalPackageUrls() throws Exception {
        String lock = """
            {"lockfileVersion":3,"packages":{
              "":{"dependencies":{"@scope/widget":"^2.0.0"}},
              "node_modules/@scope/widget":{"name":"@scope/widget","version":"2.3.0","dependencies":{"axios":"^1.7.0"}},
              "node_modules/axios":{"version":"1.7.9"}
            }}
            """;
        var analysis = new BoundedNpmLockReader().read(zip(lock));
        var scoped = analysis.dependencies().stream().filter(item -> item.coordinates().name().equals("@scope/widget"))
            .findFirst().orElseThrow();
        assertEquals("@scope", scoped.coordinates().namespace());
        assertEquals("pkg:npm/%40scope/widget@2.3.0", scoped.coordinates().purl());
        assertEquals("DIRECT", scoped.dependencyType());
        assertEquals(0, analysis.unresolvedCount());
    }

    private static ByteArrayInputStream zip(String lock) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            archive.putNextEntry(new ZipEntry("package-lock.json"));
            archive.write(lock.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
