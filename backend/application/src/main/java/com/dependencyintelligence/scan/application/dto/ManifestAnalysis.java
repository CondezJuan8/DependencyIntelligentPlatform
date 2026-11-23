package com.dependencyintelligence.scan.application.dto;

import java.util.List;

/** Concrete package occurrences detected in the analyzed source. */
public record ManifestAnalysis(List<ManifestDependency> dependencies,
                               int manifestCount, int unresolvedCount, String coverageStatus, String coverageMessage) {
    public ManifestAnalysis(List<ManifestDependency> dependencies, int manifestCount, int unresolvedCount) {
        this(dependencies, manifestCount, unresolvedCount,
            unresolvedCount > 0 ? "PARTIAL" : "COMPLETE", unresolvedCount > 0 ? "Hay dependencias o versiones que no se pudieron resolver." : null);
    }
    public ManifestAnalysis {
        dependencies = List.copyOf(dependencies);
        if (manifestCount < 0 || unresolvedCount < 0)
            throw new IllegalArgumentException("Invalid analysis counts");
        if (!"COMPLETE".equals(coverageStatus) && !"PARTIAL".equals(coverageStatus))
            throw new IllegalArgumentException("Invalid analysis coverage status");
        if ("PARTIAL".equals(coverageStatus) && (coverageMessage == null || coverageMessage.isBlank()))
            throw new IllegalArgumentException("Partial analysis coverage requires an explanation");
    }
}
