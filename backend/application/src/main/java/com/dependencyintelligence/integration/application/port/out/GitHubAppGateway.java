package com.dependencyintelligence.integration.application.port.out;

import com.dependencyintelligence.integration.application.dto.GitHubModels.Candidate;
import java.util.List;
import java.nio.file.Path;

public interface GitHubAppGateway {
    String installationUrl(String state);
    String userAuthorizationUrl(String state, String codeChallenge);
    String exchangeAuthorizationCode(String code, String codeVerifier);
    void verifyUserCanAccessInstallation(String userAccessToken, long installationId);
    InstallationInfo verifyInstallation(long installationId);
    List<Candidate> repositories(long installationId);
    String downloadArchive(long installationId, String repositoryExternalId, String fullName, Path target);
    record InstallationInfo(long id, String accountLogin, String accountType) { }
}
