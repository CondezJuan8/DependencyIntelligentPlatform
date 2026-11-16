package com.dependencyintelligence.organization.infrastructure.persistence;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import com.dependencyintelligence.organization.application.port.out.InvitationStore;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcInvitationAdapter implements InvitationStore {
    private final JdbcTemplate jdbc;
    public JdbcInvitationAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Invitee account(UUID user) {
        return jdbc.query("select email_normalized, email_verified_at is not null from users where id = ? and is_active for share",
            (row, index) -> new Invitee(row.getString(1), row.getBoolean(2)), user).stream().findFirst().orElseThrow(PermissionDeniedException::new);
    }
    @Override public boolean activeMember(UUID org, String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from organization_memberships m join users u on u.id = m.user_id
            where m.organization_id = ? and u.email_normalized = ? and m.status = 'ACTIVE')
            """, Boolean.class, org, email));
    }
    @Override public Invitation create(UUID org, String email, String hash, UUID inviter, OrganizationRole role, Instant now, Instant expires) {
        jdbc.update("update membership_invitations set status = 'EXPIRED' where organization_id = ? and email_normalized = ? and status = 'PENDING' and expires_at <= ?", org, email, Timestamp.from(now));
        UUID id = UUID.randomUUID();
        try { jdbc.update("""
            insert into membership_invitations(id, organization_id, email_normalized, token_hash, invited_by_membership_id, created_at, expires_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """, id, org, email, hash, inviter, Timestamp.from(now), Timestamp.from(expires)); }
        catch (DuplicateKeyException exception) { throw new AccessConflictException("Ya hay una invitación pendiente para ese correo. Revócala antes de enviar otra."); }
        jdbc.update("insert into invitation_roles select ?, ?, id from roles where code = ?", org, id, role.name());
        return new Invitation(id, email, "PENDING", expires, List.of(role));
    }
    @Override public PendingInvitation find(UUID org, UUID id) {
        return jdbc.query("""
            select * from membership_invitations where organization_id = ? and id = ? for update
            """, (row, index) -> new PendingInvitation(id, row.getString("email_normalized"), row.getString("token_hash"),
                row.getObject("invited_by_membership_id", UUID.class), row.getString("status"), row.getTimestamp("expires_at").toInstant(),
                jdbc.query("select r.code from invitation_roles ir join roles r on r.id = ir.role_id where ir.organization_id = ? and ir.invitation_id = ? order by r.code",
                    (roleRow, roleIndex) -> OrganizationRole.parse(roleRow.getString(1)), org, id)), org, id)
            .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada"));
    }
    @Override public void revoke(UUID org, UUID id) {
        jdbc.update("update membership_invitations set status = 'REVOKED' where organization_id = ? and id = ? and status = 'PENDING'", org, id);
    }
    @Override public UUID inviterUserId(UUID org, UUID membership) {
        return jdbc.queryForObject("select user_id from organization_memberships where organization_id = ? and id = ?", UUID.class, org, membership);
    }
    @Override public void accept(UUID org, UUID invitation, UUID user, UUID inviterUser, OrganizationRole role) {
        UUID membership = jdbc.queryForObject("""
            insert into organization_memberships(organization_id, user_id, invited_by_user_id) values (?, ?, ?)
            on conflict (organization_id, user_id) do update set status = 'ACTIVE', invited_by_user_id = excluded.invited_by_user_id, joined_at = current_timestamp
            returning id
            """, UUID.class, org, user, inviterUser);
        // Returning members must not inherit old team access or obsolete roles.
        jdbc.update("delete from team_memberships where organization_id = ? and membership_id = ?", org, membership);
        jdbc.update("delete from membership_roles where organization_id = ? and membership_id = ?", org, membership);
        jdbc.update("insert into membership_roles select ?, ?, id from roles where code = ?", org, membership, role.name());
        jdbc.update("update membership_invitations set status = 'ACCEPTED', accepted_by_user_id = ? where organization_id = ? and id = ?", user, org, invitation);
    }
}
