package com.dependencyintelligence.organization.infrastructure.persistence;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.dependencyintelligence.organization.application.usecase.OrganizationAccessRules.REPOSITORY_PERMISSIONS;

@Repository
public class JdbcOrganizationAccessAdapter implements OrganizationAccessStore {
    private final JdbcTemplate jdbc;
    public JdbcOrganizationAccessAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String lockOrganization(UUID org) {
        // Serializes membership, invitation and team mutations, including concurrent last-owner changes.
        return jdbc.query("select name from organizations where id = ? and status = 'ACTIVE' for update",
            (row, index) -> row.getString(1), org).stream().findFirst().orElseThrow(PermissionDeniedException::new);
    }
    @Override public Actor actor(UUID org, UUID user) { return actorWhere(org, "m.user_id", user); }
    @Override public Actor actorByMembership(UUID org, UUID membership) { return actorWhere(org, "m.id", membership); }
    private Actor actorWhere(UUID org, String column, UUID id) {
        // Column comes only from the two constants above, never from HTTP input.
        var rows = jdbc.query("""
            select m.id, r.code from organization_memberships m
            join users u on u.id = m.user_id and u.is_active and u.email_verified_at is not null and u.admission_status='APPROVED'
            join membership_roles mr on mr.organization_id = m.organization_id and mr.membership_id = m.id
            join roles r on r.id = mr.role_id
            where m.organization_id = ? and m.status = 'ACTIVE' and
            """ + column + " = ?", (row, index) -> Map.entry(row.getObject(1, UUID.class), OrganizationRole.parse(row.getString(2))), org, id);
        if (rows.isEmpty()) throw new PermissionDeniedException();
        return new Actor(rows.getFirst().getKey(), new HashSet<>(rows.stream().map(Map.Entry::getValue).toList()));
    }
    private List<Member> members(UUID org) {
        return jdbc.query("""
            select m.id, m.user_id, u.email, u.display_name, m.status,
              coalesce(array_agg(r.code order by r.code) filter(where r.code is not null), '{}') as role_codes
            from organization_memberships m join users u on u.id = m.user_id
            left join membership_roles mr on mr.organization_id = m.organization_id and mr.membership_id = m.id
            left join roles r on r.id = mr.role_id where m.organization_id = ?
            group by m.id, m.user_id, u.email, u.display_name, m.status order by u.email
            """, (row, index) -> new Member(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3),
                row.getString(4), row.getString(5), Arrays.stream((String[]) row.getArray(6).getArray()).map(OrganizationRole::parse).toList()), org);
    }
    @Override public Member member(UUID org, UUID id) {
        return members(org).stream().filter(member -> member.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada"));
    }
    @Override public long activeOwners(UUID org) {
        return jdbc.queryForObject("""
            select count(distinct m.id) from organization_memberships m join users u on u.id = m.user_id and u.is_active
            join membership_roles mr on mr.organization_id = m.organization_id and mr.membership_id = m.id
            join roles r on r.id = mr.role_id and r.code = 'OWNER' where m.organization_id = ? and m.status = 'ACTIVE'
            """, Long.class, org);
    }
    @Override public boolean eligibleResponsible(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where id=? and is_active and account_type='OWNER' and admission_status='APPROVED' and email_verified_at is not null)",Boolean.class,user));
    }
    @Override public Overview overview(UUID org, Actor actor) {
        var people = new HashMap<UUID, List<UUID>>();
        jdbc.query("select team_id, membership_id from team_memberships where organization_id = ?",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> people.computeIfAbsent(row.getObject(1, UUID.class), key -> new ArrayList<>()).add(row.getObject(2, UUID.class)), org);
        var grants = new HashMap<UUID, List<Grant>>();
        jdbc.query("""
            select a.team_id, a.repository_id, p.code from repository_team_access a
            join permissions p on p.id = a.permission_id where a.organization_id = ? order by a.repository_id, p.code
            """, (org.springframework.jdbc.core.RowCallbackHandler) row -> grants.computeIfAbsent(row.getObject(1, UUID.class), key -> new ArrayList<>()).add(new Grant(row.getObject(2, UUID.class), row.getString(3))), org);
        var teams = jdbc.query("select id, name from organization_teams where organization_id = ? and is_enabled order by name, id",
            (row, index) -> new Team(row.getObject(1, UUID.class), row.getString(2), people.getOrDefault(row.getObject(1, UUID.class), List.of()), grants.getOrDefault(row.getObject(1, UUID.class), List.of()),true), org);
        var repositories = jdbc.query("select id, full_name from repositories where organization_id = ? and is_enabled order by full_name, id",
            (row, index) -> new com.dependencyintelligence.organization.application.dto.AccessModels.Repository(row.getObject(1, UUID.class), row.getString(2)), org);
        var permissions = jdbc.query("select code, description from permissions order by code", (row, index) -> new Permission(row.getString(1), row.getString(2)))
            .stream().filter(permission -> REPOSITORY_PERMISSIONS.contains(permission.code())).toList();
        var definitions = jdbc.query("select code, id from roles order by code", (row, index) -> new RoleDefinition(OrganizationRole.parse(row.getString(1)),
            jdbc.queryForList("select p.code from permissions p join role_permissions rp on rp.permission_id = p.id where rp.role_id = ? order by p.code", String.class, row.getObject(2, UUID.class))));
        var invitations = jdbc.query("""
            select i.id, i.email_normalized, case when i.status in ('PENDING','AWAITING_OWNER') and i.expires_at <= current_timestamp then 'EXPIRED' else i.status end,
              i.expires_at, coalesce(array_agg(r.code order by r.code) filter(where r.code is not null), '{}'),i.target_team_id,i.repository_id
            from membership_invitations i left join invitation_roles ir on ir.organization_id = i.organization_id and ir.invitation_id = i.id
            left join roles r on r.id = ir.role_id where i.organization_id = ? and i.invited_by_membership_id = ?
            group by i.id, i.email_normalized, i.status, i.expires_at order by i.created_at desc, i.id limit 100
            """, (row, index) -> new Invitation(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getTimestamp(4).toInstant(),
                Arrays.stream((String[]) row.getArray(5).getArray()).map(OrganizationRole::parse).toList(),row.getObject(6,UUID.class),row.getObject(7,UUID.class)), org,actor.membershipId());
        return new Overview(actor.membershipId(), actor.owner(), members(org), teams, repositories, permissions, definitions, actor.owner() ? invitations : List.of());
    }
    @Override public void changeMember(UUID org, UUID id, OrganizationRole role, String status) {
        jdbc.update("delete from membership_roles where organization_id = ? and membership_id = ?", org, id);
        jdbc.update("insert into membership_roles select ?, ?, id from roles where code = ?", org, id, role.name());
        jdbc.update("update organization_memberships set status = ? where organization_id = ? and id = ?", status, org, id);
        if (status.equals("LEFT")) {
            jdbc.update("delete from member_repository_assignments where organization_id=? and membership_id=?",org,id);
            jdbc.update("delete from team_memberships where organization_id = ? and membership_id = ?", org, id);
        }
    }
    @Override public UUID createTeam(UUID org, String name) {
        UUID id = UUID.randomUUID();
        try { jdbc.update("insert into organization_teams(id, organization_id, name) values (?, ?, ?)", id, org, name); }
        catch (DuplicateKeyException exception) { throw new AccessConflictException("Ya existe un equipo con ese nombre."); }
        return id;
    }
    private void team(UUID org, UUID team) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organization_teams where organization_id = ? and id = ?)", Boolean.class, org, team))) {
            throw new ResourceNotFoundException("Equipo no encontrado");
        }
    }
    @Override public void renameTeam(UUID org, UUID id, String name) {
        team(org, id);
        try { jdbc.update("update organization_teams set name = ? where organization_id = ? and id = ?", name, org, id); }
        catch (DuplicateKeyException exception) { throw new AccessConflictException("Ya existe un equipo con ese nombre."); }
    }
    @Override public void deleteTeam(UUID org, UUID id) {
        team(org, id);
        jdbc.update("delete from member_repository_assignments where organization_id=? and team_id=?",org,id);
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from membership_invitations where organization_id=? and target_team_id=?)",Boolean.class,org,id))) {
            jdbc.update("update organization_teams set is_enabled=false where organization_id=? and id=?",org,id);
            jdbc.update("delete from repository_team_access where organization_id=? and team_id=?",org,id);
            jdbc.update("delete from team_memberships where organization_id=? and team_id=?",org,id);
            return;
        }
        jdbc.update("delete from repository_team_access where organization_id = ? and team_id = ?", org, id);
        jdbc.update("delete from team_memberships where organization_id = ? and team_id = ?", org, id);
        jdbc.update("delete from organization_teams where organization_id = ? and id = ?", org, id);
    }
    @Override public void setTeamMember(UUID org, UUID team, UUID membership, boolean included) {
        team(org, team);
        if (included) jdbc.update("insert into team_memberships values (?, ?, ?) on conflict do nothing", org, team, membership);
        else jdbc.update("delete from team_memberships where organization_id = ? and team_id = ? and membership_id = ?", org, team, membership);
    }
    @Override public void setRepositoryPermissions(UUID org, UUID team, UUID repository, List<String> permissions) {
        team(org, team);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from repositories where organization_id = ? and id = ? and is_enabled)", Boolean.class, org, repository))) {
            throw new ResourceNotFoundException("Repositorio no encontrado");
        }
        jdbc.update("delete from repository_team_access where organization_id = ? and team_id = ? and repository_id = ?", org, team, repository);
        for (var permission : permissions) jdbc.update("insert into repository_team_access select ?, ?, ?, id from permissions where code = ?", org, repository, team, permission);
    }
    @Override public void audit(UUID org, UUID actor, String action, String type, UUID id) {
        jdbc.update("insert into audit_events(id, organization_id, actor_id, action, resource_type, resource_id, result) values (?, ?, ?, ?, ?, ?, 'SUCCESS')",
            UUID.randomUUID(), org, actor, action, type, id);
    }
}
