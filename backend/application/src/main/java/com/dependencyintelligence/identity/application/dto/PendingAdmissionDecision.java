package com.dependencyintelligence.identity.application.dto;

import java.util.UUID;

public record PendingAdmissionDecision(UUID administratorId, UUID targetUserId, String decision) { }
