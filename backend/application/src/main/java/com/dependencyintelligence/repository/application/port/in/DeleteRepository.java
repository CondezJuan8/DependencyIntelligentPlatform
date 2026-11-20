package com.dependencyintelligence.repository.application.port.in;

import java.util.UUID;

public interface DeleteRepository {
    void execute(UUID organizationId, UUID repositoryId);
}
