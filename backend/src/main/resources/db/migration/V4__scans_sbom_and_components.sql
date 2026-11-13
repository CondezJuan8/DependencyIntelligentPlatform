-- Immutable after application. See docs/database/README.md.

CREATE TABLE scan_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    name text NOT NULL,
    configuration_version text NOT NULL,
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,name,configuration_version),
    CHECK (jsonb_typeof(settings)='object'),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TABLE scan_profile_ecosystems (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_profile_id uuid NOT NULL,
    package_ecosystem_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_profile_id,package_ecosystem_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_profile_ecosystems_fk2 ON scan_profile_ecosystems (package_ecosystem_id);

CREATE TABLE scans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    commit_id uuid NOT NULL,
    scan_profile_id uuid NOT NULL,
    job_id uuid NOT NULL,
    requested_by_membership_id uuid,
    scanner_tool_id uuid NOT NULL,
    scanner_version text NOT NULL,
    trigger_type text NOT NULL CHECK (trigger_type IN ('MANUAL','WEBHOOK','SCHEDULED')),
    request_key text NOT NULL,
    source_ref text,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,request_key),
    UNIQUE NULLS NOT DISTINCT (organization_id,job_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, commit_id) REFERENCES repository_commits (organization_id, repository_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (scanner_tool_id) REFERENCES scanner_tools (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scans_fk1 ON scans (organization_id, repository_id, commit_id);

CREATE INDEX ix_scans_fk2 ON scans (organization_id, scan_profile_id);

CREATE INDEX ix_scans_fk4 ON scans (organization_id, requested_by_membership_id);

CREATE INDEX ix_scans_fk5 ON scans (scanner_tool_id);

CREATE INDEX ix_scans_history ON scans (organization_id,repository_id,created_at DESC,id);

CREATE INDEX ix_scans_commit_profile ON scans (organization_id,commit_id,scan_profile_id);

CREATE TABLE scan_stage_executions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    stage_id uuid NOT NULL,
    attempt_number integer NOT NULL DEFAULT 1 CHECK (attempt_number>0),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    error_code text,
    metrics jsonb,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id,stage_id,attempt_number),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (stage_id) REFERENCES scan_stages (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_stage_executions_fk2 ON scan_stage_executions (stage_id);

CREATE TABLE scan_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    level text NOT NULL CHECK (level IN ('DEBUG','INFO','WARN','ERROR')),
    message text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_logs_fk1 ON scan_logs (organization_id, scan_id);

CREATE TABLE scan_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('SBOM','LOG','PROVIDER_RESULT')),
    format text NOT NULL,
    sha256 text NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    size_bytes bigint NOT NULL CHECK (size_bytes>=0),
    storage_reference text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id,id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE sbom_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    artifact_id uuid NOT NULL,
    serial_number text,
    spec_version text NOT NULL,
    generated_at timestamptz NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id, artifact_id) REFERENCES scan_artifacts (organization_id, scan_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_sbom_documents_fk1 ON sbom_documents (organization_id, scan_id, artifact_id);

CREATE TABLE sbom_external_references (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    sbom_document_id uuid NOT NULL,
    reference_type text NOT NULL,
    url text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,sbom_document_id,reference_type,url),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, sbom_document_id) REFERENCES sbom_documents (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE components (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    package_ecosystem_id uuid NOT NULL,
    namespace text NOT NULL DEFAULT '',
    name text NOT NULL,
    normalized_name text NOT NULL,
    purl_type text,
    description text,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,package_ecosystem_id,namespace,normalized_name),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems (id) ON DELETE RESTRICT
);

CREATE INDEX ix_components_fk1 ON components (package_ecosystem_id);

CREATE TABLE component_identifiers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_id uuid NOT NULL,
    identifier_type text NOT NULL CHECK (identifier_type IN ('PURL','CPE','SWID')),
    value text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,identifier_type,value),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_component_identifiers_fk1 ON component_identifiers (organization_id, component_id);

CREATE TABLE component_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_id uuid NOT NULL,
    version text NOT NULL,
    normalized_version text,
    published_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,component_id,version),
    UNIQUE NULLS NOT DISTINCT (organization_id,component_id,id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE scan_components (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    bom_ref text NOT NULL,
    scope_id uuid,
    is_root boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id,bom_ref),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id,id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (scope_id) REFERENCES dependency_scopes (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_components_fk2 ON scan_components (organization_id, component_version_id);

CREATE INDEX ix_scan_components_fk3 ON scan_components (scope_id);

CREATE INDEX ix_scan_components_inventory ON scan_components (organization_id,component_version_id,scan_id);

CREATE UNIQUE INDEX uq_scan_root ON scan_components (organization_id,scan_id) WHERE is_root;

CREATE TABLE dependency_edges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    source_scan_component_id uuid NOT NULL,
    target_scan_component_id uuid NOT NULL,
    relationship_type_id uuid NOT NULL,
    scope_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id),
    CHECK (source_scan_component_id<>target_scan_component_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id, source_scan_component_id) REFERENCES scan_components (organization_id, scan_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id, target_scan_component_id) REFERENCES scan_components (organization_id, scan_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (relationship_type_id) REFERENCES dependency_relationship_types (id) ON DELETE RESTRICT,
    FOREIGN KEY (scope_id) REFERENCES dependency_scopes (id) ON DELETE RESTRICT
);

CREATE INDEX ix_dependency_edges_fk2 ON dependency_edges (organization_id, scan_id, target_scan_component_id);

CREATE INDEX ix_dependency_edges_fk3 ON dependency_edges (relationship_type_id);

CREATE INDEX ix_dependency_edges_fk4 ON dependency_edges (scope_id);

CREATE TABLE component_hashes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    algorithm text NOT NULL,
    digest text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,component_version_id,algorithm,digest),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE component_licenses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    license_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,component_version_id,license_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (license_id) REFERENCES license_catalog (id) ON DELETE RESTRICT
);

CREATE INDEX ix_component_licenses_fk2 ON component_licenses (license_id);

CREATE TABLE scan_component_licenses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_component_id uuid NOT NULL,
    license_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_component_id,license_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (license_id) REFERENCES license_catalog (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_component_licenses_fk2 ON scan_component_licenses (license_id);

CREATE TABLE component_external_references (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    reference_type text NOT NULL,
    url text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,component_version_id,reference_type,url),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT
);
