package com.dependencyintelligence.scan.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/maven-analysis")
public class MavenAnalysisController {
 private final AnalyzeMavenRepository analysis; private final AuthorizedRepositoryActions access;
 public MavenAnalysisController(AnalyzeMavenRepository analysis,AuthorizedRepositoryActions access){this.analysis=analysis;this.access=access;}
 @PostMapping public MavenInventory run(@PathVariable UUID organizationId,@PathVariable UUID repositoryId,Principal principal){
   access.execute(principal,organizationId,repositoryId,"scan.create",()->true);
   return analysis.run(organizationId,repositoryId);
 }
 @GetMapping public Optional<MavenInventory> latest(@PathVariable UUID organizationId,@PathVariable UUID repositoryId,Principal principal){
   return access.execute(principal,organizationId,repositoryId,"repository.read",()->analysis.latest(organizationId,repositoryId));
 }
}
