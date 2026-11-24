package com.dependencyintelligence.scan.infrastructure.job;

import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import com.dependencyintelligence.scan.application.usecase.AnalyzeRepositoryUseCase;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class AnalysisJobWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisJobWorker.class);
    private final AnalysisJobStore jobs;
    private final AnalyzeRepositoryUseCase analysis;
    private final String workerId = "analysis-" + UUID.randomUUID();

    public AnalysisJobWorker(AnalysisJobStore jobs, AnalyzeRepositoryUseCase analysis) {
        this.jobs = jobs;
        this.analysis = analysis;
    }

    @Scheduled(fixedDelayString = "${app.analysis-worker.poll-delay-ms:1000}")
    public void poll() {
        final Optional<AnalysisJobStore.ClaimedJob> next;
        try {
            next = jobs.claim(workerId).stream().findFirst();
        } catch (RuntimeException unavailable) {
            LOG.warn("Analysis job claim failed with {}", unavailable.getClass().getSimpleName());
            return;
        }
        if (next.isEmpty()) return;
        AnalysisJobStore.ClaimedJob job = next.get();
        try {
            analysis.run(job.organizationId(), job.repositoryId(), job.jobId(), source -> {
                boolean pinned = jobs.pinSource(job.organizationId(), job.jobId(), workerId,
                    source.provider(), source.sha256(), source.commitSha());
                if (!pinned) throw new IllegalStateException("La fuente cambió desde otro intento del mismo análisis.");
                jobs.recordStage(job.organizationId(), job.jobId(), "SOURCE_PREPARED");
            }, stage -> jobs.recordStage(job.organizationId(), job.jobId(), stage),
                () -> jobs.isCancelled(job.organizationId(), job.jobId()));
            if (!jobs.isCancelled(job.organizationId(), job.jobId())) {
                jobs.complete(job.organizationId(), job.jobId(), workerId, job.attemptNumber());
            }
        } catch (CancellationException cancelled) {
            LOG.info("Analysis job {} was cancelled", job.jobId());
        } catch (RuntimeException failure) {
            LOG.warn("Analysis job {} attempt {} failed with {}", job.jobId(), job.attemptNumber(),
                failure.getClass().getSimpleName());
            jobs.retryOrFail(job.organizationId(), job.jobId(), workerId, job.attemptNumber(), "ANALYSIS_FAILED");
        }
    }
}
