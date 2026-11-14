package com.dependencyintelligence.repository.application.dto;

import com.dependencyintelligence.repository.domain.Repository;
import java.time.Instant;
import java.util.UUID;

public record RepositoryResult(UUID id, UUID organizationId, String provider, String externalId,
                               String fullName, String defaultBranch, Instant createdAt) {
    public static RepositoryResult from(Repository repository) {
        return new RepositoryResult(repository.id().value(), repository.organizationId().value(),
            repository.provider(), repository.externalId(), repository.fullName(),
            repository.defaultBranch(), repository.createdAt());
    }
}
