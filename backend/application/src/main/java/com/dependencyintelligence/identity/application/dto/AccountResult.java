package com.dependencyintelligence.identity.application.dto;

import java.util.UUID;

public record AccountResult(UUID id, String email, String displayName, boolean emailVerified,
        String accountType, String admissionStatus, boolean platformAdmin) {
    public AccountResult(UUID id, String email, String displayName, boolean emailVerified) {
        this(id,email,displayName,emailVerified,"OWNER","APPROVED",false);
    }
}
