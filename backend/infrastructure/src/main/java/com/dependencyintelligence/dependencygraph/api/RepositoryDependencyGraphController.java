package com.dependencyintelligence.dependencygraph.api;

import com.dependencyintelligence.dependencygraph.application.dto.RepositoryDependencyGraph;
import com.dependencyintelligence.dependencygraph.application.port.in.GetRepositoryDependencyGraph;
import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/dependency-graph")
public final class RepositoryDependencyGraphController {
    private final GetRepositoryDependencyGraph graph;
    private final AuthorizedRepositoryActions access;

    public RepositoryDependencyGraphController(GetRepositoryDependencyGraph graph, AuthorizedRepositoryActions access) {
        this.graph = graph;
        this.access = access;
    }

    @GetMapping
    public RepositoryDependencyGraph get(@PathVariable UUID organizationId, @PathVariable UUID repositoryId, Principal principal) {
        access.execute(principal, organizationId, repositoryId, "repository.read", () -> true);
        return graph.execute(organizationId, repositoryId);
    }
}
