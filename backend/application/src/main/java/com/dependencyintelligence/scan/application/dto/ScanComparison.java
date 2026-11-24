package com.dependencyintelligence.scan.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanComparison(ScanPoint base, ScanPoint target, int added, int removed, int updated, int unchanged,
        List<DependencyDelta> dependencies, List<SecurityDelta> securityChanges, List<Recommendation> recommendations) {
    public ScanComparison {
        dependencies = List.copyOf(dependencies);
        securityChanges = List.copyOf(securityChanges);
        recommendations = List.copyOf(recommendations);
    }
    public record ScanPoint(UUID id, String commitSha, Instant completedAt, String coverageStatus, String coverageMessage) { }
    public record DependencyDelta(String ecosystem, String namespace, String name, String change,
            List<String> baseVersions, List<String> targetVersions, int baseOccurrences, int targetOccurrences,
            List<String> baseAdvisories, List<String> targetAdvisories, String baseAssessment, String targetAssessment) {
        public DependencyDelta {
            baseVersions = List.copyOf(baseVersions); targetVersions = List.copyOf(targetVersions);
            baseAdvisories = List.copyOf(baseAdvisories); targetAdvisories = List.copyOf(targetAdvisories);
        }
    }
    public record SecurityDelta(String advisoryId, String change, String packageName, String sourceUrl) { }
    public record Recommendation(String packageName, String priority, String explanation, List<String> evidenceIds) {
        public Recommendation { evidenceIds = List.copyOf(evidenceIds); }
    }
}
