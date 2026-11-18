package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.dto.WorkspaceSettingsResult;
import com.dependencyintelligence.organization.application.port.in.ManageWorkspaceSettings;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import com.dependencyintelligence.organization.application.port.out.WorkspaceSettingsStore;
import com.dependencyintelligence.organization.domain.WorkspaceLanguage;
import com.dependencyintelligence.organization.domain.WorkspaceTheme;
import java.util.Objects;
import java.util.UUID;
import static com.dependencyintelligence.organization.application.usecase.OrganizationAccessRules.manager;

public final class ManageWorkspaceSettingsUseCase implements ManageWorkspaceSettings {
    private final OrganizationAccessStore access;
    private final WorkspaceSettingsStore settings;

    public ManageWorkspaceSettingsUseCase(OrganizationAccessStore access, WorkspaceSettingsStore settings) {
        this.access = Objects.requireNonNull(access);
        this.settings = Objects.requireNonNull(settings);
    }

    @Override public WorkspaceSettingsResult get(UUID actor, UUID organization) {
        var member = access.actor(Objects.requireNonNull(organization), Objects.requireNonNull(actor));
        return result(organization, settings.read(organization), member.manager());
    }

    @Override public WorkspaceSettingsResult update(UUID actor, UUID organization, String theme, String language) {
        Objects.requireNonNull(organization);
        manager(access, organization, Objects.requireNonNull(actor));
        var validatedTheme = WorkspaceTheme.parse(theme);
        var validatedLanguage = WorkspaceLanguage.parse(language);
        settings.update(organization, actor, validatedTheme, validatedLanguage);
        access.audit(organization, actor, "organization.settings.update", "organization_settings", organization);
        return result(organization, settings.read(organization), true);
    }

    private WorkspaceSettingsResult result(UUID organization, WorkspaceSettingsStore.Settings settings, boolean canManage) {
        if (settings == null || !settings.organizationId().equals(organization)) throw new PermissionDeniedException();
        return new WorkspaceSettingsResult(settings.organizationId(), settings.organizationName(), settings.theme(), settings.language(), canManage);
    }
}
