package com.dependencyintelligence.identity.infrastructure.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.identity.providers")
public record IdentityProviderProperties(Client google, Client github) {
    public record Client(boolean enabled, String clientId, String clientSecret) {
        @Override public String toString() { return "OAuthClient[REDACTED]"; }
    }
}
