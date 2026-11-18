package com.dependencyintelligence.organization.api;

import com.dependencyintelligence.organization.application.dto.WorkspaceSettingsResult;
import com.dependencyintelligence.organization.application.port.in.ManageWorkspaceSettings;
import com.dependencyintelligence.organization.domain.WorkspaceLanguage;
import com.dependencyintelligence.organization.domain.WorkspaceTheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/settings")
public class WorkspaceSettingsController {
    private final ManageWorkspaceSettings settings;

    public WorkspaceSettingsController(ManageWorkspaceSettings settings) { this.settings = settings; }

    @GetMapping
    public WorkspaceSettingsResult get(Principal principal, @PathVariable UUID organizationId) {
        return settings.get(UUID.fromString(principal.getName()), organizationId);
    }

    @PostMapping
    public WorkspaceSettingsResult update(Principal principal, @PathVariable UUID organizationId,
                                           @Valid @RequestBody UpdateWorkspaceSettings request) {
        return settings.update(UUID.fromString(principal.getName()), organizationId, request.theme().name(), request.language().name());
    }

    public record UpdateWorkspaceSettings(@NotNull WorkspaceTheme theme, @NotNull WorkspaceLanguage language) { }
}
