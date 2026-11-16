package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import static com.dependencyintelligence.identity.infrastructure.oauth.ExplicitAuthorizationRequests.*;

public class BoundAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final RecentIdentitySession sessions;
    private final Clock clock;
    private final Duration lifetime;

    public BoundAuthorizationRequests(RecentIdentitySession sessions, Clock clock, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("OAuth request lifetime must be positive");
        this.sessions = sessions;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) { return delegate.loadAuthorizationRequest(request); }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorization, request, response);
    }

    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        var authorization = delegate.removeAuthorizationRequest(request, response);
        if (authorization == null) return null;
        if (!request.getRequestURI().equals(URI.create(authorization.getRedirectUri()).getPath())) reject();
        Instant issued = authorization.getAttribute(ISSUED_AT);
        if (issued == null || issued.isAfter(clock.instant()) || !issued.plus(lifetime).isAfter(clock.instant())) reject();
        SessionIdentity bound = authorization.getAttribute(LINK_IDENTITY);
        Object current = request.getAttribute(CURRENT_IDENTITY);
        if (bound != null) {
            if (!bound.equals(current)) reject();
            try { sessions.requireCurrent(bound); }
            catch (RuntimeException exception) { reject(); }
        } else if (current != null) reject();
        request.setAttribute(ORIGINAL_REQUEST, authorization);
        return authorization;
    }

    private static void reject() { throw new OAuth2AuthenticationException(new OAuth2Error("invalid_authorization_context")); }
}
