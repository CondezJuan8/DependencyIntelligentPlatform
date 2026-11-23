package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.scan.application.usecase.AnalyzeRepositoryUseCase;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/dependency-analysis")
public final class RepositoryAnalysisController {
    private final AnalyzeRepositoryUseCase analysis;
    private final AuthorizedRepositoryActions access;

    public RepositoryAnalysisController(AnalyzeRepositoryUseCase analysis, AuthorizedRepositoryActions access) {
        this.analysis = analysis;
        this.access = access;
    }

    @PostMapping
    public List<AnalyzeRepositoryUseCase.Result> run(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
                                                    Principal principal) {
        access.execute(principal, organizationId, repositoryId, "scan.create", () -> true);
        return analysis.run(organizationId, repositoryId);
    }
}
