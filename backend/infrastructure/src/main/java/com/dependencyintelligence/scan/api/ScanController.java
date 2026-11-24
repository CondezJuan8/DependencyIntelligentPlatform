package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.dto.ScanResult;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.scan.application.port.in.FindScanHistory;
import com.dependencyintelligence.scan.application.dto.ScanHistoryItem;
import com.dependencyintelligence.scan.application.dto.ScanComparison;
import com.dependencyintelligence.scan.application.port.in.CompareScans;
import com.dependencyintelligence.scan.application.port.in.UpdateScanLabel;
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
    private final FindScanHistory history;
    private final CompareScans comparison;
    private final AuthorizedRepositoryActions access;
    private final UpdateScanLabel updateScanLabel;

    public ScanController(RequestScan requestScan, FindScanHistory history, CompareScans comparison,
            AuthorizedRepositoryActions access, UpdateScanLabel updateScanLabel) {
        this.requestScan = requestScan;
        this.history = history;
        this.comparison = comparison;
        this.access = access;
        this.updateScanLabel = updateScanLabel;
    }

    @GetMapping
    public ResponseEntity<java.util.List<ScanHistoryItem>> history(@PathVariable UUID organizationId,
            @PathVariable UUID repositoryId, Principal principal) {
        return ResponseEntity.ok(access.execute(principal, organizationId, repositoryId, "scan.read",
            () -> history.execute(organizationId, repositoryId)));
    }

    @GetMapping("/compare")
    public ScanComparison compare(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
            @RequestParam UUID baseScanId, @RequestParam UUID targetScanId, Principal principal) {
        return access.execute(principal, organizationId, repositoryId, "scan.read",
            () -> comparison.execute(organizationId, repositoryId, baseScanId, targetScanId));
    }

    @PostMapping
    public ResponseEntity<ScanResult> request(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
                                             @Valid @RequestBody ScanRequest request, Principal principal) {
        return ResponseEntity.accepted().body(access.execute(principal, organizationId, repositoryId, "scan.create", () ->
            requestScan.execute(new RequestScanCommand(organizationId, repositoryId, request.gitReference()))));
    }

    @PutMapping("/{scanId}/label")
    public ResponseEntity<Void> label(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
            @PathVariable UUID scanId, @Valid @RequestBody ScanLabelRequest request, Principal principal) {
        access.execute(principal, organizationId, repositoryId, "scan.create", () ->
            updateScanLabel.execute(organizationId, repositoryId, scanId, request.displayName()));
        return ResponseEntity.noContent().build();
    }

    public record ScanRequest(@NotBlank @Size(max = 255) String gitReference) { }
    public record ScanLabelRequest(@Size(max = 100) String displayName) { }
}
