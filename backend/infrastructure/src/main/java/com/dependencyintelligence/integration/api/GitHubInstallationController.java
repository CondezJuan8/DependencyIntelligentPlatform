package com.dependencyintelligence.integration.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.integration.application.dto.GitHubModels.*;
import com.dependencyintelligence.integration.application.port.in.ManageGitHubInstallation;
import com.dependencyintelligence.integration.infrastructure.configuration.GitHubAppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/github")
public class GitHubInstallationController {
    private final ManageGitHubInstallation github;
    private final AuthorizedRepositoryActions authorization;
    private final GitHubAppProperties properties;
    public GitHubInstallationController(ManageGitHubInstallation github, AuthorizedRepositoryActions authorization,
                                        GitHubAppProperties properties) {
        this.github = github; this.authorization = authorization; this.properties = properties;
    }

    @GetMapping("/status")
    public SetupStatus status(@PathVariable UUID organizationId, Principal principal) {
        boolean canManage;
        try {
            canManage = authorization.execute(principal, organizationId, null, "repository.manage", () -> true);
        } catch (PermissionDeniedException denied) {
            canManage = false;
        }
        boolean configured = present(properties.enabled()) && present(properties.slug()) && present(properties.id())
            && present(properties.clientId()) && present(properties.clientSecret())
            && (present(properties.privateKeyBase64()) || present(properties.privateKeyFile()))
            && present(properties.callbackUrl());
        String detail = !canManage
            ? "Solo el propietario o un administrador de esta organización puede conectar GitHub."
            : !configured
                ? "La GitHub App no está configurada. Completa las variables GITHUB_APP_* en .env y reinicia el backend."
                : "GitHub está listo para instalarse en esta organización.";
        return new SetupStatus(configured, canManage, detail);
    }

    @PostMapping("/installations")
    public InstallationLink begin(@PathVariable UUID organizationId, Principal principal,
                                  jakarta.servlet.http.HttpServletRequest request) {
        return authorization.execute(principal, organizationId, null, "repository.manage", () ->
            github.begin(organizationId, user(principal), request.getSession(true).getId()));
    }

    @PostMapping("/installations/existing")
    public InstallationLink connectExisting(@PathVariable UUID organizationId, @Valid @RequestBody ExistingRequest body,
                                             Principal principal, jakarta.servlet.http.HttpServletRequest request) {
        return authorization.execute(principal, organizationId, null, "repository.manage", () -> {
            try {
                return github.connectExisting(organizationId, user(principal), request.getSession(true).getId(), Long.parseLong(body.installationId()));
            } catch (NumberFormatException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de instalación de GitHub no es válido.");
            }
        });
    }

    @PostMapping("/installations/authorize")
    public InstallationLink authorizeInstallation(@PathVariable UUID organizationId, @Valid @RequestBody CompleteRequest request,
                                                   Principal principal, jakarta.servlet.http.HttpServletRequest servletRequest) {
        return authorization.execute(principal, organizationId, null, "repository.manage", () -> {
            var session = servletRequest.getSession(false);
            if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión de la instalación ya no está disponible.");
            try {
                long installationId = Long.parseLong(request.installationId());
                return github.authorizeInstallation(organizationId, user(principal), session.getId(), request.state(), installationId);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de instalación de GitHub no es válido.");
            } catch (com.dependencyintelligence.integration.application.usecase.InvalidGitHubInstallStateException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El retorno de GitHub venció o pertenece a otra sesión. Vuelve a iniciar la instalación.");
            }
        });
    }

    @GetMapping("/repositories")
    public Workspace workspace(@PathVariable UUID organizationId, Principal principal) {
        return github.workspace(organizationId, user(principal));
    }

    @PostMapping("/repositories/import")
    public ImportResult importRepositories(@PathVariable UUID organizationId, @Valid @RequestBody ImportRequest request,
                                           Principal principal) {
        return github.importRepositories(organizationId, user(principal), request.repositoryIds());
    }

    private static UUID user(Principal principal) { return UUID.fromString(principal.getName()); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean present(boolean value) { return value; }

    public record SetupStatus(boolean configured, boolean canManage, String detail) { }

    public record CompleteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String state,
                                  @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String installationId) {
        @Override public String toString() { return "CompleteRequest[REDACTED]"; }
    }
    public record ImportRequest(@NotEmpty @Size(max = 500) List<@Pattern(regexp = "[0-9]{1,20}") String> repositoryIds) { }
    public record ExistingRequest(@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String installationId) { }
}
