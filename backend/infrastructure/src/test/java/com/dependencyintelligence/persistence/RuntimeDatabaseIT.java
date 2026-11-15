package com.dependencyintelligence.persistence;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.sbom.application.dto.ImportSbomCommand;
import com.dependencyintelligence.sbom.application.port.in.ImportSbom;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.database.enforce-runtime-role=true")
class RuntimeDatabaseIT extends PostgresIntegrationSupport {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) { database(registry, true); }
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTransactions transactions;
    @Autowired RegisterRepository register;
    @Autowired FindRepository find;
    @Autowired ImportSbom importer;

    private UUID organization() {
        var id = UUID.randomUUID();
        return transactions.execute(id, () -> {
            jdbc.update("insert into organizations(id, name, slug) values (?, 'Runtime fixture', ?)", id, "runtime-" + id);
            return id;
        });
    }

    @Test void runtimeIsNotOwnerOrPrivilegedAndCannotAlterSchema() {
        assertEquals("dependency_app", jdbc.queryForObject("select current_user", String.class));
        assertFalse(jdbc.queryForObject("select rolsuper or rolbypassrls or rolcreatedb or rolcreaterole from pg_roles where rolname = current_user", Boolean.class));
        assertFalse(jdbc.queryForObject("select has_schema_privilege(current_user, 'public', 'CREATE')", Boolean.class));
        assertThrows(DataAccessException.class, () -> jdbc.execute("create table public.runtime_forbidden(id integer)"));
    }

    @Test void contextsResetBetweenTransactionsAndCannotSwitchInsideOne() {
        var first = organization();
        var second = organization();
        assertEquals(0, jdbc.queryForObject("select count(*) from organizations", Integer.class));
        assertEquals(1, transactions.execute(first, () -> jdbc.queryForObject("select count(*) from organizations", Integer.class)));
        assertThrows(IllegalStateException.class, () -> transactions.execute(first,
            () -> transactions.execute(second, () -> 1)));
        assertEquals(1, transactions.execute(second, () -> jdbc.queryForObject("select count(*) from organizations", Integer.class)));
        assertEquals(0, jdbc.queryForObject("select count(*) from organizations", Integer.class));
    }

    @Test void rlsDeniesForeignReadsWritesAndCompositeLinks() {
        var first = organization();
        var second = organization();
        var foreignRepo = register.execute(new RegisterRepositoryCommand(second, "demo", UUID.randomUUID().toString(), "fixture/foreign", "main"));
        assertEquals(0, transactions.execute(first, () -> jdbc.queryForObject("select count(*) from repositories where id = ?", Integer.class, foreignRepo.id())));
        assertEquals(0, transactions.execute(first, () -> jdbc.update("update organizations set name = 'not allowed' where id = ?", second)));
        assertEquals(0, transactions.execute(first, () -> jdbc.update("delete from organizations where id = ?", second)));
        assertThrows(DataAccessException.class, () -> transactions.execute(first, () -> jdbc.update("insert into organization_teams(organization_id, name) values (?, 'foreign')", second)));
        assertThrows(DataAccessException.class, () -> transactions.execute(first, () -> jdbc.update("insert into scans(id, organization_id, repository_id, git_reference, status) values (?, ?, ?, 'main', 'PENDING')", UUID.randomUUID(), first, foreignRepo.id())));
        assertThrows(DataAccessException.class, () -> transactions.execute(first, () -> jdbc.update("update roles set name = 'forbidden' where code = 'OWNER'")));
        assertThrows(DataAccessException.class, () -> transactions.execute(first, () -> jdbc.update("delete from audit_events where organization_id = ?", first)));
    }

    @Test void jpaAndNormalizedImporterWorkWithRealRuntimeRole() {
        var tenant = organization();
        var repository = register.execute(new RegisterRepositoryCommand(tenant, "demo", UUID.randomUUID().toString(), "fixture/runtime", "main"));
        assertEquals(repository.id(), find.execute(tenant, repository.id()).id());
        assertThrows(ResourceNotFoundException.class, () -> find.execute(UUID.randomUUID(), repository.id()));
        String document = """
            {"bomFormat":"CycloneDX","specVersion":"1.6","components":[
              {"type":"library","bom-ref":"runtime","group":"org.example","name":"core","version":"1.0.0","purl":"pkg:maven/org.example/core@1.0.0"}]}
            """;
        var result = importer.execute(new ImportSbomCommand(tenant, repository.id(), new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8))));
        assertEquals(1, result.componentCount());
        assertEquals(1, transactions.execute(tenant, () -> jdbc.queryForObject("select count(*) from component_versions", Integer.class)));
        assertEquals(1, transactions.execute(tenant, () -> jdbc.queryForObject("select count(*) from sbom_documents where scan_id = ?", Integer.class, result.scanId())));
    }
}
