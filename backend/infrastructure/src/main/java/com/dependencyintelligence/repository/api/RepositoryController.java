package com.dependencyintelligence.repository.api;

import com.dependencyintelligence.repository.application.dto.RegisterRepositoryCommand;
import com.dependencyintelligence.repository.application.dto.RepositoryResult;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

/** Inbound adapter. Remains blocked by security until membership authorization is implemented. */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories")
public class RepositoryController {
    private final RegisterRepository register;
    private final FindRepository find;

    public RepositoryController(RegisterRepository register, FindRepository find) {
        this.register = register;
        this.find = find;
    }

    @PostMapping
    public ResponseEntity<RepositoryResult> register(@PathVariable UUID organizationId,
                                                     @Valid @RequestBody RegisterRepositoryRequest request) {
        var result = register.execute(new RegisterRepositoryCommand(organizationId, request.provider(),
            request.externalId(), request.fullName(), request.defaultBranch()));
        return ResponseEntity.created(URI.create("/api/organizations/" + organizationId + "/repositories/" + result.id())).body(result);
    }

    @GetMapping("/{repositoryId}")
    public RepositoryResult find(@PathVariable UUID organizationId, @PathVariable UUID repositoryId) {
        return find.execute(organizationId, repositoryId);
    }

    public record RegisterRepositoryRequest(@NotBlank @Size(max = 40) String provider,
                                            @NotBlank @Size(max = 255) String externalId,
                                            @NotBlank @Size(max = 512) String fullName,
                                            @NotBlank @Size(max = 255) String defaultBranch) { }
}
