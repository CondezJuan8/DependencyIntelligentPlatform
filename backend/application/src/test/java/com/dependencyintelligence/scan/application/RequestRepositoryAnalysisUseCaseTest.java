package com.dependencyintelligence.scan.application;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import com.dependencyintelligence.scan.application.usecase.RequestRepositoryAnalysisUseCase;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestRepositoryAnalysisUseCaseTest {
    @Test void rejectsInvalidIdempotencyKeyBeforeReadingRepository() {
        AtomicInteger reads = new AtomicInteger();
        RepositoryStore repositories = repositories(reads, Optional.empty());
        AnalysisJobStore jobs = jobs();
        var useCase = new RequestRepositoryAnalysisUseCase(repositories, jobs, Clock.systemUTC());

        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), "short"));
        assertEquals(0, reads.get());
    }

    @Test void missingRepositoryCannotQueueAnalysis() {
        AtomicInteger reads = new AtomicInteger();
        RepositoryStore repositories = repositories(reads, Optional.empty());
        AtomicInteger enqueues = new AtomicInteger();
        AnalysisJobStore jobs = new StubJobs() {
            @Override public AnalysisJobTicket enqueue(UUID org, UUID repo, String key, Instant at) {
                enqueues.incrementAndGet();
                return new AnalysisJobTicket(UUID.randomUUID(), "PENDING");
            }
        };
        var useCase = new RequestRepositoryAnalysisUseCase(repositories, jobs, Clock.systemUTC());

        assertThrows(ResourceNotFoundException.class,
            () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString()));
        assertEquals(1, reads.get());
        assertEquals(0, enqueues.get());
    }

    @Test void queuesManualJobWithStableRequestKeyAndTimestamp() {
        UUID organization = UUID.randomUUID(), repositoryId = UUID.randomUUID(), jobId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-24T12:00:00Z");
        Repository repository = new Repository(new RepositoryId(repositoryId), new OrganizationId(organization),
            "local", repositoryId.toString(), "team/repo", "main", now);
        RepositoryStore repositories = new RepositoryStore() {
            @Override public Repository add(Repository value) { throw new UnsupportedOperationException(); }
            @Override public Optional<Repository> find(OrganizationId org, RepositoryId id) {
                return org.value().equals(organization) && id.value().equals(repositoryId)
                    ? Optional.of(repository) : Optional.empty();
            }
        };
        String key = UUID.randomUUID().toString();
        AtomicInteger enqueues = new AtomicInteger();
        AnalysisJobStore jobs = new StubJobs() {
            @Override public AnalysisJobTicket enqueue(UUID org, UUID repo, String receivedKey, Instant requestedAt) {
                enqueues.incrementAndGet();
                assertEquals(organization, org);
                assertEquals(repositoryId, repo);
                assertEquals(key, receivedKey);
                assertEquals(now, requestedAt);
                return new AnalysisJobTicket(jobId, "PENDING");
            }
        };
        var useCase = new RequestRepositoryAnalysisUseCase(repositories, jobs,
            Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(new AnalysisJobTicket(jobId, "PENDING"), useCase.execute(organization, repositoryId, key));
        assertEquals(1, enqueues.get());
    }

    private static RepositoryStore repositories(AtomicInteger reads, Optional<Repository> result) {
        return new RepositoryStore() {
            @Override public Repository add(Repository repository) { throw new UnsupportedOperationException(); }
            @Override public Optional<Repository> find(OrganizationId organizationId, RepositoryId repositoryId) {
                reads.incrementAndGet();
                return result;
            }
        };
    }

    private static AnalysisJobStore jobs() { return new StubJobs(); }

    private static class StubJobs implements AnalysisJobStore {
        @Override public AnalysisJobTicket enqueue(UUID org, UUID repo, String key, Instant at) {
            return new AnalysisJobTicket(UUID.randomUUID(), "PENDING");
        }
        @Override public Optional<AnalysisJobStatus> find(UUID org, UUID repo, UUID job) { return Optional.empty(); }
        @Override public Optional<AnalysisJobStatus> findActive(UUID org, UUID repo) { return Optional.empty(); }
        @Override public boolean cancel(UUID org, UUID repo, UUID job) { return false; }
        @Override public List<ClaimedJob> claim(String worker) { return List.of(); }
        @Override public boolean pinSource(UUID org, UUID job, String worker, String provider, String sha, String commit) { return false; }
        @Override public boolean isCancelled(UUID org, UUID job) { return false; }
        @Override public void recordStage(UUID org, UUID job, String stage) { }
        @Override public void complete(UUID org, UUID job, String worker, int attempt) { }
        @Override public void retryOrFail(UUID org, UUID job, String worker, int attempt, String code) { }
    }
}
