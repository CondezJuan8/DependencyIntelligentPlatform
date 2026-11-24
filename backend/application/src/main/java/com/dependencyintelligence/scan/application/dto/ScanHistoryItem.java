package com.dependencyintelligence.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ScanHistoryItem(UUID id, UUID repositoryId, String repositoryName, String gitReference,
        String commitSha, String status, Instant requestedAt, Instant startedAt, Instant finishedAt,
        String failureReason, String origin, String scannerName, String scannerVersion, String sourceSha256,
        int manifestCount, int dependencyCount, int unresolvedCount, String coverageStatus,
        String coverageMessage, String displayName, long sequenceNumber) { }
