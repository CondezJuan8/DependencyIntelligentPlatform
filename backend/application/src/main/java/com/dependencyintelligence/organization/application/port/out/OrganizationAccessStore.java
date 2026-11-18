package com.dependencyintelligence.organization.application.port.out;

import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.util.List;
import java.util.UUID;

/** All operations require the same organization transaction established by Infrastructure. */
public interface OrganizationAccessStore {
    String lockOrganization(UUID organizationId);
    Actor actor(UUID organizationId, UUID userId);
    Actor actorByMembership(UUID organizationId, UUID membershipId);
    Member member(UUID organizationId, UUID membershipId);
    long activeOwners(UUID organizationId);
    boolean eligibleResponsible(UUID userId);
    Overview overview(UUID organizationId, Actor actor);
    void changeMember(UUID organizationId, UUID membershipId, OrganizationRole role, String status);
    UUID createTeam(UUID organizationId, String name);
    void renameTeam(UUID organizationId, UUID teamId, String name);
    void deleteTeam(UUID organizationId, UUID teamId);
    void setTeamMember(UUID organizationId, UUID teamId, UUID membershipId, boolean included);
    void setRepositoryPermissions(UUID organizationId, UUID teamId, UUID repositoryId, List<String> permissions);
    void setMemberRepositoryPermissions(UUID organizationId, UUID membershipId, UUID repositoryId, List<String> permissions);
    void audit(UUID organizationId, UUID actorId, String action, String resourceType, UUID resourceId);
}
