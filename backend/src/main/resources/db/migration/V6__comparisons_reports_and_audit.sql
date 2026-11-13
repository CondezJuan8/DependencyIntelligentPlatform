-- Immutable after application. See docs/database/README.md.

CREATE TABLE scan_comparisons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    base_scan_id uuid NOT NULL,
    target_scan_id uuid NOT NULL,
    method_version text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,base_scan_id,target_scan_id,method_version),
    CHECK (base_scan_id<>target_scan_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans (organization_id, repository_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, target_scan_id) REFERENCES scans (organization_id, repository_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_comparisons_fk1 ON scan_comparisons (organization_id, repository_id, base_scan_id);

CREATE INDEX ix_scan_comparisons_fk2 ON scan_comparisons (organization_id, repository_id, target_scan_id);

CREATE TABLE scan_component_changes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    comparison_id uuid NOT NULL,
    component_id uuid NOT NULL,
    old_version_id uuid,
    new_version_id uuid,
    change_type text NOT NULL CHECK (change_type IN ('ADDED','REMOVED','UPDATED','UNCHANGED')),
    evidence text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,comparison_id,component_id,old_version_id,new_version_id),
    CHECK ((change_type='ADDED' AND old_version_id IS NULL AND new_version_id IS NOT NULL) OR (change_type='REMOVED' AND old_version_id IS NOT NULL AND new_version_id IS NULL) OR (change_type='UPDATED' AND old_version_id IS NOT NULL AND new_version_id IS NOT NULL AND old_version_id<>new_version_id) OR (change_type='UNCHANGED' AND old_version_id IS NOT NULL AND old_version_id=new_version_id)),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id, old_version_id) REFERENCES component_versions (organization_id, component_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id, new_version_id) REFERENCES component_versions (organization_id, component_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_component_changes_fk2 ON scan_component_changes (organization_id, component_id);

CREATE INDEX ix_scan_component_changes_fk3 ON scan_component_changes (organization_id, component_id, old_version_id);

CREATE INDEX ix_scan_component_changes_fk4 ON scan_component_changes (organization_id, component_id, new_version_id);

CREATE TABLE report_definitions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type_id uuid NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (name),
    FOREIGN KEY (report_type_id) REFERENCES report_types (id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_definitions_fk0 ON report_definitions (report_type_id);

CREATE TABLE report_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    definition_id uuid NOT NULL,
    format_id uuid NOT NULL,
    requested_by_membership_id uuid NOT NULL,
    job_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    parameters jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,job_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (definition_id) REFERENCES report_definitions (id) ON DELETE RESTRICT,
    FOREIGN KEY (format_id) REFERENCES report_formats (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_jobs_fk1 ON report_jobs (definition_id);

CREATE INDEX ix_report_jobs_fk2 ON report_jobs (format_id);

CREATE INDEX ix_report_jobs_fk3 ON report_jobs (organization_id, requested_by_membership_id);

CREATE TABLE report_job_scans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    report_job_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,report_job_id,scan_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_job_scans_fk2 ON report_job_scans (organization_id, scan_id);

CREATE TABLE report_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    report_job_id uuid NOT NULL,
    sha256 text NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    size_bytes bigint NOT NULL CHECK (size_bytes>=0),
    content_type text NOT NULL,
    storage_reference text NOT NULL,
    generated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_artifacts_fk1 ON report_artifacts (organization_id, report_job_id);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    actor_membership_id uuid,
    action text NOT NULL,
    resource_type text NOT NULL,
    resource_reference text NOT NULL,
    result text NOT NULL CHECK (result IN ('SUCCESS','DENIED','FAILURE')),
    correlation_id uuid,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, actor_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_audit_events_fk1 ON audit_events (organization_id, actor_membership_id);

CREATE INDEX ix_audit_timeline ON audit_events (organization_id,occurred_at DESC,id);

CREATE TABLE audit_event_changes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    audit_event_id uuid NOT NULL,
    attribute text NOT NULL,
    old_value_redacted text,
    new_value_redacted text,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,audit_event_id,attribute),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, audit_event_id) REFERENCES audit_events (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE data_retention_policies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    data_type text NOT NULL,
    retention_days integer CHECK (retention_days>=0),
    action text NOT NULL CHECK (action IN ('DELETE','ANONYMIZE','KEEP')),
    legal_basis text,
    is_enabled boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,data_type),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TABLE data_deletion_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    requested_by_membership_id uuid NOT NULL,
    reason text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_data_deletion_requests_fk1 ON data_deletion_requests (organization_id, requested_by_membership_id);

CREATE TABLE deletion_request_repositories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    deletion_request_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,deletion_request_id,repository_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, deletion_request_id) REFERENCES data_deletion_requests (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_deletion_request_repositories_fk2 ON deletion_request_repositories (organization_id, repository_id);

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS app;
CREATE FUNCTION app.current_organization_id() RETURNS uuid LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.organization_id',true),'')::uuid $$;
REVOKE ALL ON SCHEMA app FROM PUBLIC;
GRANT USAGE ON SCHEMA app TO dependency_app;
GRANT EXECUTE ON FUNCTION app.current_organization_id() TO dependency_app;


ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organizations TO PUBLIC USING (id=app.current_organization_id()) WITH CHECK (id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON organizations TO dependency_app;

GRANT SELECT ON roles TO dependency_app;

GRANT SELECT ON permissions TO dependency_app;

GRANT SELECT ON role_permissions TO dependency_app;

ALTER TABLE organization_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organization_memberships TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON organization_memberships TO dependency_app;

ALTER TABLE membership_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON membership_roles TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON membership_roles TO dependency_app;

ALTER TABLE membership_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_invitations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON membership_invitations TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON membership_invitations TO dependency_app;

ALTER TABLE organization_teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_teams FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organization_teams TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON organization_teams TO dependency_app;

ALTER TABLE team_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON team_memberships TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON team_memberships TO dependency_app;

GRANT SELECT ON integration_providers TO dependency_app;

GRANT SELECT ON package_ecosystems TO dependency_app;

GRANT SELECT ON dependency_scopes TO dependency_app;

GRANT SELECT ON dependency_relationship_types TO dependency_app;

GRANT SELECT ON vulnerability_sources TO dependency_app;

GRANT SELECT ON scanner_tools TO dependency_app;

GRANT SELECT ON license_catalog TO dependency_app;

GRANT SELECT ON job_types TO dependency_app;

GRANT SELECT ON scan_stages TO dependency_app;

GRANT SELECT ON report_types TO dependency_app;

GRANT SELECT ON report_formats TO dependency_app;

GRANT SELECT ON document_types TO dependency_app;

GRANT SELECT ON notification_channels TO dependency_app;

GRANT SELECT ON severity_levels TO dependency_app;

ALTER TABLE integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON integrations TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON integrations TO dependency_app;

ALTER TABLE github_installations ENABLE ROW LEVEL SECURITY;
ALTER TABLE github_installations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON github_installations TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON github_installations TO dependency_app;

ALTER TABLE repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE repositories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON repositories TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON repositories TO dependency_app;

ALTER TABLE repository_commits ENABLE ROW LEVEL SECURITY;
ALTER TABLE repository_commits FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON repository_commits TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON repository_commits TO dependency_app;

ALTER TABLE repository_branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE repository_branches FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON repository_branches TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON repository_branches TO dependency_app;

ALTER TABLE repository_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE repository_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON repository_settings TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON repository_settings TO dependency_app;

ALTER TABLE repository_team_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE repository_team_access FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON repository_team_access TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON repository_team_access TO dependency_app;

ALTER TABLE webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON webhook_events TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON webhook_events TO dependency_app;

ALTER TABLE integration_sync_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_sync_runs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON integration_sync_runs TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON integration_sync_runs TO dependency_app;

ALTER TABLE background_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE background_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON background_jobs TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON background_jobs TO dependency_app;

ALTER TABLE background_job_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE background_job_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON background_job_attempts TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON background_job_attempts TO dependency_app;

ALTER TABLE background_job_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE background_job_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON background_job_events TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON background_job_events TO dependency_app;

ALTER TABLE scan_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_profiles TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_profiles TO dependency_app;

ALTER TABLE scan_profile_ecosystems ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_profile_ecosystems FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_profile_ecosystems TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_profile_ecosystems TO dependency_app;

ALTER TABLE scans ENABLE ROW LEVEL SECURITY;
ALTER TABLE scans FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scans TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scans TO dependency_app;

ALTER TABLE scan_stage_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_stage_executions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_stage_executions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_stage_executions TO dependency_app;

ALTER TABLE scan_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_logs TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_logs TO dependency_app;

ALTER TABLE scan_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_artifacts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_artifacts TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_artifacts TO dependency_app;

ALTER TABLE sbom_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE sbom_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sbom_documents TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON sbom_documents TO dependency_app;

ALTER TABLE sbom_external_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE sbom_external_references FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sbom_external_references TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON sbom_external_references TO dependency_app;

ALTER TABLE components ENABLE ROW LEVEL SECURITY;
ALTER TABLE components FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON components TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON components TO dependency_app;

ALTER TABLE component_identifiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_identifiers FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON component_identifiers TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON component_identifiers TO dependency_app;

ALTER TABLE component_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON component_versions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON component_versions TO dependency_app;

ALTER TABLE scan_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_components FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_components TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_components TO dependency_app;

ALTER TABLE dependency_edges ENABLE ROW LEVEL SECURITY;
ALTER TABLE dependency_edges FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON dependency_edges TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON dependency_edges TO dependency_app;

ALTER TABLE component_hashes ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_hashes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON component_hashes TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON component_hashes TO dependency_app;

ALTER TABLE component_licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_licenses FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON component_licenses TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON component_licenses TO dependency_app;

ALTER TABLE scan_component_licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_component_licenses FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_component_licenses TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_component_licenses TO dependency_app;

ALTER TABLE component_external_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_external_references FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON component_external_references TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON component_external_references TO dependency_app;

GRANT SELECT ON vulnerabilities TO dependency_app;

GRANT SELECT ON vulnerability_aliases TO dependency_app;

GRANT SELECT ON vulnerability_references TO dependency_app;

GRANT SELECT ON vulnerability_severities TO dependency_app;

GRANT SELECT ON vulnerability_affected_packages TO dependency_app;

GRANT SELECT ON vulnerability_affected_ranges TO dependency_app;

GRANT SELECT ON vulnerability_range_events TO dependency_app;

GRANT SELECT ON vulnerability_fixed_versions TO dependency_app;

GRANT SELECT ON vulnerability_source_records TO dependency_app;

ALTER TABLE scan_vulnerability_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_vulnerability_findings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_vulnerability_findings TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_vulnerability_findings TO dependency_app;

ALTER TABLE finding_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE finding_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON finding_evidence TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON finding_evidence TO dependency_app;

ALTER TABLE scan_comparisons ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_comparisons FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_comparisons TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_comparisons TO dependency_app;

ALTER TABLE scan_component_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_component_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_component_changes TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON scan_component_changes TO dependency_app;

GRANT SELECT ON report_definitions TO dependency_app;

ALTER TABLE report_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_jobs TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON report_jobs TO dependency_app;

ALTER TABLE report_job_scans ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_job_scans FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_job_scans TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON report_job_scans TO dependency_app;

ALTER TABLE report_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_artifacts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_artifacts TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON report_artifacts TO dependency_app;

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_events TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON audit_events TO dependency_app;

ALTER TABLE audit_event_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_event_changes TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON audit_event_changes TO dependency_app;

ALTER TABLE data_retention_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_retention_policies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON data_retention_policies TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON data_retention_policies TO dependency_app;

ALTER TABLE data_deletion_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_deletion_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON data_deletion_requests TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON data_deletion_requests TO dependency_app;

ALTER TABLE deletion_request_repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE deletion_request_repositories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON deletion_request_repositories TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON deletion_request_repositories TO dependency_app;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_member_visibility ON users FOR SELECT TO dependency_app USING
(EXISTS (SELECT 1 FROM organization_memberships m WHERE m.user_id=users.id AND m.status='ACTIVE'));
GRANT SELECT ON users TO dependency_app;
REVOKE ALL ON user_identities FROM dependency_app;
REVOKE UPDATE,DELETE ON audit_events,audit_event_changes FROM dependency_app;
CREATE FUNCTION app.reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit records are append-only' USING ERRCODE='42501'; END $$;
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION app.reject_audit_mutation();
CREATE TRIGGER audit_changes_immutable BEFORE UPDATE OR DELETE ON audit_event_changes FOR EACH ROW EXECUTE FUNCTION app.reject_audit_mutation();

