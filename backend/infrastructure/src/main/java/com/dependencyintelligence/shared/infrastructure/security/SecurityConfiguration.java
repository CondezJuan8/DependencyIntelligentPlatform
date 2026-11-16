package com.dependencyintelligence.shared.infrastructure.security;

import com.dependencyintelligence.identity.application.port.in.ValidateAccountSession;
import com.dependencyintelligence.identity.infrastructure.security.ActiveAccountFilter;
import com.dependencyintelligence.identity.infrastructure.security.AuthenticationRateLimitFilter;
import com.dependencyintelligence.identity.infrastructure.oauth.*;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.http.HttpMethod;
import java.time.Clock;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityContextRepository securityContexts() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthentication(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts, CsrfTokenRepository csrf,
            ValidateAccountSession accounts, Clock clock,
            ExplicitAuthorizationRequests resolver, BoundAuthorizationRequests requests, ExternalAuthenticationHandlers handlers,
            GithubIdentityUserService github, OidcUserService google, RestClientAuthorizationCodeTokenResponseClient tokenClient,
            @Value("${app.security.authentication.max-attempts-per-minute:20}") int maxAttempts) throws Exception {
        return http
            .securityContext(context -> context.securityContextRepository(contexts))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .requestCache(cache -> cache.disable())
            .addFilterAfter(new ActiveAccountFilter(accounts), SecurityContextHolderFilter.class)
            .addFilterBefore(new CallbackIdentityCapture(), OAuth2LoginAuthenticationFilter.class)
            .addFilterBefore(new AuthenticationRateLimitFilter(clock, maxAttempts), CsrfFilter.class)
            .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .oauth2Login(oauth -> oauth.loginPage("/")
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver).authorizationRequestRepository(requests))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/callback/*"))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
                .userInfoEndpoint(endpoint -> endpoint.userService(github).oidcUserService(google))
                .authorizedClientRepository(new DiscardingAuthorizedClients())
                .successHandler(handlers).failureHandler(handlers))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/providers", "/api/auth/oauth2/callback/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/providers/*/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/password-reset/request",
                    "/api/auth/password-reset/confirm", "/api/auth/email-verification/confirm").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/email-verification/request", "/api/auth/providers/**",
                    "/api/organizations", "/api/organizations/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> response.setStatus(401))
                .accessDeniedHandler((request, response, exception) -> response.setStatus(403)))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .build();
    }
}
