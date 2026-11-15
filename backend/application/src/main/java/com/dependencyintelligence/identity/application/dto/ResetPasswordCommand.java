package com.dependencyintelligence.identity.application.dto;

public record ResetPasswordCommand(String token, String password) {
    @Override public String toString() { return "ResetPasswordCommand[REDACTED]"; }
}
