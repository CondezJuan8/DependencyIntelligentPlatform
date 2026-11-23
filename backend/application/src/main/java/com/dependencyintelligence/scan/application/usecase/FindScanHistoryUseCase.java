package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.ScanHistoryItem;
import com.dependencyintelligence.scan.application.port.in.FindScanHistory;
import com.dependencyintelligence.scan.application.port.out.ScanHistoryReader;
import java.util.List;
import java.util.UUID;

public final class FindScanHistoryUseCase implements FindScanHistory {
    private final ScanHistoryReader history;

    public FindScanHistoryUseCase(ScanHistoryReader history) { this.history = history; }

    @Override public List<ScanHistoryItem> execute(UUID organizationId, UUID repositoryId) {
        return history.find(organizationId, repositoryId, 50);
    }
}
