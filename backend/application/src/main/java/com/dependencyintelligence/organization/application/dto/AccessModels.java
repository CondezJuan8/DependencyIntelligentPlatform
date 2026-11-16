package com.dependencyintelligence.organization.application.dto;

import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AccessModels {
    private AccessModels() { }
    public record Actor(UUID membershipId, Set<OrganizationRole> roles) {
        public Actor { roles = Set.copyOf(roles); }
        public boolean owner() { return roles.contains(OrganizationRole.OWNER); }
        public boolean manager() { return roles.stream().anyMatch(OrganizationRole::managesAccess); }
    }
    public record Member(UUID id, UUID userId, String email, String displayName, String status, List<OrganizationRole> roles) { }
    public record Grant(UUID repositoryId, String permission) { }
    public record Team(UUID id, String name, List<UUID> membershipIds, List<Grant> grants, boolean enabled) { }
    public record Repository(UUID id, String fullName) { }
    public record Permission(String code, String description) { }
    public record RoleDefinition(OrganizationRole code, List<String> permissions) { }
    public record Invitation(UUID id, String email, String status, Instant expiresAt, List<OrganizationRole> roles, UUID teamId, UUID repositoryId) { }
    public record Overview(UUID ownMembershipId, boolean owner, List<Member> members, List<Team> teams,
            List<Repository> repositories, List<Permission> permissions, List<RoleDefinition> roles, List<Invitation> invitations) { }
    public record Invitee(String email, boolean verified) { }
    public record PendingInvitation(UUID id, String email, String hash, UUID inviterMembershipId,
            String status, Instant expiresAt, List<OrganizationRole> roles, UUID teamId, UUID repositoryId, UUID acceptedByUserId) {
        @Override public String toString() { return "PendingInvitation[REDACTED]"; }
    }
}
