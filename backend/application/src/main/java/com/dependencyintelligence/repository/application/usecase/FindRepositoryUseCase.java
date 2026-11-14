package com.dependencyintelligence.repository.application.usecase;

import com.dependencyintelligence.repository.application.dto.RepositoryResult;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.util.UUID;

public final class FindRepositoryUseCase implements FindRepository {
    private final RepositoryStore repositories;

    public FindRepositoryUseCase(RepositoryStore repositories) { this.repositories = repositories; }

    @Override
    public RepositoryResult execute(UUID organizationId, UUID repositoryId) {
        return repositories.find(new OrganizationId(organizationId), new RepositoryId(repositoryId))
            .map(RepositoryResult::from).orElseThrow(() -> new ResourceNotFoundException("Repository"));
    }
}
