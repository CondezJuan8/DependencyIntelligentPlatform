package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MavenAnalysisStore {
    MavenInventory save(String scannerName, UUID scanId, UUID organizationId, UUID repositoryId, String provider,
                        String sourceSha256, String commitSha, Instant analyzedAt, ManifestAnalysis analysis);
    default MavenInventory saveForJob(UUID jobId, String scannerName, UUID scanId, UUID organizationId,
            UUID repositoryId, String provider, String sourceSha256, String commitSha, Instant analyzedAt,
            ManifestAnalysis analysis) {
        return save(scannerName, scanId, organizationId, repositoryId, provider, sourceSha256, commitSha,
            analyzedAt, analysis);
    }
    void saveFailure(String scannerName, UUID organizationId, UUID repositoryId, String provider,
                     String sourceSha256, String commitSha, Instant failedAt, String reason);
    default void saveFailureForJob(UUID jobId, String scannerName, UUID organizationId, UUID repositoryId,
            String provider, String sourceSha256, String commitSha, Instant failedAt, String reason) {
        saveFailure(scannerName, organizationId, repositoryId, provider, sourceSha256, commitSha, failedAt, reason);
    }
    Optional<MavenInventory> latest(String scannerName, UUID organizationId, UUID repositoryId);
    default Optional<MavenInventory> findById(String scannerName, UUID organizationId, UUID repositoryId,
            UUID scanId) { return latest(scannerName, organizationId, repositoryId); }
    default Optional<MavenInventory> findForJob(UUID jobId, String scannerName, UUID organizationId,
            UUID repositoryId) { return Optional.empty(); }
}
