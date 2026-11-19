package com.dependencyintelligence.integration.application.port.in;

import com.dependencyintelligence.integration.application.dto.GitHubModels.*;
import java.util.List;
import java.util.UUID;

public interface ManageGitHubInstallation {
    InstallationLink begin(UUID organization, UUID user, String browserSession);
    InstallationLink connectExisting(UUID organization, UUID user, String browserSession, long installationId);
    InstallationLink authorizeInstallation(UUID organization, UUID user, String browserSession, String state, long installationId);
    void completeFromCallback(UUID user, String browserSession, String state, String code);
    Workspace workspace(UUID organization, UUID user);
    ImportResult importRepositories(UUID organization, UUID user, List<String> externalRepositoryIds);
}
