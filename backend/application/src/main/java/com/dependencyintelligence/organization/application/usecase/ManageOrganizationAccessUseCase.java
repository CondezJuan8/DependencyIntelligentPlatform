package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import com.dependencyintelligence.organization.application.port.in.ManageOrganizationAccess;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.dependencyintelligence.organization.application.usecase.OrganizationAccessRules.*;

public final class ManageOrganizationAccessUseCase implements ManageOrganizationAccess {
    private final OrganizationAccessStore store;
    public ManageOrganizationAccessUseCase(OrganizationAccessStore store) { this.store = store; }

    @Override public Overview overview(UUID user, UUID org) { return store.overview(org, manager(store, org, user)); }

    @Override public void changeMember(UUID user, UUID org, UUID memberId, String roleCode, String status) {
        var actor = manager(store, org, user);
        var role = OrganizationRole.parse(roleCode);
        if (!Set.of("ACTIVE", "SUSPENDED", "LEFT").contains(status)) throw new IllegalArgumentException("Estado de membresía no válido");
        assignable(actor, role);
        var target = store.member(org, memberId);
        if (role.managesAccess() && !store.eligibleResponsible(target.userId())) throw new AccessConflictException("Solo un responsable verificado y aprobado puede recibir un rol de gestión");
        if (!actor.owner() && target.roles().stream().anyMatch(OrganizationRole::managesAccess)) throw new PermissionDeniedException();
        if (target.status().equals("ACTIVE") && target.roles().contains(OrganizationRole.OWNER)
                && (role != OrganizationRole.OWNER || !status.equals("ACTIVE")) && store.activeOwners(org) <= 1) {
            throw new AccessConflictException("La organización debe conservar al menos un propietario activo.");
        }
        store.changeMember(org, memberId, role, status);
        store.audit(org, user, "membership.update", "membership", memberId);
    }
    @Override public UUID createTeam(UUID user, UUID org, String name) {
        manager(store, org, user);
        UUID team = store.createTeam(org, name(name));
        store.audit(org, user, "team.create", "team", team);
        return team;
    }
    @Override public void renameTeam(UUID user, UUID org, UUID team, String name) {
        manager(store, org, user);
        store.renameTeam(org, team, name(name));
        store.audit(org, user, "team.rename", "team", team);
    }
    @Override public void deleteTeam(UUID user, UUID org, UUID team) {
        manager(store, org, user);
        store.deleteTeam(org, team);
        store.audit(org, user, "team.delete", "team", team);
    }
    @Override public void setTeamMember(UUID user, UUID org, UUID team, UUID membership, boolean included) {
        manager(store, org, user);
        var target = store.member(org, membership);
        if (included && !target.status().equals("ACTIVE")) throw new AccessConflictException("Solo se pueden añadir miembros activos al equipo.");
        store.setTeamMember(org, team, membership, included);
        store.audit(org, user, included ? "team.member.add" : "team.member.remove", "team", team);
    }
    @Override public void setRepositoryPermissions(UUID user, UUID org, UUID team, UUID repository, List<String> permissions) {
        manager(store, org, user);
        if (permissions == null || permissions.size() > REPOSITORY_PERMISSIONS.size()
                || !REPOSITORY_PERMISSIONS.containsAll(permissions)) throw new IllegalArgumentException("Permisos de repositorio no válidos");
        store.setRepositoryPermissions(org, team, repository, permissions.stream().distinct().toList());
        store.audit(org, user, "team.repository.permissions", "team", team);
    }
    @Override public void setMemberRepositoryPermissions(UUID user, UUID org, UUID membership, UUID repository, List<String> permissions) {
        manager(store, org, user);
        var target = store.member(org, membership);
        if (!target.status().equals("ACTIVE")) throw new AccessConflictException("Solo miembros activos pueden recibir permisos directos.");
        if (permissions == null || permissions.size() > REPOSITORY_PERMISSIONS.size()
                || !REPOSITORY_PERMISSIONS.containsAll(permissions)) throw new IllegalArgumentException("Permisos de repositorio no vÃ¡lidos");
        store.setMemberRepositoryPermissions(org, membership, repository, permissions.stream().distinct().toList());
        store.audit(org, user, "member.repository.permissions", "membership", membership);
    }
    private String name(String value) {
        if (value == null || value.strip().isBlank() || value.strip().length() > 200) throw new IllegalArgumentException("Nombre de equipo no válido");
        return value.strip();
    }
}
