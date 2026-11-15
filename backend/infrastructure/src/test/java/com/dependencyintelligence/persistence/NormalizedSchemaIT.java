package com.dependencyintelligence.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioural fixtures for normalization and cross-module/tenant integrity, not a CRUD test per table. */
class NormalizedSchemaIT extends PostgresIntegrationSupport {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) { database(registry, false); }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private record Fixture(UUID tenant, UUID user, UUID member, UUID repo, UUID scan, UUID component, UUID version, UUID occurrence) { }

    private Fixture fixture() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), repo = UUID.randomUUID(), scan = UUID.randomUUID();
        jdbc.update("insert into organizations(id,name,slug) values (?,'Schema fixture',?)", tenant, "schema-" + tenant);
        jdbc.update("insert into users(id,email,display_name) values (?,?, 'Fixture')", user, user + "@example.invalid");
        var member = jdbc.queryForObject("insert into organization_memberships(organization_id,user_id) values (?,?) returning id", UUID.class, tenant, user);
        jdbc.update("insert into repositories(id,organization_id,provider,external_id,full_name,default_branch) values (?,?,'demo',?,'fixture/schema','main')", repo, tenant, repo.toString());
        jdbc.update("insert into scans(id,organization_id,repository_id,git_reference,status) values (?,?,?,'main','PENDING')", scan, tenant, repo);
        var component = jdbc.queryForObject("insert into components(organization_id,package_ecosystem_id,namespace,name) select ?,id,'org.example','core' from package_ecosystems where code='maven' returning id", UUID.class, tenant);
        var version = jdbc.queryForObject("insert into component_versions(organization_id,component_id,version) values (?,?,'1.0.0') returning id", UUID.class, tenant, component);
        var occurrence = jdbc.queryForObject("insert into scan_components(organization_id,scan_id,bom_ref,component_version_id,dependency_type) values (?,?,'core',?,'UNKNOWN') returning id", UUID.class, tenant, scan, version);
        return new Fixture(tenant, user, member, repo, scan, component, version, occurrence);
    }

    @Test void membershipRolesTeamsAndRepositoryPermissionsHaveRealForeignKeys() {
        var first = fixture(); var second = fixture();
        jdbc.update("insert into organization_memberships(organization_id,user_id) values (?,?)", second.tenant(), first.user());
        var owner = jdbc.queryForObject("select id from roles where code='OWNER'", UUID.class);
        jdbc.update("insert into membership_roles values (?,?,?)", first.tenant(), first.member(), owner);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into membership_roles values (?,?,?)", first.tenant(), first.member(), owner));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into organization_memberships(organization_id,user_id) values (?,?)", first.tenant(), first.user()));
        var team = jdbc.queryForObject("insert into organization_teams(organization_id,name) values (?,'Engineering') returning id", UUID.class, first.tenant());
        jdbc.update("insert into team_memberships values (?,?,?)", first.tenant(), team, first.member());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into team_memberships values (?,?,?)", first.tenant(), team, second.member()));
        var permission = jdbc.queryForObject("select id from permissions where code='repository.read'", UUID.class);
        jdbc.update("insert into repository_team_access values (?,?,?,?)", first.tenant(), first.repo(), team, permission);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into repository_team_access values (?,?,?,?)", first.tenant(), second.repo(), team, permission));
        jdbc.update("insert into user_identities(user_id,provider,provider_subject) values (?,'google',?)", first.user(), "fixture-" + first.user());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into user_identities(user_id,provider,provider_subject) values (?,'google',?)", second.user(), "fixture-" + first.user()));
    }

    @Test void edgesFindingsAndComparisonsCannotCrossSnapshotRepositoryOrTenant() {
        var first = fixture(); var second = fixture();
        var type = jdbc.queryForObject("select id from dependency_relationship_types where code='depends_on'", UUID.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into dependency_edges(organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) values (?,?,?,?,?)", first.tenant(), first.scan(), first.occurrence(), second.occurrence(), type));
        var otherScan = UUID.randomUUID();
        jdbc.update("insert into scans(id,organization_id,repository_id,git_reference,status) values (?,?,?,'main','PENDING')", otherScan, first.tenant(), first.repo());
        var otherOccurrence = jdbc.queryForObject("insert into scan_components(organization_id,scan_id,bom_ref,component_version_id,dependency_type) values (?,?,'child',?,'TRANSITIVE') returning id", UUID.class, first.tenant(), otherScan, first.version());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into dependency_edges(organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) values (?,?,?,?,?)", first.tenant(), first.scan(), first.occurrence(), otherOccurrence, type));
        var child = jdbc.queryForObject("insert into scan_components(organization_id,scan_id,bom_ref,component_version_id,dependency_type) values (?,?,'valid-child',?,'TRANSITIVE') returning id", UUID.class, first.tenant(), first.scan(), first.version());
        jdbc.update("insert into dependency_edges(organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) values (?,?,?,?,?)", first.tenant(), first.scan(), first.occurrence(), child, type);
        var vulnerability = UUID.randomUUID();
        jdbc.update("insert into vulnerabilities(id,source,external_id) values (?,'OSV',?)", vulnerability, "fixture-" + vulnerability);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into scan_vulnerability_findings(organization_id,scan_id,scan_component_id,vulnerability_id) values (?,?,?,?)", first.tenant(), first.scan(), second.occurrence(), vulnerability));
        jdbc.update("insert into scan_vulnerability_findings(organization_id,scan_id,scan_component_id,vulnerability_id) values (?,?,?,?)", first.tenant(), first.scan(), first.occurrence(), vulnerability);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into scan_comparisons(organization_id,repository_id,base_scan_id,target_scan_id,rules_version) values (?,?,?,?,'fixture')", first.tenant(), first.repo(), first.scan(), second.scan()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into scan_comparisons(organization_id,repository_id,base_scan_id,target_scan_id,rules_version) values (?,?,?,?,'fixture')", first.tenant(), first.repo(), first.scan(), first.scan()));
        jdbc.update("insert into scan_comparisons(organization_id,repository_id,base_scan_id,target_scan_id,rules_version) values (?,?,?,?,'fixture')", first.tenant(), first.repo(), first.scan(), otherScan);
    }

    @Test void documentsCitationsReportsAndNotificationDeliveriesRemainTenantScoped() {
        var first = fixture(); var second = fixture();
        var document = jdbc.queryForObject("insert into documents(organization_id,name,document_type,origin) values (?,'Fixture','PDF','UPLOAD') returning id", UUID.class, first.tenant());
        jdbc.update("insert into document_repositories values (?,?,?)", first.tenant(), document, first.repo());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into document_repositories values (?,?,?)", first.tenant(), document, second.repo()));
        jdbc.update("insert into document_components values (?,?,?)", first.tenant(), document, first.component());
        var version = jdbc.queryForObject("insert into document_versions(organization_id,document_id,version_number,sha256,size_bytes,mime_type,original_filename,storage_backend,storage_key) values (?,?,1,?,100,'application/pdf','fixture.pdf','LOCAL','fixtures/original.pdf') returning id", UUID.class, first.tenant(), document, "a".repeat(64));
        var chunk = jdbc.queryForObject("insert into document_chunks(organization_id,document_version_id,ordinal,content,page_start,page_end,content_hash) values (?,?,1,'Fixture text',1,1,?) returning id", UUID.class, first.tenant(), version, "b".repeat(64));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into document_chunks(organization_id,document_version_id,ordinal,content,page_start,page_end,content_hash) values (?,?,2,'bad',1,null,?)", first.tenant(), version, "b".repeat(64)));
        var model = jdbc.queryForObject("insert into embedding_models(provider,model_name,revision,dimension) values ('ollama',?,'fixture',3) returning id", UUID.class, "fixture-" + version);
        jdbc.update("insert into document_chunk_embeddings(organization_id,document_chunk_id,embedding_model_id) values (?,?,?)", first.tenant(), chunk, model);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into document_chunk_embeddings(organization_id,document_chunk_id,embedding_model_id) values (?,?,?)", first.tenant(), chunk, model));
        var conversation = jdbc.queryForObject("insert into assistant_conversations(organization_id,created_by_membership_id) values (?,?) returning id", UUID.class, first.tenant(), first.member());
        var message = jdbc.queryForObject("insert into assistant_messages(organization_id,conversation_id,ordinal,role,content) values (?,?,1,'ASSISTANT','Fixture answer') returning id", UUID.class, first.tenant(), conversation);
        jdbc.update("insert into assistant_message_evidences(organization_id,message_id,ordinal,document_chunk_id,citation_label) values (?,?,1,?,'p.1')", first.tenant(), message, chunk);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into assistant_message_evidences(organization_id,message_id,ordinal,scan_component_id,citation_label) values (?,?,2,?,'foreign')", first.tenant(), message, second.occurrence()));
        var definition = jdbc.queryForObject("select id from report_definitions where code='DEPENDENCY'", UUID.class);
        var report = jdbc.queryForObject("insert into report_jobs(organization_id,definition_id,format) values (?,?,'CSV') returning id", UUID.class, first.tenant(), definition);
        jdbc.update("insert into report_scans values (?,?,?)", first.tenant(), report, first.scan());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into report_artifacts(organization_id,report_job_id,storage_key,sha256,size_bytes,media_type) values (?,?, 'fixture/report.csv',?,10,'text/csv')", second.tenant(), report, "c".repeat(64)));
        var notification = jdbc.queryForObject("insert into notifications(organization_id,event_type,deduplication_key,title,summary) values (?,'SCAN_FAILED',?,'Fixture','Fixture') returning id", UUID.class, first.tenant(), UUID.randomUUID().toString());
        jdbc.update("insert into notification_deliveries(organization_id,notification_id,membership_id,channel) values (?,?,?,'IN_APP')", first.tenant(), notification, first.member());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into notification_deliveries(organization_id,notification_id,membership_id,channel) values (?,?,?,'IN_APP')", first.tenant(), notification, second.member()));
    }

    @Test void validatedImpactNeedsExecutedEvidenceAndPrNeedsApproval() {
        var fixture = fixture();
        var commit = jdbc.queryForObject("insert into repository_commits(organization_id,repository_id,commit_sha) values (?,?,?) returning id", UUID.class, fixture.tenant(), fixture.repo(), "a".repeat(40));
        jdbc.update("update scans set repository_commit_id=? where id=?", commit, fixture.scan());
        var target = jdbc.queryForObject("insert into component_versions(organization_id,component_id,version) values (?,?,'2.0.0') returning id", UUID.class, fixture.tenant(), fixture.component());
        var analysis = jdbc.queryForObject("insert into impact_analyses(organization_id,repository_id,base_scan_id,base_commit_id,component_id,current_version_id,target_version_id,operation,method_version) values (?,?,?,?,?,?,?,'UPDATE','fixture') returning id", UUID.class, fixture.tenant(), fixture.repo(), fixture.scan(), commit, fixture.component(), fixture.version(), target);
        var simulation = jdbc.queryForObject("insert into change_simulations(organization_id,repository_id,impact_analysis_id,base_commit_id,environment_reference,configuration_version,timeout_seconds) values (?,?,?,?,'fixture-isolated','fixture',30) returning id", UUID.class, fixture.tenant(), fixture.repo(), analysis, commit);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into impact_findings(organization_id,impact_analysis_id,classification,summary) values (?,?,'VALIDATED','unproven')", fixture.tenant(), analysis));
        var run = jdbc.queryForObject("insert into simulation_test_runs(organization_id,simulation_id,tool_name,command_summary,status) values (?,?,'fixture','fixture command','PENDING') returning id", UUID.class, fixture.tenant(), simulation);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into impact_findings(organization_id,impact_analysis_id,classification,summary,validation_test_run_id) values (?,?,'VALIDATED','not executed',?)", fixture.tenant(), analysis, run));
        jdbc.update("update simulation_test_runs set status='COMPLETED',started_at=current_timestamp,finished_at=current_timestamp,exit_code=0 where id=?", run);
        jdbc.update("insert into impact_findings(organization_id,impact_analysis_id,classification,summary,validation_test_run_id) values (?,?,'VALIDATED','fixture evidence',?)", fixture.tenant(), analysis, run);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update simulation_test_runs set status='PENDING',finished_at=null where id=?", run));
        var otherAnalysis = jdbc.queryForObject("insert into impact_analyses(organization_id,repository_id,base_scan_id,component_id,current_version_id,operation,method_version) values (?,?,?,?,?,'REMOVE','fixture') returning id", UUID.class, fixture.tenant(), fixture.repo(), fixture.scan(), fixture.component(), fixture.version());
        var evidence = jdbc.queryForObject("insert into impact_evidences(organization_id,impact_analysis_id,evidence_type,excerpt,observed_at,content_hash) values (?,?,'FIXTURE','fixture',current_timestamp,?) returning id", UUID.class, fixture.tenant(), otherAnalysis, "a".repeat(64));
        var finding = jdbc.queryForObject("select id from impact_findings where organization_id=? and impact_analysis_id=?", UUID.class, fixture.tenant(), analysis);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into impact_finding_evidences values (?,?,?)", fixture.tenant(), finding, evidence));
        var recommendation = jdbc.queryForObject("insert into impact_recommendations(organization_id,impact_analysis_id,action,priority,explanation) values (?,?,'UPDATE',1,'fixture') returning id", UUID.class, fixture.tenant(), analysis);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into impact_recommendation_evidences values (?,?,?)", fixture.tenant(), recommendation, evidence));
        jdbc.update("update impact_evidences set impact_analysis_id=? where id=?", analysis, evidence);
        jdbc.update("insert into impact_finding_evidences values (?,?,?)", fixture.tenant(), finding, evidence);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into change_pull_request_proposals(organization_id,simulation_id,title,description,proposed_patch_storage_key,status) values (?,?,'Fixture','Fixture','fixture/patch','PUBLISHED')", fixture.tenant(), simulation));
        jdbc.update("insert into change_pull_request_proposals(organization_id,simulation_id,title,description,proposed_patch_storage_key,status,approved_by_membership_id,approved_at) values (?,?,'Fixture','Fixture','fixture/patch','APPROVED',?,current_timestamp)", fixture.tenant(), simulation, fixture.member());
    }

    @Test void upgradeFromV3PreservesVersionVariantsEdgesMembershipAndFindingEvidence() {
        String schema = "v3_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
        UUID tenant=UUID.randomUUID(), user=UUID.randomUUID(), repo=UUID.randomUUID(), scan=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID(), advisory=UUID.randomUUID();
        jdbc.update("insert into " + schema + ".organizations(id,name,slug) values (?,'Legacy','legacy')", tenant);
        jdbc.update("insert into " + schema + ".users(id,subject,email,display_name) values (?,'legacy-subject',?,'Legacy')", user, user + "@example.invalid");
        jdbc.update("insert into " + schema + ".memberships(organization_id,user_id,role) values (?,?,'OWNER')", tenant, user);
        jdbc.update("insert into " + schema + ".repositories(id,organization_id,provider,external_id,full_name,default_branch) values (?,?,'github','1','legacy/repo','main')", repo, tenant);
        jdbc.update("insert into " + schema + ".scans(id,organization_id,repository_id,git_reference,status,commit_sha) values (?,?,?,'main','PENDING',?)", scan, tenant, repo, "a".repeat(40));
        jdbc.update("insert into " + schema + ".components(id,organization_id,purl,ecosystem,namespace,name,version) values (?,?,'pkg:maven/org.example/core@1','Maven','org.example','core','1'),(?,?,'pkg:maven/org.example/core@1?type=jar','Maven','org.example','core','1')", first, tenant, second, tenant);
        jdbc.update("insert into " + schema + ".scan_components values (?,?,'parent',?,'DIRECT'),(?,?,'child',?,'TRANSITIVE')", tenant, scan, first, tenant, scan, second);
        jdbc.update("insert into " + schema + ".dependency_relationships values (?,?,'parent','child')", tenant, scan);
        jdbc.update("insert into " + schema + ".vulnerabilities(id,source,external_id,raw_payload) values (?,'OSV','legacy-advisory','{\"fixture\":true}')", advisory);
        jdbc.update("insert into " + schema + ".vulnerability_findings(organization_id,scan_id,bom_ref,vulnerability_id,fixed_versions) values (?,?,'parent',?,'[\"2\",\"2\"]')", tenant, scan, advisory);
        var latest = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
        assertEquals(8, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".components", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".component_versions", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from " + schema + ".component_identifiers", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from " + schema + ".scan_components", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".dependency_edges", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".membership_roles", Integer.class));
        assertEquals("legacy-subject", jdbc.queryForObject("select provider_subject from " + schema + ".user_identities", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".finding_fixed_versions", Integer.class));
        assertEquals("CANONICAL_JSON", jdbc.queryForObject("select hash_basis from " + schema + ".vulnerability_source_records", String.class));
        assertEquals("a".repeat(40), jdbc.queryForObject("select commit_sha from " + schema + ".repository_commits", String.class));
    }

    @Test void everyTenantRelationIsForcedRlsAndNoBusinessArraysExist() {
        assertEquals(0, jdbc.queryForObject("""
            select count(*) from pg_class relation join pg_namespace namespace on namespace.oid=relation.relnamespace
            where namespace.nspname='public' and relation.relkind='r' and (relation.relname='organizations' or exists
              (select 1 from pg_attribute column_info where column_info.attrelid=relation.oid and column_info.attname='organization_id' and not column_info.attisdropped))
              and not (relation.relrowsecurity and relation.relforcerowsecurity)
            """, Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='public' and data_type='ARRAY'", Integer.class));
        assertFalse(jdbc.queryForObject("select exists(select 1 from pg_extension where extname='vector')", Boolean.class));
    }

    @Test void jobRetriesCannotExceedTheirConfiguredLimit() {
        var fixture = fixture();
        var job = jdbc.queryForObject("insert into background_jobs(organization_id,job_type,max_attempts) values (?,'SCAN',1) returning id", UUID.class, fixture.tenant());
        jdbc.update("insert into background_job_attempts(organization_id,job_id,attempt_number,worker,status) values (?,?,1,'fixture','RUNNING')", fixture.tenant(), job);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into background_job_attempts(organization_id,job_id,attempt_number,worker,status) values (?,?,2,'fixture','RUNNING')", fixture.tenant(), job));
    }
}
