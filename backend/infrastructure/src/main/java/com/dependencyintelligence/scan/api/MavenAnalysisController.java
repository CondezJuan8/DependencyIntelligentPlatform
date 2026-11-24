package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import com.dependencyintelligence.scan.application.port.in.RequestRepositoryAnalysis;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/maven-analysis")
public class MavenAnalysisController {
 private final AnalyzeMavenRepository analysis; private final RequestRepositoryAnalysis request;
 private final AuthorizedRepositoryActions access;
 public MavenAnalysisController(AnalyzeMavenRepository analysis,RequestRepositoryAnalysis request,AuthorizedRepositoryActions access){this.analysis=analysis;this.request=request;this.access=access;}
 @PostMapping public ResponseEntity<AnalysisJobTicket> run(@PathVariable UUID organizationId,@PathVariable UUID repositoryId,
         @RequestHeader("Idempotency-Key") String idempotencyKey, Principal principal){
   AnalysisJobTicket ticket=access.execute(principal,organizationId,repositoryId,"scan.create",
       ()->request.execute(organizationId,repositoryId,idempotencyKey));
   return ResponseEntity.accepted().body(ticket);
 }
 @GetMapping public Optional<MavenInventory> latest(@PathVariable UUID organizationId,@PathVariable UUID repositoryId,
         @RequestParam(required=false) UUID scanId, Principal principal){
   return access.execute(principal,organizationId,repositoryId,"repository.read",()->scanId == null
       ? analysis.latest(organizationId,repositoryId) : analysis.find(organizationId,repositoryId,scanId));
 }
}
