package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.ScanComparison;
import java.util.UUID;

public interface CompareScans {
    ScanComparison execute(UUID organizationId, UUID repositoryId, UUID baseScanId, UUID targetScanId);
}
