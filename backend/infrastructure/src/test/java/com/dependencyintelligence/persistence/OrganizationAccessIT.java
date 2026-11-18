package com.dependencyintelligence.persistence;

import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.organization.infrastructure.mail.SmtpInvitationDelivery;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
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
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.enforce-runtime-role=true", "app.security.authentication.max-attempts-per-minute=1000"})
@AutoConfigureMockMvc
class OrganizationAccessIT extends PostgresIntegrationSupport {
    private static GenericContainer<?> mailpit;
    private static String inbox;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String PASSWORD = "organization integration passphrase 2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTransactions tenants;
    @Autowired AccountTokenCodec tokens;
    @MockitoSpyBean SmtpInvitationDelivery mail;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        database(registry, true);
        inbox = System.getenv("TEST_MAILPIT_URL");
        if (inbox == null || inbox.isBlank()) {
            mailpit = new GenericContainer<>("axllent/mailpit:v1.33.1").withExposedPorts(1025, 8025).withEnv("MP_DISABLE_VERSION_CHECK", "true");
            mailpit.start(); inbox = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        } else {
            registry.add("spring.mail.host", () -> "127.0.0.1");
            registry.add("spring.mail.port", () -> Integer.parseInt(System.getenv("TEST_SMTP_PORT")));
        }
        registry.add("app.mail.public-base-url", () -> "http://localhost:8088");
    }
    private record Person(UUID id, String email, MockHttpSession session) { }
    private record Invitation(UUID id, String token) { }
    private MvcResult postJson(String path, MockHttpSession session, Map<String, ?> body) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        return mvc.perform(post(path).servletPath(path).session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
    }
    private JsonNode ok(int expected, MvcResult result) throws Exception {
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return result.getResponse().getContentAsString().isBlank() ? json.nullNode() : json.readTree(result.getResponse().getContentAsString());
    }
    private Person person(boolean verified) throws Exception {
        String email = "team-" + UUID.randomUUID() + "@example.test";
        var session = new MockHttpSession();
        UUID id = UUID.fromString(ok(201, postJson("/api/auth/register", session, Map.of("email", email, "displayName", "Team fixture", "password", PASSWORD))).path("id").asText());
        if (verified) jdbc.update("update users set email_verified_at = current_timestamp where id = ?", id);
        return new Person(id, email, session);
    }
    private UUID organization(Person owner) throws Exception { return UUID.fromString(ok(201, postJson("/api/organizations", owner.session(), Map.of("name", "Organization access fixture"))).path("id").asText()); }
    private String path(UUID org) { return "/api/organizations/" + org; }
    private JsonNode overview(Person person, UUID org) throws Exception {
        return ok(200, mvc.perform(get(path(org)+"/access").session(person.session())).andReturn());
    }
    private UUID membership(Person owner, UUID org, Person target) throws Exception {
        for (var member : overview(owner, org).path("members")) if (member.path("userId").asText().equals(target.id().toString())) return UUID.fromString(member.path("id").asText());
        throw new AssertionError("Membership missing");
    }
    private JsonNode inbox(String suffix) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(inbox + suffix)).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return json.readTree(response.body());
    }
    private Invitation invite(Person owner, UUID org, Person target, String role) throws Exception {
        var response = postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email().toUpperCase(Locale.ROOT), "role", role));
        var result = ok(201, response);
        assertFalse(response.getResponse().getContentAsString().contains("token"));
        UUID id = UUID.fromString(result.path("id").asText());
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        var pattern = Pattern.compile("#organization=" + org + "&invitation=" + id + "&invite-token=([A-Za-z0-9_-]{43})");
        while (System.nanoTime() < deadline) {
            for (var message : inbox("/api/v1/search?query=" + URLEncoder.encode("to:" + target.email(), StandardCharsets.UTF_8)).path("messages")) {
                String text = inbox("/api/v1/message/" + message.path("ID").asText()).path("Text").asText();
                var matcher = pattern.matcher(text);
                if (matcher.find()) return new Invitation(id, matcher.group(1));
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Invitation not delivered by SMTP");
    }
    private MvcResult accept(Person person, UUID org, Invitation invitation) throws Exception { return postJson(path(org)+"/invitations/"+invitation.id()+"/accept", person.session(), Map.of("token", invitation.token())); }
    private UUID join(Person owner, UUID org, Person target, String role) throws Exception { var invitation = invite(owner, org, target, role); ok(204, accept(target, org, invitation)); return membership(owner, org, target); }
    private MvcResult change(Person actor, UUID org, UUID member, String role, String status) throws Exception { return postJson(path(org)+"/members/"+member, actor.session(), Map.of("role", role, "status", status)); }

    @Test void smtpInvitationRequiresMatchingVerifiedAccountAndIsConsumedOnce() throws Exception {
        var owner = person(true); var target = person(false); var wrong = person(true); var org = organization(owner);
        var invitation = invite(owner, org, target, "MEMBER");
        String hash = tenants.execute(org, () -> jdbc.queryForObject("select token_hash from membership_invitations where id = ?", String.class, invitation.id()));
        assertEquals(tokens.fingerprint(invitation.token()), hash); assertNotEquals(invitation.token(), hash);
        assertFalse(overview(owner, org).toString().contains(hash));
        ok(409, accept(wrong, org, invitation)); ok(409, accept(target, org, invitation));
        jdbc.update("update users set email_verified_at = current_timestamp where id = ?", target.id());
        ok(204, accept(target, org, invitation)); ok(409, accept(target, org, invitation));
        assertEquals(1, ok(200, mvc.perform(get("/api/organizations").session(target.session())).andReturn()).size());
        for (var member : overview(owner, org).path("members")) if (member.path("userId").asText().equals(target.id().toString())) assertEquals("MEMBER", member.path("roles").get(0).asText());
        ok(409, postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email(), "role", "MEMBER")));
        assertEquals(1, tenants.execute(org, () -> jdbc.queryForObject("select count(*) from audit_events where organization_id = ? and action = 'invitation.accept'", Integer.class, org)));
    }
    @Test void revokedExpiredTamperedAndDuplicateInvitationsCannotGrantAccess() throws Exception {
        var owner = person(true); var target = person(true); var org = organization(owner);
        var first = invite(owner, org, target, "VIEWER");
        ok(409, postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email(), "role", "VIEWER")));
        var tampered = new Invitation(first.id(), tokens.generate()); ok(409, accept(target, org, tampered));
        ok(204, postJson(path(org)+"/invitations/"+first.id()+"/revoke", owner.session(), Map.of())); ok(409, accept(target, org, first));
        var second = invite(owner, org, target, "MEMBER");
        tenants.execute(org, () -> jdbc.update("update membership_invitations set created_at = current_timestamp - interval '2 days', expires_at = current_timestamp - interval '1 day' where id = ?", second.id()));
        ok(409, accept(target, org, second));
        var replacement = invite(owner, org, target, "VIEWER"); ok(204, accept(target, org, replacement));
        assertTrue(overview(owner, org).path("invitations").toString().contains("EXPIRED"));
    }
    @Test void rolesProtectOwnersAdministratorsAndTheLastActiveOwner() throws Exception {
        var owner = person(true); var admin = person(true); var member = person(true); var org = organization(owner);
        UUID ownerMember = membership(owner, org, owner), adminMember = join(owner, org, admin, "ADMIN"), memberId = join(owner, org, member, "MEMBER");
        ok(409, change(owner, org, ownerMember, "MEMBER", "ACTIVE")); ok(409, change(owner, org, ownerMember, "OWNER", "SUSPENDED"));
        ok(403, change(admin, org, ownerMember, "VIEWER", "ACTIVE")); ok(403, change(admin, org, adminMember, "MEMBER", "ACTIVE"));
        ok(403, change(admin, org, memberId, "ADMIN", "ACTIVE"));
        ok(403, postJson(path(org)+"/invitations", admin.session(), Map.of("email", "denied@example.test", "role", "OWNER")));
        ok(204, change(admin, org, memberId, "VIEWER", "SUSPENDED")); ok(204, change(admin, org, memberId, "MEMBER", "ACTIVE"));
        mvc.perform(get(path(org)+"/access").session(member.session())).andExpect(status().isForbidden());
        ok(403, postJson(path(org)+"/teams", member.session(), Map.of("name", "Escalation")));
        ok(400, change(owner, org, memberId, "SUPERUSER", "ACTIVE"));
    }
    @Test void teamPermissionsAreIntersectedWithRolesAndRevocationsApplyToRealRequests() throws Exception {
        var owner = person(true); var member = person(true); var org = organization(owner);
        UUID membership = join(owner, org, member, "VIEWER");
        UUID repository = UUID.fromString(ok(201, postJson(path(org)+"/repositories", owner.session(), Map.of("provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "fixture/permissions", "defaultBranch", "main"))).path("id").asText());
        UUID team = UUID.fromString(ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Readers"))).path("id").asText());
        String teamPath = path(org)+"/teams/"+team, repositoryPath = path(org)+"/repositories/"+repository;
        ok(204, postJson(teamPath+"/members/"+membership, owner.session(), Map.of("included", true)));
        ok(204, postJson(teamPath+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read", "scan.read", "scan.create", "repository.manage"))));
        mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isOk());
        ok(403, postJson(path(org)+"/github/installations", member.session(), Map.of()));
        ok(403, postJson(repositoryPath+"/scans", member.session(), Map.of("gitReference", "main")));
        ok(204, change(owner, org, membership, "MEMBER", "ACTIVE")); ok(202, postJson(repositoryPath+"/scans", member.session(), Map.of("gitReference", "main")));
        ok(400, postJson(teamPath+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("organization.manage"))));
        ok(204, change(owner, org, membership, "MEMBER", "SUSPENDED")); mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isForbidden());
        ok(409, postJson(teamPath+"/members/"+membership, owner.session(), Map.of("included", true)));
        ok(204, change(owner, org, membership, "MEMBER", "ACTIVE")); mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isOk());
        ok(204, postJson(teamPath+"/members/"+membership, owner.session(), Map.of("included", false))); mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isForbidden());
        ok(204, postJson(teamPath+"/members/"+membership, owner.session(), Map.of("included", true)));
        ok(204, postJson(teamPath+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of()))); mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isForbidden());
        ok(204, postJson(teamPath+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read"))));
        ok(204, postJson(teamPath, owner.session(), Map.of("name", "Renamed readers"))); assertEquals("Renamed readers", overview(owner, org).path("teams").get(0).path("name").asText());
        ok(204, postJson(teamPath+"/delete", owner.session(), Map.of())); mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isForbidden());
        ok(204, postJson(path(org)+"/members/"+membership+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read"))));
        mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isOk());
        ok(204, postJson(path(org)+"/members/"+membership+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of())));
        mvc.perform(get(repositoryPath).session(member.session())).andExpect(status().isForbidden());
        assertEquals(0, overview(owner, org).path("teams").size());
    }
    @Test void tenantBoundariesCsrfAndAuthenticationProtectAllManagementEntryPoints() throws Exception {
        var owner = person(true); var other = person(true); var org = organization(owner); var foreignOrg = organization(other);
        UUID foreignMember = membership(other, foreignOrg, other);
        UUID foreignTeam = UUID.fromString(ok(201, postJson(path(foreignOrg)+"/teams", other.session(), Map.of("name", "Private"))).path("id").asText());
        UUID localTeam = UUID.fromString(ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Local"))).path("id").asText());
        mvc.perform(get(path(org)+"/access").session(other.session())).andExpect(status().isForbidden());
        mvc.perform(get(path(org)+"/access")).andExpect(status().isUnauthorized());
        mvc.perform(post(path(org)+"/teams").session(owner.session()).contentType("application/json").content("{\"name\":\"No csrf\"}")).andExpect(status().isForbidden());
        ok(404, postJson(path(org)+"/teams/"+foreignTeam, owner.session(), Map.of("name", "Intrusion")));
        ok(404, postJson(path(org)+"/teams/"+localTeam+"/members/"+foreignMember, owner.session(), Map.of("included", true)));
        ok(404, postJson(path(org)+"/teams/"+localTeam+"/repositories/"+UUID.randomUUID()+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read"))));
        ok(409, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Local")));
        assertEquals("Private", overview(other, foreignOrg).path("teams").get(0).path("name").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from organization_teams", Integer.class));
    }
    @Test void concurrentOwnerDemotionsPreserveOneOwner() throws Exception {
        var first = person(true); var second = person(true); var org = organization(first);
        UUID firstMember = membership(first, org, first), secondMember = join(first, org, second, "OWNER");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return change(first, org, firstMember, "MEMBER", "ACTIVE").getResponse().getStatus(); });
            var b = executor.submit(() -> { start.await(); return change(second, org, secondMember, "MEMBER", "ACTIVE").getResponse().getStatus(); });
            start.countDown(); var statuses = new ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))); Collections.sort(statuses);
            assertEquals(List.of(204, 409), statuses);
        }
        assertEquals(1, tenants.execute(org, () -> jdbc.queryForObject("select count(*) from membership_roles mr join roles r on r.id = mr.role_id where mr.organization_id = ? and r.code = 'OWNER'", Integer.class, org)));
    }
    @Test void concurrentAcceptanceIsAtomicAndSmtpFailureRollsBackInvitation() throws Exception {
        var owner = person(true); var target = person(true); var org = organization(owner); var invitation = invite(owner, org, target, "MEMBER");
        var anotherSession = new MockHttpSession();
        ok(200, postJson("/api/auth/login", anotherSession, Map.of("email", target.email(), "password", PASSWORD)));
        var sameUser = new Person(target.id(), target.email(), anotherSession); var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return accept(target, org, invitation).getResponse().getStatus(); });
            var b = executor.submit(() -> { start.await(); return accept(sameUser, org, invitation).getResponse().getStatus(); });
            start.countDown(); var statuses = new ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))); Collections.sort(statuses); assertEquals(List.of(204, 409), statuses);
        }
        String failedEmail = "failed-" + UUID.randomUUID() + "@example.test";
        doThrow(new AccountMailUnavailableException()).when(mail).send(eq(failedEmail), anyString(), any(), any(), anyString());
        ok(503, postJson(path(org)+"/invitations", owner.session(), Map.of("email", failedEmail, "role", "MEMBER")));
        assertEquals(0, tenants.execute(org, () -> jdbc.queryForObject("select count(*) from membership_invitations where email_normalized = ?", Integer.class, failedEmail)));
        assertEquals(2, overview(owner, org).path("members").size());
    }
    @Test void inviterDemotionInvalidatesPrivilegeAndReturningMembersLoseOldTeamAccess() throws Exception {
        var owner = person(true); var admin = person(true); var target = person(true); var org = organization(owner);
        UUID adminMember = join(owner, org, admin, "ADMIN"); var pending = invite(admin, org, target, "MEMBER");
        ok(204, change(owner, org, adminMember, "MEMBER", "ACTIVE")); ok(403, accept(target, org, pending));
        ok(204, postJson(path(org)+"/invitations/"+pending.id()+"/revoke", owner.session(), Map.of()));
        UUID membership = join(owner, org, target, "MEMBER");
        UUID team = UUID.fromString(ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Old team"))).path("id").asText());
        ok(204, postJson(path(org)+"/teams/"+team+"/members/"+membership, owner.session(), Map.of("included", true)));
        ok(204, change(owner, org, membership, "MEMBER", "LEFT"));
        var returning = invite(owner, org, target, "VIEWER"); ok(204, accept(target, org, returning));
        var data = overview(owner, org); assertEquals(0, data.path("teams").get(0).path("membershipIds").size());
        for (var member : data.path("members")) if (member.path("userId").asText().equals(target.id().toString())) assertEquals("VIEWER", member.path("roles").get(0).asText());
    }
}
