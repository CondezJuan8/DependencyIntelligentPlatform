package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.application.port.in.ManageExternalIdentities;
import com.dependencyintelligence.identity.application.port.in.ValidateAccountSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(IdentityProviderProperties.class)
public class ExternalProviderConfiguration {
    @Bean ClientRegistrationRepository identityClientRegistrations(IdentityProviderProperties properties, ExternalApplicationUrls urls) {
        return new ExternalClientRegistrations(properties, urls);
    }
    @Bean ExplicitAuthorizationRequests explicitAuthorizationRequests(ClientRegistrationRepository clients, Clock clock) {
        return new ExplicitAuthorizationRequests(clients, clock);
    }
    @Bean BoundAuthorizationRequests boundAuthorizationRequests(RecentIdentitySession sessions, Clock clock,
            @Value("${app.identity.authorization-lifetime}") Duration lifetime) {
        return new BoundAuthorizationRequests(sessions, clock, lifetime);
    }
    @Bean ExternalAuthenticationHandlers externalAuthenticationHandlers(ManageExternalIdentities identities, ValidateAccountSession sessions,
            RecentIdentitySession recent, SecurityContextRepository contexts, ExternalApplicationUrls urls, Clock clock) {
        return new ExternalAuthenticationHandlers(identities, sessions, recent, contexts, urls, clock);
    }
    @Bean GithubIdentityUserService githubIdentityUserService() { return new GithubIdentityUserService(new RestTemplate(requestFactory())); }
    @Bean OidcUserService googleIdentityUserService() {
        var service = new OidcUserService();
        service.setRetrieveUserInfo(request -> false);
        return service;
    }
    @Bean RestClientAuthorizationCodeTokenResponseClient identityTokenResponseClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder().requestFactory(requestFactory()).messageConverters(converters -> {
            converters.clear();
            converters.add(new FormHttpMessageConverter());
            converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
        }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }
}
