CREATE TABLE organization_settings (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    theme TEXT NOT NULL DEFAULT 'SYSTEM' CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    language TEXT NOT NULL DEFAULT 'ES' CHECK (language IN ('ES', 'EN')),
    updated_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO organization_settings (organization_id)
SELECT id FROM organizations WHERE status = 'ACTIVE';

ALTER TABLE organization_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_settings FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_settings_member_read ON organization_settings
    FOR SELECT USING (
        organization_id = app_private.current_organization_id()
        AND EXISTS (
            SELECT 1 FROM organization_memberships membership
            JOIN users account ON account.id = membership.user_id
            WHERE membership.organization_id = organization_settings.organization_id
              AND membership.user_id = app_private.current_user_id()
              AND membership.status = 'ACTIVE'
              AND account.is_active
              AND account.email_verified_at IS NOT NULL
              AND account.admission_status = 'APPROVED'
        )
    );

CREATE POLICY organization_settings_manager_insert ON organization_settings
    FOR INSERT WITH CHECK (
        organization_id = app_private.current_organization_id()
        AND EXISTS (
            SELECT 1 FROM organization_memberships membership
            JOIN membership_roles assignment ON assignment.organization_id = membership.organization_id AND assignment.membership_id = membership.id
            JOIN roles role ON role.id = assignment.role_id AND role.code IN ('OWNER', 'ADMIN')
            JOIN users account ON account.id = membership.user_id AND account.is_active
                AND account.email_verified_at IS NOT NULL AND account.admission_status = 'APPROVED'
            WHERE membership.organization_id = organization_settings.organization_id
              AND membership.user_id = app_private.current_user_id()
              AND membership.status = 'ACTIVE'
        )
    );

CREATE POLICY organization_settings_manager_update ON organization_settings
    FOR UPDATE USING (
        organization_id = app_private.current_organization_id()
        AND EXISTS (
            SELECT 1 FROM organization_memberships membership
            JOIN membership_roles assignment ON assignment.organization_id = membership.organization_id AND assignment.membership_id = membership.id
            JOIN roles role ON role.id = assignment.role_id AND role.code IN ('OWNER', 'ADMIN')
            JOIN users account ON account.id = membership.user_id AND account.is_active
                AND account.email_verified_at IS NOT NULL AND account.admission_status = 'APPROVED'
            WHERE membership.organization_id = organization_settings.organization_id
              AND membership.user_id = app_private.current_user_id()
              AND membership.status = 'ACTIVE'
        )
    ) WITH CHECK (organization_id = app_private.current_organization_id());

GRANT SELECT, INSERT, UPDATE ON organization_settings TO dependency_runtime, dependency_migrator;
