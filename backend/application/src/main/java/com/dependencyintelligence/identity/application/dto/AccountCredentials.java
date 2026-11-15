package com.dependencyintelligence.identity.application.dto;

public record AccountCredentials(AccountResult account, String passwordHash, boolean active) {
    @Override public String toString() { return "AccountCredentials[REDACTED]"; }
}
