package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.ScanHistoryItem;
import java.util.List;
import java.util.UUID;

public interface ScanHistoryReader {
    List<ScanHistoryItem> find(UUID organizationId, UUID repositoryId, int limit);
}
