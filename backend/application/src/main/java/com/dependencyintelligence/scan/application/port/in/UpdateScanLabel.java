package com.dependencyintelligence.scan.application.port.in;

import java.util.UUID;

public interface UpdateScanLabel {
    void execute(UUID organizationId, UUID repositoryId, UUID scanId, String displayName);
}
