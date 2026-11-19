INSERT INTO integration_providers(code, name) VALUES ('local', 'Local folder upload') ON CONFLICT (code) DO NOTHING;

CREATE TABLE local_repository_sources (
 organization_id UUID NOT NULL,
 repository_id UUID NOT NULL,
 storage_backend TEXT NOT NULL CHECK (storage_backend = 'LOCAL'),
 storage_key TEXT NOT NULL CHECK (storage_key ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.zip$' AND storage_key !~ '(^|/)\.\.(/|$)'),
 sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
 size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
 file_count INTEGER NOT NULL CHECK (file_count BETWEEN 1 AND 2000),
 uploaded_by_membership_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (organization_id, repository_id),
 FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, uploaded_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_local_repository_sources_uploader ON local_repository_sources(organization_id, uploaded_by_membership_id);

ALTER TABLE local_repository_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE local_repository_sources FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON local_repository_sources
 USING (organization_id = app_private.current_organization_id())
 WITH CHECK (organization_id = app_private.current_organization_id());
CREATE POLICY schema_migration_access ON local_repository_sources TO dependency_migrator USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON local_repository_sources TO dependency_runtime;
