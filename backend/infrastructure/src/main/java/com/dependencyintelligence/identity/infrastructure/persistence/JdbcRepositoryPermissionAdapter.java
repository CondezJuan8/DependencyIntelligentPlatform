package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.port.out.RepositoryPermissionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JdbcRepositoryPermissionAdapter implements RepositoryPermissionStore {
    private final JdbcTemplate jdbc;

    public JdbcRepositoryPermissionAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean permits(UUID userId, UUID organizationId, UUID repositoryId, String permission) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists (
              select 1 from organization_memberships membership
              join organizations organization on organization.id = membership.organization_id and organization.status = 'ACTIVE'
              join users account on account.id = membership.user_id and account.is_active
              join membership_roles assignment on assignment.organization_id = membership.organization_id and assignment.membership_id = membership.id
              join roles role on role.id = assignment.role_id
              join role_permissions rp on rp.role_id = role.id
              join permissions permission on permission.id = rp.permission_id and permission.code = ?
              where membership.organization_id = ? and membership.user_id = ? and membership.status = 'ACTIVE'
                and (role.code in ('OWNER', 'ADMIN') or (?::uuid is not null and exists (
                  select 1 from team_memberships team
                  join repository_team_access access on access.organization_id = team.organization_id and access.team_id = team.team_id
                  where team.organization_id = membership.organization_id and team.membership_id = membership.id
                    and access.repository_id = ? and access.permission_id = permission.id
                )))
            )
            """, Boolean.class, permission, organizationId, userId, repositoryId, repositoryId));
    }
}
