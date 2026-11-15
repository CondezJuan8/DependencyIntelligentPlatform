package com.dependencyintelligence.identity.application.dto;

import java.util.UUID;

public record RecoveryRecipient(UUID id, String email, boolean emailVerified, boolean localCredentials) { }
