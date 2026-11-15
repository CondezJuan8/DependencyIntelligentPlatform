package com.dependencyintelligence.identity.application.port.out;

import java.util.UUID;

public interface RepositoryPermissionStore {
    boolean permits(UUID userId, UUID organizationId, UUID repositoryId, String permission);
}
