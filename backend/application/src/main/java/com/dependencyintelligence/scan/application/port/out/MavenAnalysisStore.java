package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MavenAnalysisStore {
    MavenInventory save(String scannerName, UUID scanId, UUID organizationId, UUID repositoryId, String provider,
                        String sourceSha256, String commitSha, Instant analyzedAt, ManifestAnalysis analysis);
    Optional<MavenInventory> latest(String scannerName, UUID organizationId, UUID repositoryId);
}
