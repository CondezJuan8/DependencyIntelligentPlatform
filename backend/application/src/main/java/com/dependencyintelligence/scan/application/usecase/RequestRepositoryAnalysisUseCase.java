package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import com.dependencyintelligence.scan.application.port.in.RequestRepositoryAnalysis;
import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class RequestRepositoryAnalysisUseCase implements RequestRepositoryAnalysis {
    private final RepositoryStore repositories;
    private final AnalysisJobStore jobs;
    private final Clock clock;

    public RequestRepositoryAnalysisUseCase(RepositoryStore repositories, AnalysisJobStore jobs, Clock clock) {
        this.repositories = repositories;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override public AnalysisJobTicket execute(UUID organizationId, UUID repositoryId, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException("La clave de idempotencia debe tener entre 16 y 128 caracteres seguros.");
        }
        repositories.find(new OrganizationId(organizationId), new RepositoryId(repositoryId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository"));
        return jobs.enqueue(organizationId, repositoryId, idempotencyKey,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
