package com.dependencyintelligence.dependencygraph.application.port.in;

import com.dependencyintelligence.dependencygraph.application.dto.RepositoryDependencyGraph;
import java.util.UUID;

public interface GetRepositoryDependencyGraph {
    RepositoryDependencyGraph execute(UUID organizationId, UUID repositoryId);
}
