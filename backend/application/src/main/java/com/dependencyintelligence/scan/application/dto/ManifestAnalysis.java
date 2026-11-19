package com.dependencyintelligence.scan.application.dto;

import java.util.List;

/** Only concrete direct dependencies are included; unresolved declarations remain counted. */
public record ManifestAnalysis(List<ManifestDependency> dependencies, int manifestCount, int unresolvedCount) {
    public ManifestAnalysis {
        dependencies = List.copyOf(dependencies);
        if (manifestCount < 0 || unresolvedCount < 0)
            throw new IllegalArgumentException("Invalid analysis counts");
    }
}
