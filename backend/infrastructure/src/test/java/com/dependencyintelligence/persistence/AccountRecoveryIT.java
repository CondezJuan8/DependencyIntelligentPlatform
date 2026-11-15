package com.dependencyintelligence.persistence;

import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.domain.AccountTokenPurpose;
import com.dependencyintelligence.identity.infrastructure.mail.SmtpAccountMailAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.enforce-runtime-role=true", "app.security.authentication.max-attempts-per-minute=1000"})
@AutoConfigureMockMvc
class AccountRecoveryIT extends PostgresIntegrationSupport {
    private static GenericContainer<?> mailpit;
    private static String inboxUrl;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String PASSWORD = "original test passphrase 2026";
    private static final String NEW_PASSWORD = "replacement test passphrase 2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountTokenCodec tokens;
    @Autowired PasswordHashing passwords;
    @MockitoSpyBean SmtpAccountMailAdapter mail;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        database(registry, true);
        inboxUrl = System.getenv("TEST_MAILPIT_URL");
        if (inboxUrl == null || inboxUrl.isBlank()) {
            mailpit = new GenericContainer<>("axllent/mailpit:v1.33.1").withExposedPorts(1025, 8025)
                .withEnv("MP_DISABLE_VERSION_CHECK", "true");
            mailpit.start();
            inboxUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        } else {
            registry.add("spring.mail.host", () -> "127.0.0.1");
            registry.add("spring.mail.port", () -> Integer.parseInt(System.getenv("TEST_SMTP_PORT")));
        }
        registry.add("app.mail.public-base-url", () -> "http://localhost:8088");
    }

    private String email() { return "recovery-" + UUID.randomUUID() + "@example.test"; }

    private MvcResult postJson(String path, MockHttpSession session, Map<String, ?> body) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        return mvc.perform(post(path).session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
    }

    private UUID register(MockHttpSession session, String email) throws Exception {
        var result = postJson("/api/auth/register", session, Map.of("email", email, "displayName", "Recovery fixture", "password", PASSWORD));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode api(String path) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(inboxUrl + path)).timeout(Duration.ofSeconds(3)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return json.readTree(response.body());
    }

    private JsonNode messages(String email) throws Exception {
        return api("/api/v1/search?query=" + URLEncoder.encode("to:" + email, StandardCharsets.UTF_8)).path("messages");
    }

    private String deliveredToken(String email, String action, String excluded) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        Pattern pattern = Pattern.compile("#" + action + "=([A-Za-z0-9_-]{43})");
        while (System.nanoTime() < deadline) {
            for (var message : messages(email)) {
                var body = api("/api/v1/message/" + message.path("ID").asText()).path("Text").asText();
                var matcher = pattern.matcher(body);
                if (matcher.find() && !matcher.group(1).equals(excluded)) {
                    String token = matcher.group(1);
                    assertTrue(body.contains("http://localhost:8088/#"));
                    if (jdbc.queryForObject("select count(*) from user_access_tokens where token_hash = ?", Integer.class,
                            tokens.fingerprint(token)) == 1) return token;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("No committed account link was received through SMTP");
    }

    @Test void verificationUsesRealSmtpAndConsumesOnlyTheCorrectPurpose() throws Exception {
        var session = new MockHttpSession();
        String email = email();
        UUID id = register(session, email);
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        String token = deliveredToken(email, "verify-email", null);
        String hash = jdbc.queryForObject("select token_hash from user_access_tokens where user_id = ?", String.class, id);
        assertEquals(tokens.fingerprint(token), hash);
        assertNotEquals(token, hash);
        assertEquals(400, postJson("/api/auth/password-reset/confirm", new MockHttpSession(),
            Map.of("token", token, "password", NEW_PASSWORD)).getResponse().getStatus());
        assertEquals(204, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", token)).getResponse().getStatus());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        assertEquals(400, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", token)).getResponse().getStatus());
    }

    @Test void passwordResetIsUniformAndInvalidatesAllPreviousSessions() throws Exception {
        String email = email();
        var first = new MockHttpSession();
        UUID id = register(first, email);
        var second = new MockHttpSession();
        assertEquals(200, postJson("/api/auth/login", second, Map.of("email", email, "password", PASSWORD)).getResponse().getStatus());
        var unknown = postJson("/api/auth/password-reset/request", new MockHttpSession(), Map.of("email", email()));
        var known = postJson("/api/auth/password-reset/request", new MockHttpSession(), Map.of("email", email.toUpperCase()));
        assertEquals(202, known.getResponse().getStatus());
        assertEquals(known.getResponse().getStatus(), unknown.getResponse().getStatus());
        assertEquals(known.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        String token = deliveredToken(email, "reset-password", null);
        assertEquals(400, postJson("/api/auth/password-reset/confirm", new MockHttpSession(), Map.of("token", token, "password", "weak")).getResponse().getStatus());
        assertEquals(204, postJson("/api/auth/password-reset/confirm", new MockHttpSession(), Map.of("token", token, "password", NEW_PASSWORD)).getResponse().getStatus());
        mvc.perform(get("/api/auth/me").session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized());
        assertEquals(1L, jdbc.queryForObject("select credential_version from users where id = ?", Long.class, id));
        assertEquals(401, postJson("/api/auth/login", new MockHttpSession(), Map.of("email", email, "password", PASSWORD)).getResponse().getStatus());
        assertEquals(200, postJson("/api/auth/login", new MockHttpSession(), Map.of("email", email, "password", NEW_PASSWORD)).getResponse().getStatus());
        assertEquals(400, postJson("/api/auth/password-reset/confirm", new MockHttpSession(), Map.of("token", token, "password", PASSWORD)).getResponse().getStatus());
    }

    @Test void expiredTokensCannotChangeAnAccountAndResendingRevokesThePreviousLink() throws Exception {
        String email = email();
        var session = new MockHttpSession();
        UUID id = register(session, email);
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        String first = deliveredToken(email, "verify-email", null);
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        assertEquals(1, messages(email).size());
        jdbc.update("update user_access_tokens set created_at = current_timestamp - interval '2 hours', expires_at = current_timestamp - interval '1 hour' where user_id = ?", id);
        assertEquals(400, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", first)).getResponse().getStatus());
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        String second = deliveredToken(email, "verify-email", first);
        assertNotEquals(first, second);
        assertEquals(400, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", first)).getResponse().getStatus());
        assertEquals(204, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", second)).getResponse().getStatus());
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        assertEquals(2, messages(email).size());
    }

    @Test void concurrentResetConfirmationsHaveExactlyOneWinner() throws Exception {
        String email = email();
        UUID id = register(new MockHttpSession(), email);
        assertEquals(202, postJson("/api/auth/password-reset/request", new MockHttpSession(), Map.of("email", email)).getResponse().getStatus());
        String token = deliveredToken(email, "reset-password", null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return postJson("/api/auth/password-reset/confirm", new MockHttpSession(),
                    Map.of("token", token, "password", NEW_PASSWORD)).getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return postJson("/api/auth/password-reset/confirm", new MockHttpSession(),
                    Map.of("token", token, "password", PASSWORD)).getResponse().getStatus();
            });
            start.countDown();
            assertEquals(List.of(204, 400), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
        assertEquals(1L, jdbc.queryForObject("select credential_version from users where id = ?", Long.class, id));
        String hash = jdbc.queryForObject("select password_hash from user_password_credentials where user_id = ?", String.class, id);
        assertNotEquals(passwords.matches(NEW_PASSWORD, hash), passwords.matches(PASSWORD, hash));
    }

    @Test void mailFailureRollsBackTokenReplacementAndPreservesThePreviousLink() throws Exception {
        String email = email();
        var session = new MockHttpSession();
        UUID id = register(session, email);
        assertEquals(202, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        String token = deliveredToken(email, "verify-email", null);
        jdbc.update("update user_access_tokens set created_at = current_timestamp - interval '2 minutes' where user_id = ?", id);
        doThrow(new AccountMailUnavailableException()).when(mail).sendLink(eq(email), eq(AccountTokenPurpose.VERIFY_EMAIL), anyString());
        assertEquals(503, postJson("/api/auth/email-verification/request", session, Map.of()).getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from user_access_tokens where user_id = ? and consumed_at is null", Integer.class, id));
        assertEquals(204, postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", token)).getResponse().getStatus());
    }

    @Test void verificationRequestsRequireSessionAndEveryMutationRequiresCsrf() throws Exception {
        assertEquals(401, postJson("/api/auth/email-verification/request", new MockHttpSession(), Map.of()).getResponse().getStatus());
        for (String endpoint : List.of("password-reset/request", "password-reset/confirm", "email-verification/confirm")) {
            mvc.perform(post("/api/auth/" + endpoint).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        }
        var malformed = postJson("/api/auth/email-verification/confirm", new MockHttpSession(), Map.of("token", "invalid-token"));
        assertEquals(400, malformed.getResponse().getStatus());
        assertFalse(malformed.getResponse().getContentAsString().contains("invalid-token"));
    }
}
