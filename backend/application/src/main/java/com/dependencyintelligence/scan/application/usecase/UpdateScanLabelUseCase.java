package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.port.in.UpdateScanLabel;
import com.dependencyintelligence.scan.application.port.out.ScanLabelWriter;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import java.util.UUID;

public final class UpdateScanLabelUseCase implements UpdateScanLabel {
    private final ScanLabelWriter writer;

    public UpdateScanLabelUseCase(ScanLabelWriter writer) { this.writer = writer; }

    @Override public void execute(UUID organizationId, UUID repositoryId, UUID scanId, String displayName) {
        String normalized = displayName == null ? null : displayName.strip();
        if (normalized != null && normalized.isEmpty()) normalized = null;
        if (normalized != null && normalized.length() > 100)
            throw new IllegalArgumentException("El nombre del análisis no puede superar 100 caracteres.");
        if (!writer.update(organizationId, repositoryId, scanId, normalized))
            throw new ResourceNotFoundException("Completed scan");
    }
}
