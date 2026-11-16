package com.dependencyintelligence.organization.application.port.out;

import java.util.UUID;

public interface InvitationDelivery {
    void send(String email, String organizationName, UUID organizationId, UUID invitationId, String token);
}
