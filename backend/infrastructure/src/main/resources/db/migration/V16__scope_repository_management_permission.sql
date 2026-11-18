-- MEMBER can manage a repository only through an explicit team grant on that repository.
-- JdbcRepositoryPermissionAdapter requires a repository id for non-OWNER/ADMIN permissions,
-- so this capability cannot authorize organization-wide actions with a null repository.
INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'MEMBER' AND permission.code = 'repository.manage'
ON CONFLICT DO NOTHING;
