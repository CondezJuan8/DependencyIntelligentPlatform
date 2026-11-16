package com.dependencyintelligence.identity.api;

import com.dependencyintelligence.identity.application.exception.ExternalIdentityException;
import com.dependencyintelligence.identity.application.port.in.ManageExternalIdentities;
import com.dependencyintelligence.identity.application.port.in.FindActiveAccount;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import com.dependencyintelligence.identity.infrastructure.oauth.*;
import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static com.dependencyintelligence.identity.application.exception.ExternalIdentityException.Reason.*;
import static com.dependencyintelligence.identity.infrastructure.oauth.ExplicitAuthorizationRequests.LINK_IDENTITY;

@RestController
@RequestMapping("/api/auth/providers")
public class ExternalIdentityController {
    private final ClientRegistrationRepository clients;
    private final ExplicitAuthorizationRequests resolver;
    private final BoundAuthorizationRequests requests;
    private final ManageExternalIdentities identities;
    private final RecentIdentitySession sessions;
    private final FindActiveAccount accounts;

    public ExternalIdentityController(ClientRegistrationRepository clients, ExplicitAuthorizationRequests resolver,
            BoundAuthorizationRequests requests, ManageExternalIdentities identities, RecentIdentitySession sessions, FindActiveAccount accounts) {
        this.clients = clients;
        this.resolver = resolver;
        this.requests = requests;
        this.identities = identities;
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @GetMapping
    public List<ProviderStatus> providers() {
        return Arrays.stream(IdentityProvider.values()).map(provider -> new ProviderStatus(provider.code(),
            provider == IdentityProvider.GOOGLE ? "Google" : "GitHub", enabled(provider))).toList();
    }

    @PostMapping("/{provider}/login")
    public AuthorizationUrl login(@PathVariable String provider, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        if (authentication != null && authentication.getPrincipal() instanceof SessionIdentity) {
            throw new ExternalIdentityException(ALREADY_AUTHENTICATED);
        }
        return begin(IdentityProvider.fromCode(provider), null, request, response);
    }

    @PostMapping("/{provider}/link")
    public AuthorizationUrl link(@PathVariable String provider, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        var identity = session(authentication);
        sessions.requireCurrent(identity);
        // The use case rechecks this account and credential version after the provider callback.
        if (!accounts.execute(identity.userId()).emailVerified()) throw new ExternalIdentityException(EMAIL_VERIFICATION_REQUIRED);
        return begin(IdentityProvider.fromCode(provider), identity, request, response);
    }

    @GetMapping("/connections")
    public ConnectionStatus connections(Authentication authentication) {
        var identity = session(authentication);
        var account = identities.connections(identity.userId());
        return new ConnectionStatus(account.providers().stream().map(IdentityProvider::code).toList(),
            account.passwordAvailable(), sessions.recent(identity));
    }

    @PostMapping("/{provider}/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String provider, Authentication authentication, HttpServletRequest request) {
        var identity = session(authentication);
        sessions.requireCurrent(identity);
        Set<IdentityProvider> enabled = Arrays.stream(IdentityProvider.values()).filter(this::enabled).collect(Collectors.toSet());
        identities.unlink(identity.userId(), identity.credentialVersion(), IdentityProvider.fromCode(provider), enabled);
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    private AuthorizationUrl begin(IdentityProvider provider, SessionIdentity identity, HttpServletRequest request, HttpServletResponse response) {
        if (!enabled(provider)) throw new ExternalIdentityException(PROVIDER_UNAVAILABLE);
        var authorization = resolver.resolve(request, provider.code());
        if (identity != null) authorization = OAuth2AuthorizationRequest.from(authorization)
            .attributes(attributes -> attributes.put(LINK_IDENTITY, identity)).build();
        requests.saveAuthorizationRequest(authorization, request, response);
        return new AuthorizationUrl(authorization.getAuthorizationRequestUri());
    }

    private boolean enabled(IdentityProvider provider) { return clients.findByRegistrationId(provider.code()) != null; }
    private static SessionIdentity session(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
            throw new ExternalIdentityException(SESSION_EXPIRED);
        }
        return identity;
    }

    public record ProviderStatus(String id, String name, boolean available) { }
    public record AuthorizationUrl(String authorizationUrl) { }
    public record ConnectionStatus(List<String> linkedProviders, boolean passwordAvailable, boolean recentAuthentication) { }
}
