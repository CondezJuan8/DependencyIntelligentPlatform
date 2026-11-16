package com.dependencyintelligence.identity.application.dto;
import java.util.UUID;
public record InvitedRegistrationCommand(UUID organizationId, UUID invitationId, String token, String displayName, String password) {
    @Override public String toString() { return "InvitedRegistrationCommand[REDACTED]"; }
}
