package com.dependencyintelligence.repository.application.port.out;

import com.dependencyintelligence.repository.application.dto.VulnerableScanComponent;
import java.util.Optional;
import java.util.UUID;

/** Confirms that OSV reported this advisory for this exact component in this exact scan. */
public interface VulnerableScanComponentReader {
    Optional<VulnerableScanComponent> find(UUID organizationId, UUID repositoryId, UUID scanId,
                                          UUID scanComponentId, String advisoryId);
}
