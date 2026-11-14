package com.dependencyintelligence.repository.application;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.application.usecase.FindRepositoryUseCase;
import com.dependencyintelligence.repository.application.usecase.RegisterRepositoryUseCase;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryUseCasesTest {
    private final UUID organizationId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
    private final MemoryRepositories repositories = new MemoryRepositories();

    @Test void refusesRegistrationForUnknownOrganization() {
        OrganizationLookup missing = id -> false;
        var useCase = new RegisterRepositoryUseCase(missing, repositories, clock);
        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(command()));
        assertNull(repositories.saved);
    }

    @Test void normalizesProviderAndUsesInjectedTime() {
        var useCase = new RegisterRepositoryUseCase(id -> true, repositories, clock);
        var result = useCase.execute(command());
        assertEquals("github", result.provider());
        assertEquals(clock.instant(), result.createdAt());
        assertEquals(organizationId, result.organizationId());
    }

    @Test void cannotReadRepositoryFromAnotherOrganization() {
        var result = new RegisterRepositoryUseCase(id -> true, repositories, clock).execute(command());
        var find = new FindRepositoryUseCase(repositories);
        assertEquals(result.id(), find.execute(organizationId, result.id()).id());
        assertThrows(ResourceNotFoundException.class, () -> find.execute(UUID.randomUUID(), result.id()));
    }

    @Test void rejectsInvalidRepositoryBeforePersisting() {
        var useCase = new RegisterRepositoryUseCase(id -> true, repositories, clock);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
            new RegisterRepositoryCommand(organizationId, "github", "42", " ", "main")));
        assertNull(repositories.saved);
    }

    private RegisterRepositoryCommand command() {
        return new RegisterRepositoryCommand(organizationId, " GitHub ", "42", "acme/backend", "main");
    }

    private static class MemoryRepositories implements RepositoryStore {
        Repository saved;
        public Repository add(Repository repository) { saved = repository; return repository; }
        public Optional<Repository> find(OrganizationId organization, RepositoryId id) {
            return Optional.ofNullable(saved).filter(r -> r.organizationId().equals(organization) && r.id().equals(id));
        }
    }
}
