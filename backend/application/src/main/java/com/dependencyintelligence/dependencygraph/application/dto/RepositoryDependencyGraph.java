package com.dependencyintelligence.dependencygraph.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryDependencyGraph(UUID repositoryId, String repositoryName, List<Source> sources,
        List<Node> nodes, List<Edge> edges, int unresolvedCount) {
    public RepositoryDependencyGraph {
        sources = List.copyOf(sources);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public record Source(UUID scanId, String ecosystem, String sha256, String commitSha, Instant analyzedAt,
                         int unresolvedCount) { }
    public record Node(String id, String label, String version, String ecosystem, String scope,
                       String dependencyType, String manifestPath, boolean project) { }
    public record Edge(String id, String source, String target, String relationship, String ecosystem) { }
}
