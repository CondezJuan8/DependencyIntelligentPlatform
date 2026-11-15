package com.dependencyintelligence.identity.application.port.in;

import java.util.UUID;

public interface AuthorizeRepositoryAction {
    void check(UUID userId, UUID organizationId, UUID repositoryId, String permission);
}
