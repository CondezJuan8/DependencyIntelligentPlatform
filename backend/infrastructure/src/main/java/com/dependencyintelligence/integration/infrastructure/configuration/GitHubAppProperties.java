package com.dependencyintelligence.integration.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.github.app")
public record GitHubAppProperties(boolean enabled, String slug, String id, String clientId, String clientSecret,
                                  String privateKeyBase64, String privateKeyFile, String callbackUrl, String apiBaseUrl) {
    @Override public String toString() { return "GitHubAppProperties[REDACTED]"; }
}
