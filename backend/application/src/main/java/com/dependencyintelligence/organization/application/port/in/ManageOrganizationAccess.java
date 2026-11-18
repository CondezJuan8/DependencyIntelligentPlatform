package com.dependencyintelligence.organization.application.port.in;

import com.dependencyintelligence.organization.application.dto.AccessModels.Overview;
import java.util.List;
import java.util.UUID;

public interface ManageOrganizationAccess {
    Overview overview(UUID actor, UUID organization);
    void changeMember(UUID actor, UUID organization, UUID membership, String role, String status);
    UUID createTeam(UUID actor, UUID organization, String name);
    void renameTeam(UUID actor, UUID organization, UUID team, String name);
    void deleteTeam(UUID actor, UUID organization, UUID team);
    void setTeamMember(UUID actor, UUID organization, UUID team, UUID membership, boolean included);
    void setRepositoryPermissions(UUID actor, UUID organization, UUID team, UUID repository, List<String> permissions);
    void setMemberRepositoryPermissions(UUID actor, UUID organization, UUID membership, UUID repository, List<String> permissions);
}
