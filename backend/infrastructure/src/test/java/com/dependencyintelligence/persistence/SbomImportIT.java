package com.dependencyintelligence.persistence;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.sbom.application.dto.ImportSbomCommand;
import com.dependencyintelligence.sbom.application.exception.ConflictingPackageException;
import com.dependencyintelligence.sbom.application.exception.InvalidSbomException;
import com.dependencyintelligence.sbom.application.port.in.ImportSbom;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SbomImportIT {
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:17.7-alpine");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RegisterRepository register;
    @Autowired ImportSbom importer;

    @Test void importsProvenanceAndReusesCatalogAcrossDistinctSnapshots() throws Exception {
        var tenant = organization();
        var repository = repository(tenant);
        String content = document(component("first", "core") + "," + component("second", "core"));
        var first = importer.execute(command(tenant, repository, content));
        var second = importer.execute(command(tenant, repository, content));
        assertNotEquals(first.scanId(), second.scanId());
        assertEquals(2, first.componentCount());
        assertEquals(2, count("scans", tenant));
        assertEquals(1, count("components", tenant));
        assertEquals(4, count("scan_components", tenant));
        assertEquals(2, count("sbom_imports", tenant));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))), first.sha256());
        assertEquals(first.sha256(), jdbc.queryForObject("select sha256 from sbom_imports where organization_id = ? and scan_id = ?", String.class, tenant, first.scanId()));
        assertEquals("1.6", jdbc.queryForObject("select specification_version from sbom_imports where scan_id = ?", String.class, first.scanId()));
        assertEquals("urn:uuid:33333333-3333-4333-8333-333333333333", jdbc.queryForObject("select serial_number from sbom_imports where scan_id = ?", String.class, first.scanId()));
        assertEquals("org.example", jdbc.queryForObject("select namespace from components where organization_id = ?", String.class, tenant));
        assertEquals(2, jdbc.queryForObject("select count(*) from scans where organization_id = ? and origin = 'SBOM_IMPORT' and status = 'COMPLETED' and git_reference is null and commit_sha is null and scanner_name is null", Integer.class, tenant));
        assertEquals(4, jdbc.queryForObject("select count(*) from scan_components where organization_id = ? and dependency_type = 'UNKNOWN'", Integer.class, tenant));
        assertEquals(0, count("scan_artifacts", tenant));
    }

    @Test void rejectsInvalidDocumentWithoutCreatingAnyRows() {
        var tenant = organization();
        var repository = repository(tenant);
        var invalid = document(component("same", "core") + "," + component("same", "utils"));
        assertThrows(InvalidSbomException.class, () -> importer.execute(command(tenant, repository, invalid)));
        assertEquals(0, count("scans", tenant));
        assertEquals(0, count("components", tenant));
        assertEquals(0, count("sbom_imports", tenant));
    }

    @Test void tenantScopeAppliesToRepositoryAndCatalog() {
        var tenant = organization();
        var other = organization();
        var repository = repository(tenant);
        var otherRepository = repository(other);
        String content = document(component("core", "core"));
        assertThrows(ResourceNotFoundException.class, () -> importer.execute(command(other, repository, content)));
        assertEquals(0, count("scans", other));
        importer.execute(command(tenant, repository, content));
        importer.execute(command(other, otherRepository, content));
        assertEquals(1, count("components", tenant));
        assertEquals(1, count("components", other));
        var firstId = jdbc.queryForObject("select id from components where organization_id = ?", UUID.class, tenant);
        var secondId = jdbc.queryForObject("select id from components where organization_id = ?", UUID.class, other);
        assertNotEquals(firstId, secondId);
    }

    @Test void persistenceConflictRollsBackSnapshotProvenanceAndEarlierComponents() {
        var tenant = organization();
        var repository = repository(tenant);
        jdbc.update("""
            insert into components(id, organization_id, purl, ecosystem, namespace, name, version)
            values (?, ?, 'pkg:maven/org.example/conflict@1.0.0', 'Maven', 'org.example', 'conflict', 'different')
            """, UUID.randomUUID(), tenant);
        String content = document(component("new", "new-package") + "," + component("conflict", "conflict"));
        assertThrows(ConflictingPackageException.class, () -> importer.execute(command(tenant, repository, content)));
        assertEquals(0, count("scans", tenant));
        assertEquals(0, count("sbom_imports", tenant));
        assertEquals(0, count("scan_components", tenant));
        assertEquals(1, count("components", tenant));
        assertEquals("different", jdbc.queryForObject("select version from components where organization_id = ?", String.class, tenant));
    }

    private UUID organization() {
        var id = UUID.randomUUID();
        jdbc.update("insert into organizations(id, name, slug) values (?, 'Import test', ?)", id, "import-" + id);
        return id;
    }

    private UUID repository(UUID tenant) {
        return register.execute(new RegisterRepositoryCommand(tenant, "demo", UUID.randomUUID().toString(), "example/import", "main")).id();
    }

    private int count(String table, UUID tenant) {
        // Table names are test constants only; organization remains a bound SQL parameter.
        return jdbc.queryForObject("select count(*) from " + table + " where organization_id = ?", Integer.class, tenant);
    }

    private ImportSbomCommand command(UUID tenant, UUID repository, String content) {
        return new ImportSbomCommand(tenant, repository, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String document(String components) {
        return "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"serialNumber\":\"urn:uuid:33333333-3333-4333-8333-333333333333\",\"components\":[" + components + "]}";
    }

    private String component(String reference, String name) {
        return "{\"type\":\"library\",\"bom-ref\":\"" + reference + "\",\"group\":\"org.example\",\"name\":\"" + name
            + "\",\"version\":\"1.0.0\",\"purl\":\"pkg:maven/org.example/" + name + "@1.0.0\"}";
    }
}
