package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.dto.ScanResult;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.security.Principal;
import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/scans")
public class ScanController {
    private final RequestScan requestScan;
    private final AuthorizedRepositoryActions access;

    public ScanController(RequestScan requestScan, AuthorizedRepositoryActions access) {
        this.requestScan = requestScan;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<ScanResult> request(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
                                             @Valid @RequestBody ScanRequest request, Principal principal) {
        return ResponseEntity.accepted().body(access.execute(principal, organizationId, repositoryId, "scan.create", () ->
            requestScan.execute(new RequestScanCommand(organizationId, repositoryId, request.gitReference()))));
    }

    public record ScanRequest(@NotBlank @Size(max = 255) String gitReference) { }
}
