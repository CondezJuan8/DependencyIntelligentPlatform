package com.dependencyintelligence.scan.application.port.in;

import java.util.UUID;

public interface CancelAnalysisJob {
    boolean execute(UUID organizationId, UUID repositoryId, UUID jobId);
}
