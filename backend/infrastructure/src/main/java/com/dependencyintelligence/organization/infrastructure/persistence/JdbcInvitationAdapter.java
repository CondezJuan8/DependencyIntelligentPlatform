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
    public JdbcInvitationAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Invitee account(UUID user) {
        return jdbc.query("select email_normalized,email_verified_at is not null from users where id=? and is_active for share",
            (r,i)->new Invitee(r.getString(1),r.getBoolean(2)),user).stream().findFirst().orElseThrow(PermissionDeniedException::new);
    }
    @Override public boolean platformAdministrator(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from platform_administrators a join users u on u.id=a.user_id where a.user_id=? and u.is_active and u.email_verified_at is not null and u.admission_status='APPROVED')",Boolean.class,user));
    }
    @Override public boolean activeMember(UUID org,String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organization_memberships m join users u on u.id=m.user_id where m.organization_id=? and u.email_normalized=? and m.status='ACTIVE')",Boolean.class,org,email));
    }
    @Override public void target(UUID org,UUID team,UUID repository) {
        if(team==null || repository==null) throw new AccessConflictException("Solicita una invitación con equipo y repositorio");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from organization_teams t join repository_team_access a on a.organization_id=t.organization_id and a.team_id=t.id
              join repositories r on r.organization_id=a.organization_id and r.id=a.repository_id and r.is_enabled
              join permissions p on p.id=a.permission_id and p.code='repository.read'
              where t.organization_id=? and t.id=? and t.is_enabled and r.id=?)
            """,Boolean.class,org,team,repository))) throw new AccessConflictException("El equipo necesita permiso de lectura sobre un repositorio habilitado");
    }
    @Override public Invitation create(UUID org,String email,String hash,UUID inviter,OrganizationRole role,Instant now,Instant expires,UUID team,UUID repository) {
        jdbc.update("update membership_invitations set status='EXPIRED' where organization_id=? and email_normalized=? and status in ('PENDING','AWAITING_OWNER') and expires_at<=?",org,email,Timestamp.from(now));
        UUID id=UUID.randomUUID();
        try { jdbc.update("insert into membership_invitations(id,organization_id,email_normalized,token_hash,invited_by_membership_id,created_at,expires_at,target_team_id,repository_id) values(?,?,?,?,?,?,?,?,?)",id,org,email,hash,inviter,Timestamp.from(now),Timestamp.from(expires),team,repository); }
        catch(DuplicateKeyException e){throw new AccessConflictException("Ya hay una invitación pendiente para ese correo. Revócala antes de enviar otra.");}
        jdbc.update("insert into invitation_roles select ?,?,id from roles where code=?",org,id,role.name());
        return new Invitation(id,email,"PENDING",expires,List.of(role),team,repository);
    }
    @Override public PendingInvitation find(UUID org,UUID id) {
        return jdbc.query("select * from membership_invitations where organization_id=? and id=? for update",(r,i)->new PendingInvitation(id,r.getString("email_normalized"),r.getString("token_hash"),r.getObject("invited_by_membership_id",UUID.class),r.getString("status"),r.getTimestamp("expires_at").toInstant(),
            jdbc.query("select r.code from invitation_roles i join roles r on r.id=i.role_id where i.organization_id=? and i.invitation_id=? order by r.code",(role,n)->OrganizationRole.parse(role.getString(1)),org,id),r.getObject("target_team_id",UUID.class),r.getObject("repository_id",UUID.class),r.getObject("accepted_by_user_id",UUID.class)),org,id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Invitación no encontrada"));
    }
    @Override public void revoke(UUID org,UUID id) { jdbc.update("update membership_invitations set status='REVOKED' where organization_id=? and id=? and status in ('PENDING','AWAITING_OWNER')",org,id); }
    @Override public void awaitOwner(UUID org,UUID id,UUID user) { jdbc.update("update membership_invitations set status='AWAITING_OWNER',accepted_by_user_id=?,accepted_at=clock_timestamp() where organization_id=? and id=?",user,org,id); }
    @Override public UUID inviterUserId(UUID org,UUID membership) { return jdbc.queryForObject("select user_id from organization_memberships where organization_id=? and id=?",UUID.class,org,membership); }
    @Override public void accept(UUID org,UUID invitation,UUID user,UUID inviter,OrganizationRole role,UUID approver) {
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organization_memberships m join membership_roles mr on mr.organization_id=m.organization_id and mr.membership_id=m.id join roles r on r.id=mr.role_id where m.organization_id=? and m.user_id=? and r.code in ('OWNER','ADMIN'))",Boolean.class,org,user))) throw new AccessConflictException("Esta cuenta ya gestiona la organización; no se sustituye su rol mediante invitación");
        var source=find(org,invitation);
        UUID membership=jdbc.queryForObject("""
            insert into organization_memberships(organization_id,user_id,invited_by_user_id,repository_scope) values(?,?,?,'ASSIGNED')
            on conflict(organization_id,user_id) do update set status='ACTIVE',invited_by_user_id=excluded.invited_by_user_id,joined_at=current_timestamp returning id
            """,UUID.class,org,user,inviter);
        jdbc.update("delete from membership_roles where organization_id=? and membership_id=?",org,membership);
        jdbc.update("insert into membership_roles select ?,?,id from roles where code=?",org,membership,role.name());
        jdbc.update("insert into team_memberships values(?,?,?) on conflict do nothing",org,source.teamId(),membership);
        jdbc.update("insert into member_repository_assignments values(?,?,?,?) on conflict do nothing",org,membership,source.teamId(),source.repositoryId());
        jdbc.update("update membership_invitations set status='ACCEPTED',approved_at=clock_timestamp(),approved_by_user_id=? where organization_id=? and id=?",approver,org,invitation);
    }
}
