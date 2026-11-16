package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.application.exception.ExternalIdentityException;
import com.dependencyintelligence.identity.application.port.in.ManageExternalIdentities;
import com.dependencyintelligence.identity.application.port.in.ValidateAccountSession;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import static com.dependencyintelligence.identity.infrastructure.oauth.ExplicitAuthorizationRequests.*;

public class ExternalAuthenticationHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final ManageExternalIdentities identities;
    private final ValidateAccountSession sessions;
    private final RecentIdentitySession recent;
    private final SecurityContextRepository contexts;
    private final ExternalApplicationUrls urls;
    private final Clock clock;

    public ExternalAuthenticationHandlers(ManageExternalIdentities identities, ValidateAccountSession sessions,
            RecentIdentitySession recent, SecurityContextRepository contexts, ExternalApplicationUrls urls, Clock clock) {
        this.identities = identities;
        this.sessions = sessions;
        this.recent = recent;
        this.contexts = contexts;
        this.urls = urls;
        this.clock = clock;
    }

    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        try {
            var oauth = (OAuth2AuthenticationToken) authentication;
            var proof = proof(oauth);
            var authorization = (OAuth2AuthorizationRequest) request.getAttribute(ORIGINAL_REQUEST);
            if (authorization == null) throw new IllegalArgumentException("Missing authorization context");
            SessionIdentity original = authorization.getAttribute(LINK_IDENTITY);
            AuthenticatedAccount account;
            Instant authenticatedAt;
            if (original != null) {
                recent.requireCurrent(original);
                account = identities.link(original.userId(), original.credentialVersion(), proof);
                authenticatedAt = original.authenticatedAt();
            } else {
                account = identities.signIn(proof);
                authenticatedAt = clock.instant();
            }
            save(new SessionIdentity(account.account().id(), account.credentialVersion(), authenticatedAt), request, response);
            response.sendRedirect(urls.result(original == null ? "signed_in" : "linked"));
        } catch (ExternalIdentityException exception) {
            fail(request, response, exception.reason().name().toLowerCase(Locale.ROOT));
        } catch (RuntimeException exception) { fail(request, response, "login_failed"); }
    }

    private ExternalIdentityProof proof(OAuth2AuthenticationToken token) {
        String id = token.getAuthorizedClientRegistrationId();
        if (id.equals("github") && token.getPrincipal() instanceof GithubIdentityUser github) return github.proof();
        if (id.equals("google") && token.getPrincipal() instanceof OidcUser google) {
            String email = google.getEmail();
            String domain = email != null && email.contains("@") ? email.substring(email.lastIndexOf('@') + 1) : "";
            String hostedDomain = google.getClaimAsString("hd");
            boolean authoritative = Boolean.TRUE.equals(google.getEmailVerified()) &&
                (domain.equalsIgnoreCase("gmail.com") || (!domain.isEmpty() && domain.equalsIgnoreCase(hostedDomain)));
            return new ExternalIdentityProof(IdentityProvider.GOOGLE, google.getSubject(), email, authoritative, google.getFullName());
        }
        throw new IllegalArgumentException("Unsupported provider identity");
    }

    @Override public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException { fail(request, response, "login_failed"); }

    private void fail(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        var original = request.getAttribute(CURRENT_IDENTITY);
        if (original instanceof SessionIdentity identity && sessions.isCurrent(identity.userId(), identity.credentialVersion())) {
            save(identity, request, response);
        } else {
            SecurityContextHolder.clearContext();
            contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        }
        response.sendRedirect(urls.result(code));
    }

    private void save(SessionIdentity identity, HttpServletRequest request, HttpServletResponse response) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
