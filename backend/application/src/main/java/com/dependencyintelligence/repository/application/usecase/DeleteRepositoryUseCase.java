package com.dependencyintelligence.repository.application.usecase;

import com.dependencyintelligence.repository.application.port.in.DeleteRepository;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.util.UUID;

public final class DeleteRepositoryUseCase implements DeleteRepository {
    private final RepositoryStore repositories;
    private final LocalRepositorySourceStore localSources;

    public DeleteRepositoryUseCase(RepositoryStore repositories, LocalRepositorySourceStore localSources) {
        this.repositories = repositories; this.localSources = localSources;
    }

    @Override public void execute(UUID organizationId, UUID repositoryId) {
        var organization = new OrganizationId(organizationId);
        var id = new RepositoryId(repositoryId);
        var repository = repositories.find(organization, id).orElseThrow(() -> new ResourceNotFoundException("Repository"));
        if (!repositories.disable(organization, id)) throw new ResourceNotFoundException("Repository");
        if ("local".equals(repository.provider())) localSources.remove(organizationId, repositoryId);
    }
}
