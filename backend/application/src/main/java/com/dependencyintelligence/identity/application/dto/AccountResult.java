package com.dependencyintelligence.identity.application.dto;

import java.util.UUID;

public record AccountResult(UUID id, String email, String displayName, boolean emailVerified) { }
