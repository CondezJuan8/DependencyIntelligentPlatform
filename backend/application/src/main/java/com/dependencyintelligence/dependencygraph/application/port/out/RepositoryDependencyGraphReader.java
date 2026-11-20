package com.dependencyintelligence.dependencygraph.application.port.out;

import com.dependencyintelligence.dependencygraph.application.dto.RepositoryDependencyGraph;
import java.util.UUID;

public interface RepositoryDependencyGraphReader {
    RepositoryDependencyGraph read(UUID organizationId, UUID repositoryId);
}
