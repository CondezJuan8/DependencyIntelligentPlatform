package com.dependencyintelligence.identity.application.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class PlatformModels {
    private PlatformModels() { }
    public enum Resource { USER, REPOSITORY, TEAM, ORGANIZATION, MEMBERSHIP }
    public enum Operation { APPROVE, REJECT, ENABLE, DISABLE, REMOVE }
    public record Action(Resource resource, UUID id, Operation operation, String reason) { }
    public record Statistics(long owners,long pendingOwners,long members,long repositories,long enabledRepositories,long teams,long organizations,long pendingInvitations) { }
    public record User(UUID id,String email,String displayName,String accountType,String admissionStatus,boolean emailVerified,boolean active,boolean administrator) { }
    public record Item(UUID id,UUID organizationId,String name,String organizationName,boolean enabled) { }
    public record Membership(UUID id,UUID organizationId,String email,String role,String status) { }
    public record Invitation(UUID id,UUID organizationId,String email,String status,String owner,String team,String repository) { }
    public record Audit(UUID id,UUID actorId,String action,String resourceType,UUID resourceId,String reason,Instant createdAt) { }
    public record Overview(Statistics statistics,List<User> users,List<Item> repositories,List<Item> teams,List<Item> organizations,
            List<Membership> memberships,List<Invitation> invitations,List<Audit> audit) { }
}
