package com.dependencyintelligence.organization.application.port.out;

import com.dependencyintelligence.organization.domain.WorkspaceLanguage;
import com.dependencyintelligence.organization.domain.WorkspaceTheme;
import java.util.UUID;

public interface WorkspaceSettingsStore {
    Settings read(UUID organization);
    void update(UUID organization, UUID actor, WorkspaceTheme theme, WorkspaceLanguage language);

    record Settings(UUID organizationId, String organizationName, WorkspaceTheme theme, WorkspaceLanguage language) { }
}
