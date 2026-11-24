package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import com.dependencyintelligence.scan.application.port.in.FindAnalysisJob;
import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import java.util.Optional;
import java.util.UUID;

public final class FindAnalysisJobUseCase implements FindAnalysisJob {
    private final AnalysisJobStore jobs;
    public FindAnalysisJobUseCase(AnalysisJobStore jobs) { this.jobs = jobs; }
    @Override public Optional<AnalysisJobStatus> execute(UUID organizationId, UUID repositoryId, UUID jobId) {
        return jobs.find(organizationId, repositoryId, jobId);
    }
    @Override public Optional<AnalysisJobStatus> active(UUID organizationId, UUID repositoryId) {
        return jobs.findActive(organizationId, repositoryId);
    }
}
