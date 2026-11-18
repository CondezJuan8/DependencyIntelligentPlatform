package com.dependencyintelligence.integration.infrastructure.configuration;

import com.dependencyintelligence.integration.application.port.in.ManageGitHubInstallation;
import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStateStore;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStore;
import com.dependencyintelligence.integration.application.usecase.ManageGitHubInstallationUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GitHubAppProperties.class)
public class GitHubAppConfiguration {
    @Bean ManageGitHubInstallation manageGitHubInstallation(GitHubAppGateway app,
            GitHubInstallationStateStore states, GitHubInstallationStore installations, Clock clock) {
        return new ManageGitHubInstallationUseCase(app, states, installations, clock);
    }
}
