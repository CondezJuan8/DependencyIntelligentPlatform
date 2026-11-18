package com.dependencyintelligence.integration.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GitHubInstallationStateStore {
    record Pending(UUID organizationId, UUID userId, String browserSession, Instant expiresAt,
                   Long installationId, String codeVerifier) { }
    void save(String state, Pending pending);
    Optional<Pending> find(String state);
    boolean consume(String state, Pending pending);
    boolean attachAuthorization(String state, Pending current, Pending authorized);
}
