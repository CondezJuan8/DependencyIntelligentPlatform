package com.dependencyintelligence.organization.application.port.in;

import com.dependencyintelligence.organization.application.dto.AccessModels.Invitation;
import java.util.UUID;

public interface ManageInvitations {
    Invitation invite(UUID actor, UUID organization, String email, String role);
    void revoke(UUID actor, UUID organization, UUID invitation);
    void accept(UUID actor, UUID organization, UUID invitation, String token);
}
