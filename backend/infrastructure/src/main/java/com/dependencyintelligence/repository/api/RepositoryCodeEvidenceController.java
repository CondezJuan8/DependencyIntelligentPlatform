package com.dependencyintelligence.repository.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.repository.application.dto.RepositoryCodeEvidence;
import com.dependencyintelligence.repository.application.port.in.FindRepositoryVulnerabilityEvidence;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/vulnerability-analysis")
public final class RepositoryCodeEvidenceController {
    private final AuthorizedRepositoryActions authorization;
    private final FindRepositoryVulnerabilityEvidence evidence;

    public RepositoryCodeEvidenceController(AuthorizedRepositoryActions authorization, FindRepositoryVulnerabilityEvidence evidence) {
        this.authorization = authorization; this.evidence = evidence;
    }

    @GetMapping("/{advisoryId}/code-evidence")
    public RepositoryCodeEvidence find(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
            @PathVariable String advisoryId, @RequestParam UUID scanId, @RequestParam UUID scanComponentId,
            Principal principal) {
        authorization.execute(principal, organizationId, repositoryId, "repository.read", () -> true);
        return evidence.execute(organizationId, repositoryId, scanId, scanComponentId, advisoryId);
    }
}
