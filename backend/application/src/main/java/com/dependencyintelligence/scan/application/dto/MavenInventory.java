package com.dependencyintelligence.scan.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MavenInventory(UUID scanId, String source, String sourceSha256, String commitSha,
                             Instant analyzedAt, int manifestCount, int unresolvedCount,
                             String coverageStatus, String coverageMessage, List<ManifestDependency> dependencies) {
    public MavenInventory { dependencies = List.copyOf(dependencies); }
}
