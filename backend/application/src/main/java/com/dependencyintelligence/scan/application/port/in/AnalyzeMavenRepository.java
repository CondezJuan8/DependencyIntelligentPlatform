package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import java.util.Optional;
import java.util.UUID;

public interface AnalyzeMavenRepository {
    MavenInventory run(UUID organizationId, UUID repositoryId);
    Optional<MavenInventory> latest(UUID organizationId, UUID repositoryId);
    default Optional<MavenInventory> find(UUID organizationId, UUID repositoryId, UUID scanId) {
        return latest(organizationId, repositoryId);
    }
}
