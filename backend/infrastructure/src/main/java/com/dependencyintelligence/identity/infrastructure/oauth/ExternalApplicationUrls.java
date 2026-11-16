package com.dependencyintelligence.identity.infrastructure.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class ExternalApplicationUrls {
    private final String origin;

    public ExternalApplicationUrls(@Value("${app.identity.public-base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("PUBLIC_BASE_URL must be an HTTP(S) origin without credentials, query or fragment");
        }
        origin = baseUrl.replaceAll("/+$", "");
    }

    public String callback(String provider) { return origin + "/api/auth/oauth2/callback/" + provider; }
    public String result(String code) { return origin + "/#external-auth=" + code; }
}
