package com.dependencyintelligence.scan.application.dto;

import com.dependencyintelligence.scan.domain.Scan;
import java.time.Instant;
import java.util.UUID;

public record ScanResult(UUID id, UUID repositoryId, String gitReference, String status, Instant requestedAt) {
    public static ScanResult from(Scan scan) {
        return new ScanResult(scan.id(), scan.repositoryId().value(), scan.gitReference(),
            scan.status().name(), scan.requestedAt());
    }
}
