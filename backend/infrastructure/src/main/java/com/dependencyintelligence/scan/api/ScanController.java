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

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/scans")
public class ScanController {
    private final RequestScan requestScan;

    public ScanController(RequestScan requestScan) { this.requestScan = requestScan; }

    @PostMapping
    public ResponseEntity<ScanResult> request(@PathVariable UUID organizationId, @PathVariable UUID repositoryId,
                                             @Valid @RequestBody ScanRequest request) {
        return ResponseEntity.accepted().body(requestScan.execute(
            new RequestScanCommand(organizationId, repositoryId, request.gitReference())));
    }

    public record ScanRequest(@NotBlank @Size(max = 255) String gitReference) { }
}
