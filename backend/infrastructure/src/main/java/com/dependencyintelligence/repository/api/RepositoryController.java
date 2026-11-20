package com.dependencyintelligence.repository.api;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.dto.RepositoryResult;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.DeleteRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import java.security.Principal;
import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;

/** Inbound adapter with membership, role and repository-team authorization. */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories")
public class RepositoryController {
    private final RegisterRepository register;
    private final FindRepository find;
    private final DeleteRepository delete;
    private final AuthorizedRepositoryActions access;

    public RepositoryController(RegisterRepository register, FindRepository find, DeleteRepository delete, AuthorizedRepositoryActions access) {
        this.register = register;
        this.find = find;
        this.delete = delete;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<RepositoryResult> register(@PathVariable UUID organizationId,
                                                     @Valid @RequestBody RegisterRepositoryRequest request, Principal principal) {
        var result = access.execute(principal, organizationId, null, "repository.manage", () ->
            register.execute(new RegisterRepositoryCommand(organizationId, request.provider(),
                request.externalId(), request.fullName(), request.defaultBranch())));
        return ResponseEntity.created(URI.create("/api/organizations/" + organizationId + "/repositories/" + result.id())).body(result);
    }

    @GetMapping("/{repositoryId}")
    public RepositoryResult find(@PathVariable UUID organizationId, @PathVariable UUID repositoryId, Principal principal) {
        return access.execute(principal, organizationId, repositoryId, "repository.read", () -> find.execute(organizationId, repositoryId));
    }

    @DeleteMapping("/{repositoryId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID repositoryId, Principal principal) {
        access.execute(principal, organizationId, repositoryId, "repository.manage", () -> { delete.execute(organizationId, repositoryId); return null; });
    }

    public record RegisterRepositoryRequest(@NotBlank @Size(max = 40) String provider,
                                            @NotBlank @Size(max = 255) String externalId,
                                            @NotBlank @Size(max = 512) String fullName,
                                            @NotBlank @Size(max = 255) String defaultBranch) { }
}
