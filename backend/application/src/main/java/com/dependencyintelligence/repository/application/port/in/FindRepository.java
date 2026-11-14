package com.dependencyintelligence.repository.application.port.in;

import com.dependencyintelligence.repository.application.dto.RepositoryResult;
import java.util.UUID;

public interface FindRepository {
    RepositoryResult execute(UUID organizationId, UUID repositoryId);
}
