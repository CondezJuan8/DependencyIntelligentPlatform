package com.dependencyintelligence.integration.application.dto;

import java.util.List;

public final class GitHubModels {
    private GitHubModels() { }
    public record InstallationLink(String url) { }
    public record Candidate(String externalId, String fullName, String defaultBranch, String htmlUrl,
                            String visibility, boolean archived) { }
    public record Installation(long id, String accountLogin, String accountType, List<Candidate> repositories) {
        public Installation { repositories = List.copyOf(repositories); }
    }
    public record Workspace(List<Installation> installations, List<String> importedRepositoryIds,
                            int unavailableRepositoryCount, int revokedInstallationCount) {
        public Workspace { installations = List.copyOf(installations); importedRepositoryIds = List.copyOf(importedRepositoryIds); }
    }
    public record ImportResult(int importedCount) { }
}
