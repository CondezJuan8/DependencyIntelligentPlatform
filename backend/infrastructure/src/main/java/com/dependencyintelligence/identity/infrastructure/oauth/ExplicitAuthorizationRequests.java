package com.dependencyintelligence.identity.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import java.time.Clock;

/** Only CSRF-protected controller POSTs initiate login or linking. */
public class ExplicitAuthorizationRequests implements OAuth2AuthorizationRequestResolver {
    public static final String ISSUED_AT = "dependency.issuedAt";
    public static final String LINK_IDENTITY = "dependency.linkIdentity";
    public static final String ORIGINAL_REQUEST = "dependency.originalAuthorization";
    public static final String CURRENT_IDENTITY = "dependency.callbackIdentity";
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public ExplicitAuthorizationRequests(ClientRegistrationRepository clients, Clock clock) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/auth/oauth2/authorize");
        delegate.setAuthorizationRequestCustomizer(builder -> {
            OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
            builder.attributes(attributes -> attributes.put(ISSUED_AT, clock.instant()));
        });
    }

    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) { return null; }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return delegate.resolve(request, registrationId);
    }
}
