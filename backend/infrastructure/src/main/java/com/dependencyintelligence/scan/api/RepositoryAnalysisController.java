package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import com.dependencyintelligence.scan.application.port.in.CancelAnalysisJob;
import com.dependencyintelligence.scan.application.port.in.FindAnalysisJob;
import com.dependencyintelligence.scan.application.port.in.RequestRepositoryAnalysis;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/dependency-analysis")
public final class RepositoryAnalysisController {
    private final RequestRepositoryAnalysis request;
    private final FindAnalysisJob find;
    private final CancelAnalysisJob cancel;
    private final AuthorizedRepositoryActions access;

    public RepositoryAnalysisController(RequestRepositoryAnalysis request, FindAnalysisJob find,
            CancelAnalysisJob cancel, AuthorizedRepositoryActions access) {
        this.request = request;
        this.find = find;
        this.cancel = cancel;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<AnalysisJobTicket> run(@PathVariable UUID organizationId,
            @PathVariable UUID repositoryId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            Principal principal) {
        AnalysisJobTicket ticket = access.execute(principal, organizationId, repositoryId, "scan.create",
            () -> request.execute(organizationId, repositoryId, idempotencyKey));
        return ResponseEntity.accepted().body(ticket);
    }

    @GetMapping("/{jobId}")
    public AnalysisJobStatus status(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
            @PathVariable UUID jobId, Principal principal) {
        return access.execute(principal, organizationId, repositoryId, "scan.read", () -> find
            .execute(organizationId, repositoryId, jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Analysis job")));
    }

    @GetMapping("/active")
    public ResponseEntity<AnalysisJobStatus> active(@PathVariable UUID organizationId,
            @PathVariable UUID repositoryId, Principal principal) {
        return access.execute(principal, organizationId, repositoryId, "scan.read", () -> find
            .active(organizationId, repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build()));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<AnalysisJobStatus> cancel(@PathVariable UUID organizationId,
            @PathVariable UUID repositoryId, @PathVariable UUID jobId, Principal principal) {
        return access.execute(principal, organizationId, repositoryId, "scan.create", () -> {
            cancel.execute(organizationId, repositoryId, jobId);
            AnalysisJobStatus status = find.execute(organizationId, repositoryId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis job"));
            return ResponseEntity.ok(status);
        });
    }
}
