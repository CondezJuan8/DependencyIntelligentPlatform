package com.dependencyintelligence.organization.application.dto;

import com.dependencyintelligence.organization.domain.WorkspaceLanguage;
import com.dependencyintelligence.organization.domain.WorkspaceTheme;
import java.util.UUID;

public record WorkspaceSettingsResult(UUID organizationId, String organizationName, WorkspaceTheme theme,
                                      WorkspaceLanguage language, boolean canManage) { }
