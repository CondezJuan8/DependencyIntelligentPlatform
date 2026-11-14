package com.dependencyintelligence.persistence;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.exception.RepositoryAlreadyExistsException;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Run with -Pdatabase-it. Uses an isolated container or an explicitly supplied disposable database. */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseIT {
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
    @Autowired DataSource dataSource;
    @Autowired RegisterRepository register;
    @Autowired FindRepository find;
    @Autowired RequestScan requestScan;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;

    @Test void realBusinessControllersRemainDeniedEvenWithValidCsrf() throws Exception {
        var base = "/api/organizations/" + UUID.randomUUID() + "/repositories";
        mvc.perform(get(base + "/" + UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(post(base).with(csrf()).contentType("application/json")
            .content("{\"provider\":\"github\",\"externalId\":\"42\",\"fullName\":\"acme/api\",\"defaultBranch\":\"main\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + UUID.randomUUID() + "/scans").with(csrf())
            .contentType("application/json").content("{\"gitReference\":\"main\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void migrationsAndJpaValidationBootSuccessfully() {
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        flyway.validate();
        assertEquals(13, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'public' and table_name <> 'flyway_schema_history'", Integer.class));
    }

    @Test void registersReadsAndQueuesScanThroughRealAdapters() {
        var organization = organization();
        var repository = register.execute(command(organization, "roundtrip"));
        assertEquals(repository, find.execute(organization, repository.id()));
        var scan = requestScan.execute(new RequestScanCommand(organization, repository.id(), "main"));
        assertEquals("PENDING", scan.status());
        assertEquals(scan.id(), jdbc.queryForObject("select id from scans where organization_id = ? and repository_id = ?", UUID.class, organization, repository.id()));
    }

    @Test void enforcesTenantScopeAndProviderUniqueness() {
        var first = organization();
        var second = organization();
        var repository = register.execute(command(first, "shared-id"));
        assertThrows(RepositoryAlreadyExistsException.class, () -> register.execute(command(first, "shared-id")));
        assertDoesNotThrow(() -> register.execute(command(second, "shared-id")));
        assertThrows(ResourceNotFoundException.class, () -> find.execute(second, repository.id()));
        assertThrows(ResourceNotFoundException.class, () -> requestScan.execute(new RequestScanCommand(second, repository.id(), "main")));
        assertThrows(DataIntegrityViolationException.class, () -> insertScan(second, repository.id()));
    }

    @Test void rejectsCrossSnapshotAndCrossTenantGraphEdgesAndFindings() {
        var tenant = organization();
        var otherTenant = organization();
        var repository = register.execute(command(tenant, "graph"));
        var firstScan = insertScan(tenant, repository.id());
        var secondScan = insertScan(tenant, repository.id());
        var component = UUID.randomUUID();
        jdbc.update("insert into components(id, organization_id, purl, ecosystem, name, version) values (?, ?, 'pkg:maven/acme/core@1', 'Maven', 'core', '1')", component, tenant);
        jdbc.update("insert into scan_components values (?, ?, 'parent', ?, 'DIRECT')", tenant, firstScan, component);
        jdbc.update("insert into scan_components values (?, ?, 'child', ?, 'TRANSITIVE')", tenant, secondScan, component);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into dependency_relationships values (?, ?, 'parent', 'child')", tenant, firstScan));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into scan_components values (?, ?, 'foreign', ?, 'DIRECT')", otherTenant, firstScan, component));
        jdbc.update("insert into scan_components values (?, ?, 'child', ?, 'TRANSITIVE')", tenant, firstScan, component);
        assertDoesNotThrow(() -> jdbc.update("insert into dependency_relationships values (?, ?, 'parent', 'child')", tenant, firstScan));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into dependency_relationships values (?, ?, 'parent', 'parent')", tenant, firstScan));
        var vulnerability = UUID.randomUUID();
        jdbc.update("insert into vulnerabilities(id, source, external_id) values (?, 'OSV', ?)", vulnerability, vulnerability.toString());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into vulnerability_findings(organization_id, scan_id, bom_ref, vulnerability_id) values (?, ?, 'parent', ?)", tenant, secondScan, vulnerability));
        assertDoesNotThrow(() -> jdbc.update("insert into vulnerability_findings(organization_id, scan_id, bom_ref, vulnerability_id) values (?, ?, 'parent', ?)", tenant, firstScan, vulnerability));
    }

    @Test void databaseRejectsInvalidScanLifecycle() {
        var tenant = organization();
        var repository = register.execute(command(tenant, "lifecycle"));
        var scan = insertScan(tenant, repository.id());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update scans set status = 'COMPLETED' where id = ?", scan));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update scans set status = 'RUNNING', started_at = requested_at - interval '1 second' where id = ?", scan));
    }

    @Test void upgradesExistingV1WithoutRewritingItsChecksumOrDeletingData() {
        var schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var v1 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load();
        v1.migrate();
        jdbc.execute("create table " + schema + ".retained_record (value text not null)");
        jdbc.update("insert into " + schema + ".retained_record values ('existing data')");
        var latest = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
        assertEquals(1, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals("2", latest.info().current().getVersion().getVersion());
        assertEquals("existing data", jdbc.queryForObject("select value from " + schema + ".retained_record", String.class));
        assertEquals(0, latest.migrate().migrationsExecuted);
    }

    private UUID organization() {
        var id = UUID.randomUUID();
        jdbc.update("insert into organizations(id, name, slug) values (?, 'Test organization', ?)", id, "test-" + id);
        return id;
    }

    private RegisterRepositoryCommand command(UUID organization, String externalId) {
        return new RegisterRepositoryCommand(organization, "github", externalId, "acme/backend", "main");
    }

    private UUID insertScan(UUID organization, UUID repository) {
        var id = UUID.randomUUID();
        jdbc.update("insert into scans(id, organization_id, repository_id, git_reference, status) values (?, ?, ?, 'main', 'PENDING')", id, organization, repository);
        return id;
    }
}
