package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import java.util.UUID;

public interface RequestRepositoryAnalysis {
    AnalysisJobTicket execute(UUID organizationId, UUID repositoryId, String idempotencyKey);
}
