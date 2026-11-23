package com.dependencyintelligence.scan.application.port.in;

import com.dependencyintelligence.scan.application.dto.ScanHistoryItem;
import java.util.List;
import java.util.UUID;

public interface FindScanHistory {
    List<ScanHistoryItem> execute(UUID organizationId, UUID repositoryId);
}
