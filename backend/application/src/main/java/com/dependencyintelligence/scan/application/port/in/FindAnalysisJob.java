package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import java.util.Optional;
import java.util.UUID;

public interface FindAnalysisJob {
    Optional<AnalysisJobStatus> execute(UUID organizationId, UUID repositoryId, UUID jobId);
    Optional<AnalysisJobStatus> active(UUID organizationId, UUID repositoryId);
}
