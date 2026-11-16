package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.application.dto.ExternalIdentityProof;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;

public class GithubIdentityUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService users = new DefaultOAuth2UserService();
    private final RestOperations http;

    public GithubIdentityUserService(RestOperations http) {
        this.http = http;
        users.setRestOperations(http);
    }

    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        if (!request.getClientRegistration().getRegistrationId().equals("github")) throw failure();
        OAuth2User user = users.loadUser(request);
        String subject = String.valueOf(user.getAttributes().get("id"));
        if (!subject.matches("[1-9][0-9]{0,19}")) throw failure();
        String email = verifiedEmail(request);
        String name = user.getAttribute("name");
        if (name == null || name.isBlank()) name = user.getAttribute("login");
        return new GithubIdentityUser(new ExternalIdentityProof(IdentityProvider.GITHUB, subject, email, email != null, name));
    }

    private String verifiedEmail(OAuth2UserRequest request) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(request.getAccessToken().getTokenValue());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2026-03-10");
        String uri = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri().replaceAll("/+$", "") + "/emails";
        try {
            var emails = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
            if (emails == null) return null;
            return emails.stream().filter(value -> Boolean.TRUE.equals(value.get("primary")) && Boolean.TRUE.equals(value.get("verified")))
                .map(value -> value.get("email")).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        } catch (RestClientException exception) { return null; }
    }

    private static OAuth2AuthenticationException failure() {
        return new OAuth2AuthenticationException(new OAuth2Error("provider_identity_unavailable"));
    }
}
