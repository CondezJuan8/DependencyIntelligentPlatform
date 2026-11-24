package com.dependencyintelligence.scan.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisJobStatus(UUID jobId, String status, Instant requestedAt, Instant startedAt,
        Instant finishedAt, String errorCode, List<String> completedStages, List<ScanResult> scans) {
    public AnalysisJobStatus {
        completedStages = List.copyOf(completedStages);
        scans = List.copyOf(scans);
    }

    public record ScanResult(String ecosystem, UUID scanId, String status, int manifestCount,
            int dependencyCount, int unresolvedCount, String coverageStatus, String coverageMessage,
            String failureReason) { }
}
