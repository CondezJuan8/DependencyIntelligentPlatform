package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.ScanComparison;
import com.dependencyintelligence.scan.application.port.in.CompareScans;
import com.dependencyintelligence.scan.application.port.out.ScanComparisonReader;
import java.util.UUID;

public final class CompareScansUseCase implements CompareScans {
    private final ScanComparisonReader reader;
    public CompareScansUseCase(ScanComparisonReader reader) { this.reader = reader; }
    @Override public ScanComparison execute(UUID organization, UUID repository, UUID baseScan, UUID targetScan) {
        if (organization == null || repository == null || baseScan == null || targetScan == null || baseScan.equals(targetScan))
            throw new IllegalArgumentException("Selecciona dos análisis distintos del mismo repositorio.");
        return reader.compare(organization, repository, baseScan, targetScan);
    }
}
