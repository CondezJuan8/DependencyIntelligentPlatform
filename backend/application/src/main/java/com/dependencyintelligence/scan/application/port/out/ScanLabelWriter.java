package com.dependencyintelligence.scan.application.port.out;

import java.util.UUID;

public interface ScanLabelWriter {
    boolean update(UUID organizationId, UUID repositoryId, UUID scanId, String displayName);
}
