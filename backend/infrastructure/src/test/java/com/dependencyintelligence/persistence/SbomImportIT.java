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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SbomImportIT extends PostgresIntegrationSupport {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) { database(registry, false); }

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
        assertEquals(2, count("sbom_documents", tenant));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))), first.sha256());
        assertEquals(first.sha256(), jdbc.queryForObject("select sha256 from sbom_documents where organization_id = ? and scan_id = ?", String.class, tenant, first.scanId()));
        assertEquals("1.6", jdbc.queryForObject("select specification_version from sbom_documents where scan_id = ?", String.class, first.scanId()));
        assertEquals("urn:uuid:33333333-3333-4333-8333-333333333333", jdbc.queryForObject("select serial_number from sbom_documents where scan_id = ?", String.class, first.scanId()));
        assertEquals("org.example", jdbc.queryForObject("select namespace from components where organization_id = ?", String.class, tenant));
        assertEquals(2, jdbc.queryForObject("select count(*) from scans where organization_id = ? and origin = 'SBOM_IMPORT' and status = 'COMPLETED' and git_reference is null and repository_commit_id is null and scanner_name is null", Integer.class, tenant));
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
        assertEquals(0, count("sbom_documents", tenant));
    }

    @Test void persistsScopedNpmComponentsAndTheirDirectAndTransitiveClassification() {
        var tenant = organization();
        var repository = repository(tenant);
        String content = """
            {"bomFormat":"CycloneDX","specVersion":"1.7","metadata":{"component":{"type":"application","bom-ref":"root"}},
             "components":[
               {"type":"library","bom-ref":"scoped","name":"@scope/widget","version":"2.3.0","purl":"pkg:npm/%40scope/widget@2.3.0","scope":"required"},
               {"type":"library","bom-ref":"axios","name":"axios","version":"1.7.9","purl":"pkg:npm/axios@1.7.9","scope":"optional"}],
             "dependencies":[{"ref":"root","dependsOn":["scoped"]},{"ref":"scoped","dependsOn":["axios"]}]}
            """;

        var imported = importer.execute(command(tenant, repository, content));
        assertEquals(2, imported.componentCount());
        assertEquals(1, jdbc.queryForObject("select count(*) from scan_components where organization_id=? and scan_id=? and dependency_type='DIRECT'", Integer.class, tenant, imported.scanId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from scan_components where organization_id=? and scan_id=? and dependency_type='TRANSITIVE'", Integer.class, tenant, imported.scanId()));
        assertEquals("@scope/widget", jdbc.queryForObject("select c.name from scan_components sc join component_versions cv on cv.organization_id=sc.organization_id and cv.id=sc.component_version_id join components c on c.organization_id=cv.organization_id and c.id=cv.component_id where sc.organization_id=? and sc.scan_id=? and sc.bom_ref='scoped'", String.class, tenant, imported.scanId()));
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
        var packageId = jdbc.queryForObject("insert into components(organization_id, package_ecosystem_id, namespace, name) select ?, id, 'org.example', 'conflict' from package_ecosystems where code = 'maven' returning id", UUID.class, tenant);
        var versionId = jdbc.queryForObject("insert into component_versions(organization_id, component_id, version) values (?, ?, 'different') returning id", UUID.class, tenant, packageId);
        jdbc.update("insert into component_identifiers(organization_id, component_version_id, identifier_type, value) values (?, ?, 'PURL', 'pkg:maven/org.example/conflict@1.0.0')", tenant, versionId);
        String content = document(component("new", "new-package") + "," + component("conflict", "conflict"));
        assertThrows(ConflictingPackageException.class, () -> importer.execute(command(tenant, repository, content)));
        assertEquals(0, count("scans", tenant));
        assertEquals(0, count("sbom_documents", tenant));
        assertEquals(0, count("scan_components", tenant));
        assertEquals(1, count("components", tenant));
        assertEquals("different", jdbc.queryForObject("select version from component_versions where organization_id = ?", String.class, tenant));
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
