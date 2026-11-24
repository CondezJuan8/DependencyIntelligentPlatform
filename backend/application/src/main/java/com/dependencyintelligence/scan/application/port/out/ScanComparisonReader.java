package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.ScanComparison;
import java.util.UUID;

public interface ScanComparisonReader {
    ScanComparison compare(UUID organizationId, UUID repositoryId, UUID baseScanId, UUID targetScanId);
}
