package com.dependencyintelligence.dependencygraph.application.usecase;

import com.dependencyintelligence.dependencygraph.application.dto.RepositoryDependencyGraph;
import com.dependencyintelligence.dependencygraph.application.port.in.GetRepositoryDependencyGraph;
import com.dependencyintelligence.dependencygraph.application.port.out.RepositoryDependencyGraphReader;
import java.util.UUID;

public final class GetRepositoryDependencyGraphUseCase implements GetRepositoryDependencyGraph {
    private final RepositoryDependencyGraphReader reader;

    public GetRepositoryDependencyGraphUseCase(RepositoryDependencyGraphReader reader) { this.reader = reader; }

    @Override public RepositoryDependencyGraph execute(UUID organizationId, UUID repositoryId) {
        if (organizationId == null || repositoryId == null) throw new IllegalArgumentException("Organization and repository are required.");
        return reader.read(organizationId, repositoryId);
    }
}
