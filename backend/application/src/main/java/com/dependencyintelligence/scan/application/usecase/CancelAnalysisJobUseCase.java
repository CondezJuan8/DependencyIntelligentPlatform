package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.port.in.CancelAnalysisJob;
import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import java.util.UUID;

public final class CancelAnalysisJobUseCase implements CancelAnalysisJob {
    private final AnalysisJobStore jobs;
    public CancelAnalysisJobUseCase(AnalysisJobStore jobs) { this.jobs = jobs; }
    @Override public boolean execute(UUID organizationId, UUID repositoryId, UUID jobId) {
        return jobs.cancel(organizationId, repositoryId, jobId);
    }
}
