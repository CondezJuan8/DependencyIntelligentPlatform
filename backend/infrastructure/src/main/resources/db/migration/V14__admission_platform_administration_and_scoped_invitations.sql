-- Existing accounts retain access. New public registrations explicitly request admission.
ALTER TABLE users ADD COLUMN account_type TEXT NOT NULL DEFAULT 'OWNER' CHECK (account_type IN ('OWNER','MEMBER'));
ALTER TABLE users ADD COLUMN admission_status TEXT NOT NULL DEFAULT 'APPROVED' CHECK (admission_status IN ('PENDING_REVIEW','APPROVED','REJECTED'));
ALTER TABLE users ADD COLUMN reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE users ADD COLUMN reviewed_at TIMESTAMPTZ;
CREATE TABLE platform_administrators (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
REVOKE ALL ON platform_administrators FROM PUBLIC;
GRANT SELECT ON platform_administrators TO dependency_runtime;
GRANT ALL ON platform_administrators TO dependency_migrator;

CREATE OR REPLACE FUNCTION app_private.is_platform_administrator() RETURNS BOOLEAN
LANGUAGE SQL STABLE SET search_path = pg_catalog, public AS $$
 SELECT EXISTS (SELECT 1 FROM public.platform_administrators a JOIN public.users u ON u.id=a.user_id
   WHERE a.user_id = app_private.current_user_id() AND u.is_active AND u.email_verified_at IS NOT NULL AND u.admission_status='APPROVED');
$$;
REVOKE ALL ON FUNCTION app_private.is_platform_administrator() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_private.is_platform_administrator() TO dependency_runtime,dependency_migrator;

ALTER TABLE organization_teams ADD COLUMN is_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE organization_memberships ADD COLUMN repository_scope TEXT NOT NULL DEFAULT 'TEAM' CHECK(repository_scope IN ('TEAM','ASSIGNED'));
CREATE TABLE member_repository_assignments (
 organization_id UUID NOT NULL, membership_id UUID NOT NULL, team_id UUID NOT NULL, repository_id UUID NOT NULL,
 PRIMARY KEY(organization_id,membership_id,team_id,repository_id),
 FOREIGN KEY(organization_id,membership_id) REFERENCES organization_memberships(organization_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(organization_id,team_id) REFERENCES organization_teams(organization_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(organization_id,repository_id) REFERENCES repositories(organization_id,id) ON DELETE RESTRICT
);
ALTER TABLE member_repository_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE member_repository_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON member_repository_assignments
 USING(organization_id=app_private.current_organization_id()) WITH CHECK(organization_id=app_private.current_organization_id());
GRANT SELECT,INSERT,UPDATE,DELETE ON member_repository_assignments TO dependency_runtime,dependency_migrator;

ALTER TABLE membership_invitations DROP CONSTRAINT membership_invitations_status_check;
ALTER TABLE membership_invitations ADD CONSTRAINT membership_invitations_status_check
 CHECK(status IN ('PENDING','AWAITING_OWNER','ACCEPTED','EXPIRED','REVOKED'));
ALTER TABLE membership_invitations ADD COLUMN target_team_id UUID;
ALTER TABLE membership_invitations ADD COLUMN repository_id UUID;
ALTER TABLE membership_invitations ADD COLUMN accepted_at TIMESTAMPTZ;
ALTER TABLE membership_invitations ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE membership_invitations ADD COLUMN approved_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE membership_invitations ADD CONSTRAINT fk_invitation_target_team FOREIGN KEY(organization_id,target_team_id) REFERENCES organization_teams(organization_id,id) ON DELETE RESTRICT;
ALTER TABLE membership_invitations ADD CONSTRAINT fk_invitation_repository FOREIGN KEY(organization_id,repository_id) REFERENCES repositories(organization_id,id) ON DELETE RESTRICT;
DROP INDEX uq_pending_invitation;
CREATE UNIQUE INDEX uq_pending_invitation ON membership_invitations(organization_id,email_normalized) WHERE status IN ('PENDING','AWAITING_OWNER');

CREATE TABLE platform_audit_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 action TEXT NOT NULL, resource_type TEXT NOT NULL, resource_id UUID NOT NULL,
 reason TEXT NOT NULL CHECK(length(btrim(reason)) BETWEEN 3 AND 500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE platform_audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_audit_events FORCE ROW LEVEL SECURITY;
CREATE POLICY administrator_access ON platform_audit_events USING(app_private.is_platform_administrator())
 WITH CHECK(app_private.is_platform_administrator() AND actor_id=app_private.current_user_id());
CREATE TRIGGER platform_audit_append_only BEFORE UPDATE OR DELETE ON platform_audit_events
 FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation();
GRANT SELECT,INSERT ON platform_audit_events TO dependency_runtime;
GRANT ALL ON platform_audit_events TO dependency_migrator;

-- Narrow global administration policies; no runtime BYPASSRLS or owner privileges.
DO $$ DECLARE relation TEXT; BEGIN
 FOREACH relation IN ARRAY ARRAY['organizations','organization_memberships','membership_roles','organization_teams','team_memberships','repositories','membership_invitations','invitation_roles','repository_team_access','member_repository_assignments'] LOOP
  EXECUTE format('CREATE POLICY platform_administrator_read ON %I FOR SELECT USING(app_private.is_platform_administrator())',relation);
 END LOOP;
 FOREACH relation IN ARRAY ARRAY['organization_teams','repositories','organization_memberships'] LOOP
  EXECUTE format('CREATE POLICY platform_administrator_update ON %I FOR UPDATE USING(app_private.is_platform_administrator()) WITH CHECK(app_private.is_platform_administrator())',relation);
 END LOOP;
END $$;
