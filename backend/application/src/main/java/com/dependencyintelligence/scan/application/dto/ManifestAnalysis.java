package com.dependencyintelligence.scan.application.dto;

import java.util.List;

/** Concrete package occurrences and only source-backed relationships are included. */
public record ManifestAnalysis(List<ManifestDependency> dependencies, List<ManifestDependencyEdge> edges,
                               int manifestCount, int unresolvedCount) {
    public ManifestAnalysis(List<ManifestDependency> dependencies, int manifestCount, int unresolvedCount) {
        this(dependencies, List.of(), manifestCount, unresolvedCount);
    }
    public ManifestAnalysis {
        dependencies = List.copyOf(dependencies);
        edges = List.copyOf(edges);
        if (manifestCount < 0 || unresolvedCount < 0)
            throw new IllegalArgumentException("Invalid analysis counts");
    }
}
