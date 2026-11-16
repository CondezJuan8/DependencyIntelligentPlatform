package com.dependencyintelligence.persistence;

import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import static com.dependencyintelligence.identity.infrastructure.oauth.ExplicitAuthorizationRequests.ISSUED_AT;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.enforce-runtime-role=true", "app.security.authentication.max-attempts-per-minute=1000"})
@AutoConfigureMockMvc
@Import(ExternalIdentityIT.Clients.class)
class ExternalIdentityIT extends PostgresIntegrationSupport {
    private static LocalOAuthProvider provider;
    private static final String PASSWORD = "local fixture passphrase 2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private static synchronized LocalOAuthProvider provider() {
        if (provider == null) {
            try { provider = new LocalOAuthProvider(); }
            catch (Exception exception) { throw new IllegalStateException("Unable to start isolated identity fixture", exception); }
        }
        return provider;
    }

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        database(registry, true);
        registry.add("app.identity.public-base-url", () -> "http://localhost:8088");
        provider();
    }

    @TestConfiguration static class Clients {
        @Bean @Primary ClientRegistrationRepository fixtureClients() {
            return new InMemoryClientRegistrationRepository(provider().client("google"), provider().client("github"));
        }
    }

    @AfterAll static void stopProvider() { if (provider != null) provider.close(); }

    private record Attempt(String provider, Map<String, String> parameters, String csrfHeader, String csrfToken) { }
    private record Result(int status, String location) { }

    private String email() { return "oauth-" + UUID.randomUUID() + "@example.test"; }
    private LocalOAuthProvider.Profile google(String email) {
        return new LocalOAuthProvider.Profile("google-sub-" + UUID.randomUUID(), email, true, "example.test");
    }
    private LocalOAuthProvider.Profile github(String email, boolean verified) {
        return new LocalOAuthProvider.Profile(Long.toString(ThreadLocalRandom.current().nextLong(100_000_000L, Long.MAX_VALUE)), email, verified, null);
    }

    private MvcResult postJson(String path, MockHttpSession session, Map<String, ?> body) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        return mvc.perform(post(path).session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
    }

    private UUID register(MockHttpSession session, String email, boolean verified) throws Exception {
        var result = postJson("/api/auth/register", session, Map.of("email", email, "displayName", "Local OAuth fixture", "password", PASSWORD));
        assertEquals(201, result.getResponse().getStatus());
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        if (verified) jdbc.update("update users set email_verified_at = current_timestamp where id = ?", id);
        return id;
    }

    private Attempt begin(MockHttpSession session, String id, String action) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        var result = mvc.perform(post("/api/auth/providers/" + id + "/" + action).session(session)
            .header(token.get("headerName").asText(), token.get("token").asText())
            .header("Host", "untrusted.example").contentType("application/json").content("{}")).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String authorization = json.readTree(result.getResponse().getContentAsString()).get("authorizationUrl").asText();
        var parameters = LocalOAuthProvider.parameters(URI.create(authorization).getRawQuery());
        assertEquals("http://localhost:8088/api/auth/oauth2/callback/" + id, parameters.get("redirect_uri"));
        assertEquals("S256", parameters.get("code_challenge_method"));
        assertEquals("code", parameters.get("response_type"));
        assertFalse(parameters.get("state").isBlank());
        assertFalse(parameters.containsKey("client_secret"));
        return new Attempt(id, parameters, token.get("headerName").asText(), token.get("token").asText());
    }

    private Result callback(MockHttpSession session, Attempt attempt, LocalOAuthProvider.Profile profile, String defect) throws Exception {
        String code = provider().code(attempt.provider(), attempt.parameters(), profile, defect);
        return rawCallback(session, attempt.provider(), attempt.parameters().get("state"), code);
    }

    private Result rawCallback(MockHttpSession session, String id, String state, String code) throws Exception {
        String path = "/api/auth/oauth2/callback/" + id;
        var result = mvc.perform(get(path).servletPath(path).session(session).param("state", state).param("code", code)
            .with(request -> { request.setServerName("localhost"); request.setServerPort(8088); return request; })).andReturn();
        return new Result(result.getResponse().getStatus(), result.getResponse().getRedirectedUrl());
    }

    private JsonNode me(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void googleValidatesTheRealCodeFlowAndKeepsTheStableSubject() throws Exception {
        var session = new MockHttpSession();
        String originalSession = session.getId();
        String email = email();
        var profile = google(email);
        var attempt = begin(session, "google", "login");
        assertTrue(attempt.parameters().get("scope").contains("openid"));
        assertNotNull(attempt.parameters().get("nonce"));
        int pkce = provider().pkceValidated.get();
        assertEquals(new Result(302, "http://localhost:8088/#external-auth=signed_in"), callback(session, attempt, profile, ""));
        assertEquals(pkce + 1, provider().pkceValidated.get());
        assertNotEquals(originalSession, session.getId());
        var account = me(session);
        assertTrue(account.get("emailVerified").asBoolean());
        String id = account.get("id").asText();
        assertEquals(0, jdbc.queryForObject("select count(*) from user_password_credentials where user_id = ?::uuid", Integer.class, id));
        mvc.perform(post("/api/organizations").session(session).header(attempt.csrfHeader(), attempt.csrfToken())
            .contentType("application/json").content("{\"name\":\"Stale CSRF\"}")).andExpect(status().isForbidden());
        assertEquals(204, postJson("/api/auth/logout", session, Map.of()).getResponse().getStatus());
        var again = new MockHttpSession();
        var changed = new LocalOAuthProvider.Profile(profile.subject(), email(), false, null);
        callback(again, begin(again, "google", "login"), changed, "");
        assertEquals(id, me(again).get("id").asText());
        assertEquals(email, me(again).get("email").asText());
        for (var names = again.getAttributeNames(); names.hasMoreElements();) {
            assertFalse(String.valueOf(again.getAttribute(names.nextElement())).contains("fixture-access-"));
        }
    }

    @Test void githubUsesVerifiedPrimaryEmailAndIdEvenWhenEmailLaterBecomesUnavailable() throws Exception {
        String email = email();
        var profile = github(email, true);
        var session = new MockHttpSession();
        var attempt = begin(session, "github", "login");
        assertEquals(Set.of("read:user", "user:email"), Set.of(attempt.parameters().get("scope").split(" ")));
        assertEquals(new Result(302, "http://localhost:8088/#external-auth=signed_in"), callback(session, attempt, profile, ""));
        var account = me(session);
        assertEquals(email, account.get("email").asText());
        assertTrue(account.get("emailVerified").asBoolean());
        String id = account.get("id").asText();
        postJson("/api/auth/logout", session, Map.of());
        var again = new MockHttpSession();
        callback(again, begin(again, "github", "login"), new LocalOAuthProvider.Profile(profile.subject(), null, false, null), "");
        assertEquals(id, me(again).get("id").asText());
    }

    @Test void matchingEmailNeverMergesAnExistingLocalAccount() throws Exception {
        String email = email();
        UUID id = register(new MockHttpSession(), email, true);
        var session = new MockHttpSession();
        assertEquals(new Result(302, "http://localhost:8088/#external-auth=link_required"),
            callback(session, begin(session, "google", "login"), google(email), ""));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_identities where user_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from users where email_normalized = ?", Integer.class, email));
    }

    @Test void explicitLinkingAllowsDifferentEmailsAndUnlinkRevokesAccess() throws Exception {
        var session = new MockHttpSession();
        String email = email();
        UUID id = register(session, email, true);
        var second = new MockHttpSession();
        postJson("/api/auth/login", second, Map.of("email", email, "password", PASSWORD));
        var profile = github(email(), true);
        assertEquals(new Result(302, "http://localhost:8088/#external-auth=linked"),
            callback(session, begin(session, "github", "link"), profile, ""));
        assertEquals(id.toString(), me(session).get("id").asText());
        assertEquals(email, me(session).get("email").asText());
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/providers/connections").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.linkedProviders[0]").value("github")).andExpect(jsonPath("$.passwordAvailable").value(true));
        assertEquals(204, postJson("/api/auth/providers/github/unlink", session, Map.of()).getResponse().getStatus());
        assertTrue(session.isInvalid());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_identities where user_id = ?", Integer.class, id));
        assertEquals(2L, jdbc.queryForObject("select credential_version from users where id = ?", Long.class, id));
        var local = new MockHttpSession();
        assertEquals(200, postJson("/api/auth/login", local, Map.of("email", email, "password", PASSWORD)).getResponse().getStatus());
    }

    @Test void anExternalOnlyAccountKeepsAtLeastOneAvailableMethod() throws Exception {
        var session = new MockHttpSession();
        callback(session, begin(session, "google", "login"), google(email()), "");
        String id = me(session).get("id").asText();
        var denied = postJson("/api/auth/providers/google/unlink", session, Map.of());
        assertEquals(409, denied.getResponse().getStatus());
        assertEquals("last_sign_in_method", json.readTree(denied.getResponse().getContentAsString()).get("code").asText());
        var github = github(null, false);
        assertEquals(new Result(302, "http://localhost:8088/#external-auth=linked"),
            callback(session, begin(session, "github", "link"), github, ""));
        assertEquals(204, postJson("/api/auth/providers/google/unlink", session, Map.of()).getResponse().getStatus());
        var again = new MockHttpSession();
        callback(again, begin(again, "github", "login"), github, "");
        assertEquals(id, me(again).get("id").asText());
    }

    @Test void invalidStateProviderAndExpiredRequestsStopBeforeTokenExchange() throws Exception {
        var session = new MockHttpSession();
        var attempt = begin(session, "google", "login");
        int before = provider().tokenRequests.get();
        var profile = google(email());
        String code = provider().code("google", attempt.parameters(), profile, "");
        assertEquals("http://localhost:8088/#external-auth=login_failed", rawCallback(session, "google", "wrong-state", code).location());
        assertEquals(before, provider().tokenRequests.get());
        assertEquals("http://localhost:8088/#external-auth=login_failed",
            rawCallback(session, "github", attempt.parameters().get("state"), code).location());
        assertEquals(before, provider().tokenRequests.get());
        var expired = begin(session, "google", "login");
        for (var names = session.getAttributeNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            if (session.getAttribute(name) instanceof OAuth2AuthorizationRequest request) {
                session.setAttribute(name, OAuth2AuthorizationRequest.from(request)
                    .attributes(attributes -> attributes.put(ISSUED_AT, Instant.now().minusSeconds(600))).build());
            }
        }
        assertEquals("http://localhost:8088/#external-auth=login_failed", callback(session, expired, profile, "").location());
        assertEquals(before, provider().tokenRequests.get());
    }

    @Test void oidcRejectsInvalidSignatureIssuerAudienceExpirationAndNonce() throws Exception {
        for (String defect : List.of("signature", "issuer", "audience", "expired", "nonce")) {
            var session = new MockHttpSession();
            String email = email();
            var result = callback(session, begin(session, "google", "login"), google(email), defect);
            assertEquals(new Result(302, "http://localhost:8088/#external-auth=login_failed"), result, defect);
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
            assertEquals(0, jdbc.queryForObject("select count(*) from users where email_normalized = ?", Integer.class, email));
        }
    }

    @Test void linkingIsBoundToTheAccountThatStartedTheFlowAndFailuresPreserveTheCurrentAccount() throws Exception {
        var first = new MockHttpSession();
        UUID firstId = register(first, email(), true);
        var attempt = begin(first, "github", "link");
        var second = new MockHttpSession();
        UUID secondId = register(second, email(), true);
        first.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            second.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        int before = provider().tokenRequests.get();
        assertEquals("http://localhost:8088/#external-auth=login_failed", callback(first, attempt, github(email(), true), "").location());
        assertEquals(before, provider().tokenRequests.get());
        assertEquals(secondId.toString(), me(first).get("id").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_identities where user_id = ?", Integer.class, firstId));
    }

    @Test void linkingRequiresVerifiedEmailRecentAuthenticationAndCsrf() throws Exception {
        var session = new MockHttpSession();
        UUID id = register(session, email(), false);
        assertEquals(409, postJson("/api/auth/providers/google/link", session, Map.of()).getResponse().getStatus());
        jdbc.update("update users set email_verified_at = current_timestamp where id = ?", id);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionIdentity(id, 0, Instant.now().minusSeconds(3600)), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        var stale = postJson("/api/auth/providers/google/link", session, Map.of());
        assertEquals(409, stale.getResponse().getStatus());
        assertEquals("session_expired", json.readTree(stale.getResponse().getContentAsString()).get("code").asText());
        mvc.perform(post("/api/auth/providers/google/login").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/providers/google/unlink").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        assertEquals(401, postJson("/api/auth/providers/google/link", new MockHttpSession(), Map.of()).getResponse().getStatus());
    }

    @Test void parallelExternalSignupsForOneEmailCreateExactlyOneAccount() throws Exception {
        String email = email();
        var googleSession = new MockHttpSession();
        var githubSession = new MockHttpSession();
        var googleAttempt = begin(googleSession, "google", "login");
        var githubAttempt = begin(githubSession, "github", "login");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> callback(googleSession, googleAttempt, google(email), ""));
            var second = executor.submit(() -> callback(githubSession, githubAttempt, github(email, true), ""));
            var results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(result -> result.location().endsWith("signed_in")).count());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from users where email_normalized = ?", Integer.class, email));
        assertEquals(1, jdbc.queryForObject("select count(*) from user_identities identity join users account on account.id = identity.user_id where account.email_normalized = ?", Integer.class, email));
    }
}
