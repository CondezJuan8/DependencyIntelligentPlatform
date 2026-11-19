package com.dependencyintelligence.identity.application.dto;

public record BootstrapPlatformAdministratorCommand(
        String email, String displayName, String password, String passwordConfirmation) {
    @Override public String toString() { return "BootstrapPlatformAdministratorCommand[REDACTED]"; }
}
