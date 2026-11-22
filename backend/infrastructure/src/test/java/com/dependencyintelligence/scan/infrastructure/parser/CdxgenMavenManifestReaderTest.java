package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdxgenMavenManifestReaderTest {
    @Test void resolvesInheritedPropertiesAndAnImportedBomFromTheUploadedProject() throws Exception {
        CycloneDxGenerator emptyBom = (archive, ecosystem) -> """
            {"bomFormat":"CycloneDX","specVersion":"1.6",
             "metadata":{"component":{"type":"application","bom-ref":"root"},
               "properties":[{"name":"dependency-intelligence:source-manifest-count","value":"1"}]},
             "components":[],"dependencies":[]}
            """.getBytes(StandardCharsets.UTF_8);
        var reader = new CdxgenMavenManifestReader(emptyBom,
            new CycloneDxManifestMapper(new ObjectMapper()), new BoundedMavenArchiveReader());
        String parent = """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>parent</artifactId>
              <version>1.0.0</version><packaging>pom</packaging><properties><lib.version>3.4.5</lib.version></properties>
            </project>
            """;
        String bom = """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>local-bom</artifactId>
              <version>1.0.0</version><packaging>pom</packaging><dependencyManagement><dependencies>
                <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>${lib.version}</version></dependency>
              </dependencies></dependencyManagement></project>
            """;
        String module = """
            <project><modelVersion>4.0.0</modelVersion><parent><groupId>org.example</groupId><artifactId>parent</artifactId>
              <version>1.0.0</version><relativePath>../pom.xml</relativePath></parent><artifactId>service</artifactId>
              <dependencyManagement><dependencies><dependency><groupId>org.example</groupId><artifactId>local-bom</artifactId>
                <version>1.0.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
              <dependencies><dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency></dependencies>
            </project>
            """;

        var result = reader.read(zip(Map.of("pom.xml", parent, "service/pom.xml", module, "bom/pom.xml", bom)));

        assertEquals(3, result.manifestCount());
        assertEquals(1, result.dependencies().size());
        assertEquals("3.4.5", result.dependencies().getFirst().coordinates().version());
        assertEquals("service/pom.xml", result.dependencies().getFirst().manifestPath());
        assertEquals("PARTIAL", result.coverageStatus());
    }

    @Test void recoversExactDirectDeclarationsWhenCdxgenReturnsAnEmptyBom() throws Exception {
        CycloneDxGenerator emptyBom = (archive, ecosystem) -> """
            {"bomFormat":"CycloneDX","specVersion":"1.6",
             "metadata":{"component":{"type":"application","bom-ref":"root"},
               "properties":[{"name":"dependency-intelligence:source-manifest-count","value":"1"}]},
             "components":[],"dependencies":[]}
            """.getBytes(StandardCharsets.UTF_8);
        String pom = """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>demo</artifactId>
              <version>1.0.0</version><properties><jackson.version>2.17.2</jackson.version></properties>
              <dependencies>
                <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>${jackson.version}</version></dependency>
              </dependencies>
            </project>
            """;

        var reader = new CdxgenMavenManifestReader(emptyBom,
            new CycloneDxManifestMapper(new ObjectMapper()), new BoundedMavenArchiveReader());

        var result = reader.read(zip(pom));

        assertEquals(1, result.manifestCount());
        assertEquals(1, result.dependencies().size());
        assertEquals("com.fasterxml.jackson.core", result.dependencies().getFirst().coordinates().namespace());
        assertEquals("jackson-databind", result.dependencies().getFirst().coordinates().name());
        assertEquals("2.17.2", result.dependencies().getFirst().coordinates().version());
        assertEquals("DIRECT", result.dependencies().getFirst().dependencyType());
        assertEquals("PARTIAL", result.coverageStatus());
        assertTrue(result.coverageMessage().contains("dependencias directas"), result.coverageMessage());
    }

    @Test void keepsMavenManifestVisibleAsPartialWhenNoExactDependencyCanBeRecovered() throws Exception {
        CycloneDxGenerator emptyBom = (archive, ecosystem) -> """
            {"bomFormat":"CycloneDX","specVersion":"1.6",
             "metadata":{"component":{"type":"application","bom-ref":"root"},
               "properties":[{"name":"dependency-intelligence:source-manifest-count","value":"1"}]},
             "components":[],"dependencies":[]}
            """.getBytes(StandardCharsets.UTF_8);
        String pom = """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>demo</artifactId>
              <dependencies><dependency><groupId>org.example</groupId><artifactId>managed</artifactId></dependency></dependencies>
            </project>
            """;

        var reader = new CdxgenMavenManifestReader(emptyBom,
            new CycloneDxManifestMapper(new ObjectMapper()), new BoundedMavenArchiveReader());

        var result = reader.read(zip(pom));

        assertEquals(1, result.manifestCount());
        assertTrue(result.dependencies().isEmpty());
        assertEquals(1, result.unresolvedCount());
        assertEquals("PARTIAL", result.coverageStatus());
        assertTrue(result.coverageMessage().contains("versión exacta"));
    }

    private static ByteArrayInputStream zip(String pom) throws Exception {
        return zip(Map.of("pom.xml", pom));
    }

    private static ByteArrayInputStream zip(java.util.Map<String, String> poms) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            for (var entry : poms.entrySet()) {
                archive.putNextEntry(new ZipEntry(entry.getKey()));
                archive.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
