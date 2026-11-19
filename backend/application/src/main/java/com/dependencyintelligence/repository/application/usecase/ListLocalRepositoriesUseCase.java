package com.dependencyintelligence.repository.application.usecase;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.dto.LocalRepositorySummary;
import com.dependencyintelligence.repository.application.port.in.ListLocalRepositories;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.util.List;
import java.util.UUID;

public final class ListLocalRepositoriesUseCase implements ListLocalRepositories {
    private final OrganizationLookup organizations;
    private final LocalRepositorySourceStore sources;
    public ListLocalRepositoriesUseCase(OrganizationLookup organizations, LocalRepositorySourceStore sources) {
        this.organizations = organizations; this.sources = sources;
    }
    @Override public List<LocalRepositorySummary> execute(UUID organizationId) {
        if (!organizations.exists(new OrganizationId(organizationId))) throw new ResourceNotFoundException("Organization");
        return sources.list(organizationId);
    }
}
