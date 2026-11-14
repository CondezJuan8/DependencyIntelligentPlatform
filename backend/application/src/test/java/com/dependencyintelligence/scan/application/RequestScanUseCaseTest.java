package com.dependencyintelligence.scan.application;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.usecase.RequestScanUseCase;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RequestScanUseCaseTest {
    @Test void missingOrForeignRepositoryCannotQueueScan() {
        var saved = new AtomicBoolean(false);
        RepositoryStore missing = new RepositoryStore() {
            public Repository add(Repository repository) { throw new UnsupportedOperationException(); }
            public Optional<Repository> find(OrganizationId organization, RepositoryId repository) { return Optional.empty(); }
        };
        var useCase = new RequestScanUseCase(missing, scan -> { saved.set(true); return scan; }, Clock.systemUTC());
        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(
            new RequestScanCommand(UUID.randomUUID(), UUID.randomUUID(), "main")));
        assertFalse(saved.get());
    }
}
