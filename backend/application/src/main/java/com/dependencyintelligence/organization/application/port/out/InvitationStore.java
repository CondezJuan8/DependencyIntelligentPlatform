package com.dependencyintelligence.organization.application.port.out;

import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.time.Instant;
import java.util.UUID;

public interface InvitationStore {
    Invitee account(UUID userId);
    boolean activeMember(UUID organizationId, String email);
    Invitation create(UUID organizationId, String email, String hash, UUID inviterMembershipId, OrganizationRole role, Instant now, Instant expiresAt);
    PendingInvitation find(UUID organizationId, UUID invitationId);
    void revoke(UUID organizationId, UUID invitationId);
    void accept(UUID organizationId, UUID invitationId, UUID userId, UUID inviterUserId, OrganizationRole role);
    UUID inviterUserId(UUID organizationId, UUID membershipId);
}
