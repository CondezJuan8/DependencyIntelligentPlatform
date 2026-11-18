package com.dependencyintelligence.integration.application.port.out;

import com.dependencyintelligence.integration.application.dto.GitHubModels.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GitHubInstallationStore {
    void connect(UUID organizationId, UUID userId, GitHubAppGateway.InstallationInfo installation);
    List<GitHubAppGateway.InstallationInfo> connected(UUID organizationId, UUID userId);
    List<String> importedRepositoryIds(UUID organizationId, UUID userId);
    int importRepositories(UUID organizationId, UUID userId, Map<Long, List<Candidate>> repositories);
}
