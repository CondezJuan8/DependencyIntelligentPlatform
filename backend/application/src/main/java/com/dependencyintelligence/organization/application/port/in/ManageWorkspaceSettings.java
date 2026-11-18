package com.dependencyintelligence.organization.application.port.in;

import com.dependencyintelligence.organization.application.dto.WorkspaceSettingsResult;
import java.util.UUID;

public interface ManageWorkspaceSettings {
    WorkspaceSettingsResult get(UUID actor, UUID organization);
    WorkspaceSettingsResult update(UUID actor, UUID organization, String theme, String language);
}
