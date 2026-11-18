CREATE TABLE member_repository_permissions (
 organization_id UUID NOT NULL,
 membership_id UUID NOT NULL,
 repository_id UUID NOT NULL,
 permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, membership_id, repository_id, permission_id),
 FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id,id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id,id) ON DELETE RESTRICT
);
CREATE INDEX member_repository_permissions_lookup_idx
 ON member_repository_permissions(organization_id, membership_id, repository_id);
ALTER TABLE member_repository_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE member_repository_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON member_repository_permissions
 USING (organization_id=app_private.current_organization_id())
 WITH CHECK (organization_id=app_private.current_organization_id());
CREATE POLICY platform_administrator_read ON member_repository_permissions
 FOR SELECT USING (app_private.is_platform_administrator());
GRANT SELECT,INSERT,UPDATE,DELETE ON member_repository_permissions TO dependency_runtime,dependency_migrator;
