package com.dependencyintelligence.sbom.application;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.sbom.application.dto.ImportSbomCommand;
import com.dependencyintelligence.sbom.application.exception.InvalidSbomException;
import com.dependencyintelligence.sbom.application.usecase.ImportSbomUseCase;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ImportSbomUseCaseTest {
    private final UUID organization = UUID.randomUUID();
    private final UUID repository = UUID.randomUUID();
    private final AtomicBoolean parsed = new AtomicBoolean();
    private final AtomicBoolean written = new AtomicBoolean();

    private RepositoryStore repositories(boolean exists) {
        return new RepositoryStore() {
            public Repository add(Repository value) { throw new UnsupportedOperationException(); }
            public Optional<Repository> find(OrganizationId organizationId, RepositoryId repositoryId) {
                return exists ? Optional.of(new Repository(repositoryId, organizationId,
                    "demo", "demo", "example/demo", "main", Clock.systemUTC().instant())) : Optional.empty();
            }
        };
    }

    @Test void missingOrForeignRepositoryIsRejectedBeforeReadingContent() {
        var useCase = new ImportSbomUseCase(repositories(false), input -> {
            parsed.set(true); throw new AssertionError("Must not parse");
        }, (scan, sbom, hash) -> written.set(true), Clock.systemUTC());
        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(
            new ImportSbomCommand(organization, repository, new ByteArrayInputStream(new byte[0]))));
        assertFalse(parsed.get());
        assertFalse(written.get());
    }

    @Test void invalidDocumentNeverReachesPersistence() {
        var useCase = new ImportSbomUseCase(repositories(true), input -> {
            throw new InvalidSbomException(InvalidSbomException.Reason.INVALID_JSON, "Invalid input");
        }, (scan, sbom, hash) -> written.set(true), Clock.systemUTC());
        assertThrows(InvalidSbomException.class, () -> useCase.execute(
            new ImportSbomCommand(organization, repository, new ByteArrayInputStream(new byte[0]))));
        assertFalse(written.get());
    }
}
