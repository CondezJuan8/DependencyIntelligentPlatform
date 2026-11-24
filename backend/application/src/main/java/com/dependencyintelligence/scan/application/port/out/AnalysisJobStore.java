package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobStore {
    AnalysisJobTicket enqueue(UUID organizationId, UUID repositoryId, String idempotencyKey, Instant requestedAt);
    Optional<AnalysisJobStatus> find(UUID organizationId, UUID repositoryId, UUID jobId);
    Optional<AnalysisJobStatus> findActive(UUID organizationId, UUID repositoryId);
    boolean cancel(UUID organizationId, UUID repositoryId, UUID jobId);
    List<ClaimedJob> claim(String worker);
    boolean pinSource(UUID organizationId, UUID jobId, String worker, String provider, String sha256, String commitSha);
    boolean isCancelled(UUID organizationId, UUID jobId);
    void recordStage(UUID organizationId, UUID jobId, String stage);
    void complete(UUID organizationId, UUID jobId, String worker, int attemptNumber);
    void retryOrFail(UUID organizationId, UUID jobId, String worker, int attemptNumber, String errorCode);

    record ClaimedJob(UUID organizationId, UUID jobId, UUID repositoryId, int attemptNumber, String worker) { }
}
