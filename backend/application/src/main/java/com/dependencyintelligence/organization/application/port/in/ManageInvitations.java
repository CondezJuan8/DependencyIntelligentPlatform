package com.dependencyintelligence.organization.application.port.in;

import com.dependencyintelligence.organization.application.dto.AccessModels.Invitation;
import java.util.UUID;

public interface ManageInvitations {
    Invitation invite(UUID actor, UUID organization, String email, String role, UUID teamId, UUID repositoryId);
    void revoke(UUID actor, UUID organization, UUID invitation);
    void accept(UUID actor, UUID organization, UUID invitation, String token);
    void approve(UUID actor, UUID organization, UUID invitation);
    com.dependencyintelligence.identity.application.dto.AccountResult registerInvited(com.dependencyintelligence.identity.application.dto.InvitedRegistrationCommand command);
}
