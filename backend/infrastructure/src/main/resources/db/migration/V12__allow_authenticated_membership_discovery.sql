-- Authentication supplies this context only inside the membership discovery transaction.
-- Existing tenant policies continue to govern operations scoped to an organization.
CREATE OR REPLACE FUNCTION app_private.current_user_id() RETURNS UUID
LANGUAGE SQL STABLE SET search_path = pg_catalog AS $$
 SELECT nullif(current_setting('app.user_id', true), '')::uuid;
$$;
REVOKE ALL ON FUNCTION app_private.current_user_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_private.current_user_id() TO dependency_runtime, dependency_migrator;

CREATE POLICY membership_self_discovery ON organization_memberships FOR SELECT TO dependency_runtime
 USING (app_private.current_organization_id() IS NULL AND user_id = app_private.current_user_id()
        AND status = 'ACTIVE');
CREATE POLICY organization_self_discovery ON organizations FOR SELECT TO dependency_runtime
 USING (app_private.current_organization_id() IS NULL AND status = 'ACTIVE' AND EXISTS (
   SELECT 1 FROM organization_memberships membership
   JOIN users account ON account.id = membership.user_id AND account.is_active
   WHERE membership.organization_id = organizations.id AND membership.user_id = app_private.current_user_id()
     AND membership.status = 'ACTIVE'
 ));
