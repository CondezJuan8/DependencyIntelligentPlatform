package com.dependencyintelligence.identity.application.dto;

public record RegisterAccountCommand(String email, String displayName, String password) {
    @Override public String toString() { return "RegisterAccountCommand[REDACTED]"; }
}
