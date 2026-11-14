package com.dependencyintelligence.repository.application.usecase;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.dto.RepositoryResult;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Clock;
import java.util.UUID;

public final class RegisterRepositoryUseCase implements RegisterRepository {
    private final OrganizationLookup organizations;
    private final RepositoryStore repositories;
    private final Clock clock;

    public RegisterRepositoryUseCase(OrganizationLookup organizations, RepositoryStore repositories, Clock clock) {
        this.organizations = organizations;
        this.repositories = repositories;
        this.clock = clock;
    }

    @Override
    public RepositoryResult execute(RegisterRepositoryCommand command) {
        var organizationId = new OrganizationId(command.organizationId());
        if (!organizations.exists(organizationId)) { throw new ResourceNotFoundException("Organization"); }
        var repository = new Repository(new RepositoryId(UUID.randomUUID()), organizationId,
            command.provider(), command.externalId(), command.fullName(), command.defaultBranch(),
            clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return RepositoryResult.from(repositories.add(repository));
    }
}
