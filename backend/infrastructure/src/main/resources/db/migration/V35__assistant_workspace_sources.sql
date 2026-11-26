CREATE TABLE assistant_message_workspace_sources (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
 message_id UUID NOT NULL, ordinal INTEGER NOT NULL CHECK (ordinal > 0),
 repository_id UUID NOT NULL, source_type TEXT NOT NULL CHECK (source_type IN ('OSV','SCAN','REPORT')),
 source_name TEXT NOT NULL, filename TEXT NOT NULL, section TEXT, content TEXT NOT NULL, source_url TEXT NOT NULL,
 UNIQUE (organization_id,id), UNIQUE (organization_id,message_id,ordinal),
 FOREIGN KEY (organization_id,message_id) REFERENCES assistant_messages(organization_id,id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id,repository_id) REFERENCES repositories(organization_id,id) ON DELETE RESTRICT
);
ALTER TABLE assistant_message_workspace_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistant_message_workspace_sources FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assistant_message_workspace_sources
 USING (organization_id=app_private.current_organization_id()) WITH CHECK (organization_id=app_private.current_organization_id());
CREATE POLICY schema_migration_access ON assistant_message_workspace_sources TO dependency_migrator
 USING (true) WITH CHECK (true);
GRANT SELECT,INSERT,UPDATE,DELETE ON assistant_message_workspace_sources TO dependency_runtime,dependency_migrator;
