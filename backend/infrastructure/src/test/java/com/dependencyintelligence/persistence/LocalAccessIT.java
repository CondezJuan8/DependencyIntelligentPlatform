package com.dependencyintelligence.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real runtime login and normal HTTP/CSRF/session entry points, with narrowly scoped SQL fixtures. */
@SpringBootTest(properties = {"app.database.enforce-runtime-role=true", "app.security.authentication.max-attempts-per-minute=1000"})
@AutoConfigureMockMvc
class LocalAccessIT extends PostgresIntegrationSupport {
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { database(registry, true, LocalAccessIT.class.getSimpleName()); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTransactions tenants;
    private static final String PASSWORD = "a long test passphrase 2026";

    private MvcResult postJson(String path, MockHttpSession session, Map<String, ?> body) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode token = json.readTree(csrf.getResponse().getContentAsString());
        return mvc.perform(post(path).session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
    }

    private String email() { return "user-" + UUID.randomUUID() + "@example.test"; }

    private UUID register(MockHttpSession session, String email) throws Exception {
        var result = postJson("/api/auth/register", session, Map.of("email", email, "displayName", "Local user", "password", PASSWORD));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertFalse(result.getResponse().getContentAsString().contains("password"));
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID organization(MockHttpSession session, String name) throws Exception {
        var result = postJson("/api/organizations", session, Map.of("name", name));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void approveOwner(UUID userId) {
        jdbc.update("update users set email_verified_at = coalesce(email_verified_at, current_timestamp), admission_status = 'APPROVED' where id = ?", userId);
    }

    @Test void registrationLoginLogoutAndRevokedAccountsUseRealSessions() throws Exception {
        String email = email();
        var session = new MockHttpSession();
        String originalId = session.getId();
        UUID userId = register(session, email);
        assertNotEquals(originalId, session.getId());
        String hash = jdbc.queryForObject("select password_hash from user_password_credentials where user_id = ?", String.class, userId);
        assertNotEquals(PASSWORD, hash);
        assertTrue(hash.startsWith("{pbkdf2-sha256-600000}"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId.toString()));
        var duplicate = postJson("/api/auth/register", new MockHttpSession(), Map.of("email", email.toUpperCase(), "displayName", "Duplicate", "password", PASSWORD));
        assertEquals(409, duplicate.getResponse().getStatus());
        var invalid = postJson("/api/auth/login", new MockHttpSession(), Map.of("email", email, "password", "wrong password"));
        var unknown = postJson("/api/auth/login", new MockHttpSession(), Map.of("email", email(), "password", "wrong password"));
        assertEquals(401, invalid.getResponse().getStatus());
        assertEquals(invalid.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        var logout = postJson("/api/auth/logout", session, Map.of());
        assertEquals(204, logout.getResponse().getStatus());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        var login = new MockHttpSession();
        assertEquals(200, postJson("/api/auth/login", login, Map.of("email", email.toUpperCase(), "password", PASSWORD)).getResponse().getStatus());
        jdbc.update("update users set is_active = false where id = ?", userId);
        mvc.perform(get("/api/auth/me").session(login)).andExpect(status().isUnauthorized());
        assertTrue(login.isInvalid());
    }

    @Test void csrfIsRequiredAndRotatedAfterAuthentication() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(session).contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("email", email(), "displayName", "CSRF", "password", PASSWORD))))
            .andExpect(status().isForbidden());
        var before = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        JsonNode token = json.readTree(before.getResponse().getContentAsString());
        approveOwner(register(session, email()));
        mvc.perform(post("/api/organizations").session(session).header(token.get("headerName").asText(), token.get("token").asText())
            .contentType("application/json").content("{\"name\":\"stale token\"}"))
            .andExpect(status().isForbidden());
        organization(session, "Fresh token");
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test void usersDiscoverOnlyActiveOwnOrganizationsAndCannotCrossTenants() throws Exception {
        var first = new MockHttpSession();
        approveOwner(register(first, email()));
        var org = organization(first, "Private organization");
        var other = new MockHttpSession();
        register(other, email());
        mvc.perform(get("/api/organizations").session(first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/organizations").session(other)).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(0, jdbc.queryForObject("select count(*) from organizations", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from organization_memberships", Integer.class));
        assertEquals(403, postJson("/api/organizations/"+org+"/repositories", other, Map.of(
            "provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "test/repo", "defaultBranch", "main")).getResponse().getStatus());
        tenants.execute(org, () -> jdbc.update("update organization_memberships set status = 'SUSPENDED' where organization_id = ?", org));
        mvc.perform(get("/api/organizations").session(first)).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(403, postJson("/api/organizations/"+org+"/repositories", first, Map.of(
            "provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "test/repo", "defaultBranch", "main")).getResponse().getStatus());
    }

    @Test void repositoryActionsRequireRoleAndTeamPermissionsAndReflectRevocation() throws Exception {
        var owner = new MockHttpSession();
        approveOwner(register(owner, email()));
        var org = organization(owner, "Permission fixtures");
        var created = postJson("/api/organizations/"+org+"/repositories", owner, Map.of(
            "provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "fixture/repo", "defaultBranch", "main"));
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        var repo = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
        String path = "/api/organizations/"+org+"/repositories/"+repo;
        mvc.perform(get(path).session(owner)).andExpect(status().isOk());
        var viewer = new MockHttpSession();
        UUID userId = register(viewer, email());
        approveOwner(userId);
        UUID team = UUID.randomUUID();
        tenants.execute(org, () -> {
            UUID membership = jdbc.queryForObject("insert into organization_memberships(organization_id, user_id) values (?, ?) returning id", UUID.class, org, userId);
            jdbc.update("insert into membership_roles select ?, ?, id from roles where code = 'VIEWER'", org, membership);
            jdbc.update("insert into organization_teams(id, organization_id, name) values (?, ?, 'Readers')", team, org);
            jdbc.update("insert into team_memberships values (?, ?, ?)", org, team, membership);
            return null;
        });
        mvc.perform(get(path).session(viewer)).andExpect(status().isForbidden());
        tenants.execute(org, () -> jdbc.update("insert into repository_team_access select ?, ?, ?, id from permissions where code in ('repository.read','scan.create')", org, repo, team));
        mvc.perform(get(path).session(viewer)).andExpect(status().isOk());
        assertEquals(403, postJson(path+"/scans", viewer, Map.of("gitReference", "main")).getResponse().getStatus());
        tenants.execute(org, () -> jdbc.update("""
            update membership_roles set role_id = (select id from roles where code = 'MEMBER')
            where organization_id = ? and membership_id = (select id from organization_memberships where organization_id = ? and user_id = ?)
            """, org, org, userId));
        assertEquals(202, postJson(path+"/scans", viewer, Map.of("gitReference", "main")).getResponse().getStatus());
        tenants.execute(org, () -> jdbc.update("delete from repository_team_access where organization_id = ? and repository_id = ?", org, repo));
        mvc.perform(get(path).session(viewer)).andExpect(status().isForbidden());
        assertEquals(403, postJson(path+"/scans", viewer, Map.of("gitReference", "main")).getResponse().getStatus());
    }

    @Test void weakRegistrationDoesNotCreatePartialAccounts() throws Exception {
        String email = email();
        assertEquals(400, postJson("/api/auth/register", new MockHttpSession(), Map.of(
            "email", email, "displayName", "Weak", "password", "short")).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from users where email_normalized = ?", Integer.class, email));
        String rejectedPassword = "private-registration-input-".repeat(6);
        var invalidBody = postJson("/api/auth/register", new MockHttpSession(), Map.of(
            "email", email, "displayName", "Invalid", "password", rejectedPassword));
        assertEquals(400, invalidBody.getResponse().getStatus());
        assertFalse(invalidBody.getResponse().getContentAsString().contains(rejectedPassword));
        assertEquals(0, jdbc.queryForObject("select count(*) from users where email_normalized = ?", Integer.class, email));
    }
}
