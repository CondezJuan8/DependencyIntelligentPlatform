-- Immutable tenant-scoped OSV check evidence. Public advisory writes remain isolated
-- behind dependency_catalog_writer; the HTTP runtime only writes its own scan rows.
CREATE TABLE scan_vulnerability_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    scan_component_id UUID NOT NULL,
    source TEXT NOT NULL REFERENCES vulnerability_sources(code) ON DELETE RESTRICT,
    assessment_status TEXT NOT NULL CHECK (assessment_status IN ('MATCHED','NO_KNOWN_ADVISORIES','INCOMPLETE','UNAVAILABLE')),
    checked_at TIMESTAMPTZ NOT NULL,
    source_modified_at TIMESTAMPTZ,
    response_sha256 TEXT CHECK (response_sha256 IS NULL OR response_sha256 ~ '^[a-f0-9]{64}$'),
    hash_basis TEXT CHECK (hash_basis IS NULL OR hash_basis = 'CANONICAL_JSON'),
    advisories JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(advisories) = 'array'),
    failure_code TEXT CHECK (failure_code IS NULL OR failure_code ~ '^[A-Z0-9_]{1,80}$'),
    UNIQUE (organization_id, id),
    CHECK ((assessment_status IN ('UNAVAILABLE','INCOMPLETE')) = (failure_code IS NOT NULL)),
    CHECK ((response_sha256 IS NULL) = (hash_basis IS NULL)),
    FOREIGN KEY (organization_id, scan_id, scan_component_id)
        REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_scan_vulnerability_assessment_latest
    ON scan_vulnerability_assessments(organization_id, scan_id, scan_component_id, checked_at DESC, id DESC);

ALTER TABLE scan_vulnerability_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_vulnerability_assessments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON scan_vulnerability_assessments
    USING (organization_id = app_private.current_organization_id())
    WITH CHECK (organization_id = app_private.current_organization_id());
CREATE POLICY schema_migration_access ON scan_vulnerability_assessments TO dependency_migrator
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON scan_vulnerability_assessments TO dependency_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON scan_vulnerability_assessments TO dependency_migrator;

COMMENT ON TABLE scan_vulnerability_assessments IS
    'vulnerability: Immutable per-scan-component OSV assessment, including negative/failed states and canonical JSON response evidence.';
