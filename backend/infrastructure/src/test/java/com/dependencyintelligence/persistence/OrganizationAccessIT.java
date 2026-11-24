package com.dependencyintelligence.persistence;

import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.organization.infrastructure.mail.SmtpInvitationDelivery;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import com.dependencyintelligence.repository.infrastructure.storage.RepositorySourceService;
import com.dependencyintelligence.scan.infrastructure.job.AnalysisJobWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.enforce-runtime-role=true", "app.security.authentication.max-attempts-per-minute=1000",
    "app.analysis-worker.poll-delay-ms=3600000"})
@AutoConfigureMockMvc
class OrganizationAccessIT extends PostgresIntegrationSupport {
    private static final Path LOCAL_REPOSITORY_STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "di-local-repository-it-" + UUID.randomUUID());
    private static GenericContainer<?> mailpit;
    private static String inbox;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String PASSWORD = "organization integration passphrase 2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTransactions tenants;
    @Autowired RepositorySourceService sourceService;
    @Autowired AnalysisJobWorker analysisWorker;
    @Autowired AccountTokenCodec tokens;
    @MockitoSpyBean SmtpInvitationDelivery mail;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        database(registry, true, OrganizationAccessIT.class.getSimpleName());
        inbox = System.getenv("TEST_MAILPIT_URL");
        if (inbox == null || inbox.isBlank()) {
            mailpit = new GenericContainer<>("axllent/mailpit:v1.33.1").withExposedPorts(1025, 8025).withEnv("MP_DISABLE_VERSION_CHECK", "true");
            mailpit.start(); inbox = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        } else {
            registry.add("spring.mail.host", () -> System.getenv().getOrDefault("TEST_SMTP_HOST", "127.0.0.1"));
            registry.add("spring.mail.port", () -> Integer.parseInt(System.getenv("TEST_SMTP_PORT")));
        }
        registry.add("app.mail.public-base-url", () -> "http://localhost:8088");
        registry.add("app.local-repositories.storage-root", LOCAL_REPOSITORY_STORAGE::toString);
    }

    @org.junit.jupiter.api.AfterAll static void removeLocalRepositoryFixture() throws Exception {
        if (Files.exists(LOCAL_REPOSITORY_STORAGE)) {
            try (var paths = Files.walk(LOCAL_REPOSITORY_STORAGE)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private record Person(UUID id, String email, MockHttpSession session) { }
    private record Invitation(UUID id, String token) { }
    private record InvitationTarget(UUID teamId, UUID repositoryId) { }
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
        if (verified) jdbc.update("update users set email_verified_at = current_timestamp, admission_status = 'APPROVED' where id = ?", id);
        return new Person(id, email, session);
    }
    private UUID organization(Person owner) throws Exception { return UUID.fromString(ok(201, postJson("/api/organizations", owner.session(), Map.of("name", "Organization access fixture"))).path("id").asText()); }
    private String path(UUID org) { return "/api/organizations/" + org; }

    @Test void localFolderImportPersistsPrivateArchiveAndOrganizationRepository() throws Exception {
        var owner = person(true);
        UUID org = organization(owner);
        var csrfResponse = mvc.perform(get("/api/auth/csrf").session(owner.session())).andExpect(status().isOk()).andReturn();
        var csrf = json.readTree(csrfResponse.getResponse().getContentAsString());
        String mavenSource = """
            <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>local-fixture</artifactId><version>1.0.0</version>
              <dependencies><dependency><groupId>org.example</groupId><artifactId>core</artifactId><version>1.2.3</version></dependency></dependencies>
            </project>
            """;
        String npmSource = """
            {"name":"local-fixture","version":"1.0.0","lockfileVersion":3,"requires":true,"packages":{
              "":{"name":"local-fixture","version":"1.0.0","dependencies":{"axios":"^1.7.0"}},
              "node_modules/axios":{"version":"1.7.2"}
            }}
            """;
        var upload = multipart(path(org) + "/repositories/local-folder");
        upload.file(new MockMultipartFile("files", "pom.xml", "application/xml", mavenSource.getBytes(StandardCharsets.UTF_8)));
        upload.file(new MockMultipartFile("files", "package-lock.json", "application/json", npmSource.getBytes(StandardCharsets.UTF_8)));
        upload.param("name", "local-fixture");
        upload.param("paths", "local-fixture/pom.xml", "local-fixture/package-lock.json");
        upload.session(owner.session());
        upload.header(csrf.path("headerName").asText(), csrf.path("token").asText());
        upload.servletPath(path(org) + "/repositories/local-folder");
        var result = mvc.perform(upload)
            .andExpect(status().isOk()).andReturn();
        JsonNode imported = json.readTree(result.getResponse().getContentAsString());
        UUID repository = UUID.fromString(imported.path("id").asText());
        assertEquals("local-fixture", imported.path("name").asText());
        assertEquals(2, imported.path("fileCount").asInt());
        assertEquals("local", tenants.execute(org, () -> jdbc.queryForObject("select provider from repositories where organization_id=? and id=?", String.class, org, repository)));
        String storageKey = tenants.execute(org, () -> jdbc.queryForObject("select storage_key from local_repository_sources where organization_id=? and repository_id=?", String.class, org, repository));
        Path archive = LOCAL_REPOSITORY_STORAGE.resolve(storageKey).normalize();
        assertTrue(archive.startsWith(LOCAL_REPOSITORY_STORAGE) && Files.isRegularFile(archive));
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
            var entries = new HashSet<String>();
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) entries.add(entry.getName());
            assertEquals(Set.of("pom.xml", "package-lock.json"), entries);
        }
        try (var snapshot = sourceService.prepare(sourceService.locate(org, repository))) {
            assertEquals("local", snapshot.provider());
            assertEquals(archive, snapshot.archive());
            assertEquals(64, snapshot.sha256().length());
        }
        var listing = mvc.perform(get(path(org) + "/repositories/local-folders").session(owner.session()))
            .andExpect(status().isOk()).andReturn();
        assertEquals(repository.toString(), json.readTree(listing.getResponse().getContentAsString()).get(0).path("id").asText());

        var workspace = mvc.perform(get("/api/account/workspace").session(owner.session())).andExpect(status().isOk()).andReturn();
        assertTrue(java.util.stream.StreamSupport.stream(json.readTree(workspace.getResponse().getContentAsString()).spliterator(), false)
            .anyMatch(item -> repository.toString().equals(item.path("id").asText())), "OWNER sees its enabled local repository in Inventory");
        mvc.perform(get(path(org) + "/repositories/" + repository + "/maven-analysis").session(owner.session()))
            .andExpect(status().isOk());
        mvc.perform(get(path(org) + "/repositories/" + repository + "/npm-analysis").session(owner.session()))
            .andExpect(status().isOk());

        String jobPath = path(org) + "/repositories/" + repository + "/dependency-analysis";
        var jobCsrfResponse = mvc.perform(get("/api/auth/csrf").session(owner.session())).andExpect(status().isOk()).andReturn();
        JsonNode jobCsrf = json.readTree(jobCsrfResponse.getResponse().getContentAsString());
        var requested = mvc.perform(post(jobPath).servletPath(jobPath).session(owner.session())
            .header(jobCsrf.path("headerName").asText(), jobCsrf.path("token").asText())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content("{}"))
            .andExpect(status().isAccepted()).andReturn();
        UUID jobId = UUID.fromString(json.readTree(requested.getResponse().getContentAsString()).path("jobId").asText());
        mvc.perform(get(jobPath + "/active").session(owner.session()).servletPath(jobPath + "/active"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(jobId.toString()));
        mvc.perform(get(jobPath + "/" + jobId).session(owner.session()).servletPath(jobPath + "/" + jobId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(jobId.toString()));
        var cancelCsrfResponse = mvc.perform(get("/api/auth/csrf").session(owner.session())).andExpect(status().isOk()).andReturn();
        JsonNode cancelCsrf = json.readTree(cancelCsrfResponse.getResponse().getContentAsString());
        String cancelPath = jobPath + "/" + jobId;
        mvc.perform(delete(cancelPath).servletPath(cancelPath).session(owner.session())
            .header(cancelCsrf.path("headerName").asText(), cancelCsrf.path("token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        var secondRequest = mvc.perform(post(jobPath).servletPath(jobPath).session(owner.session())
            .header(jobCsrf.path("headerName").asText(), jobCsrf.path("token").asText())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content("{}"))
            .andExpect(status().isAccepted()).andReturn();
        UUID completedJobId = UUID.fromString(json.readTree(secondRequest.getResponse().getContentAsString()).path("jobId").asText());
        analysisWorker.poll();
        var finished = mvc.perform(get(jobPath + "/" + completedJobId).session(owner.session()).servletPath(jobPath + "/" + completedJobId))
            .andExpect(status().isOk()).andReturn();
        JsonNode finishedStatus = json.readTree(finished.getResponse().getContentAsString());
        assertEquals("COMPLETED", finishedStatus.path("status").asText());
        assertEquals(3, finishedStatus.path("completedStages").size());
        assertEquals(2, finishedStatus.path("scans").size());
        UUID mavenScanId = null;
        UUID npmScanId = null;
        for (var scan : finishedStatus.path("scans")) {
            assertEquals(1, scan.path("dependencyCount").asInt());
            if ("MAVEN".equals(scan.path("ecosystem").asText())) mavenScanId = UUID.fromString(scan.path("scanId").asText());
            if ("NPM".equals(scan.path("ecosystem").asText())) npmScanId = UUID.fromString(scan.path("scanId").asText());
        }
        assertNotNull(mavenScanId);
        assertNotNull(npmScanId);
        var mavenInventory = mvc.perform(get(path(org) + "/repositories/" + repository + "/maven-analysis")
            .param("scanId", mavenScanId.toString()).session(owner.session())).andExpect(status().isOk()).andReturn();
        var npmInventory = mvc.perform(get(path(org) + "/repositories/" + repository + "/npm-analysis")
            .param("scanId", npmScanId.toString()).session(owner.session())).andExpect(status().isOk()).andReturn();
        JsonNode mavenResult = json.readTree(mavenInventory.getResponse().getContentAsString());
        JsonNode npmResult = json.readTree(npmInventory.getResponse().getContentAsString());
        assertEquals(1, mavenResult.path("dependencies").size());
        assertEquals("core", mavenResult.path("dependencies").get(0).path("coordinates").path("name").asText());
        assertEquals(1, npmResult.path("dependencies").size());
        assertEquals("axios", npmResult.path("dependencies").get(0).path("coordinates").path("name").asText());
    }
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
        var destination = invitationTarget(owner, org);
        return invite(owner, org, target, role, destination.teamId(), destination.repositoryId());
    }
    private InvitationTarget invitationTarget(Person owner, UUID org) throws Exception {
        UUID repository = UUID.fromString(ok(201, postJson(path(org)+"/repositories", owner.session(), Map.of(
            "provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "fixture/invitation-" + UUID.randomUUID(), "defaultBranch", "main"))).path("id").asText());
        JsonNode teamResponse = ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Invite " + UUID.randomUUID())));
        UUID team = UUID.fromString(teamResponse.path("id").asText());
        ok(204, postJson(path(org)+"/teams/"+team+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read"))));
        return new InvitationTarget(team, repository);
    }
    private Invitation invite(Person owner, UUID org, Person target, String role, UUID team, UUID repository) throws Exception {
        ok(204, postJson(path(org)+"/teams/"+team+"/repositories/"+repository+"/permissions", owner.session(), Map.of("permissions", List.of("repository.read"))));
        var response = postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email().toUpperCase(Locale.ROOT), "role", role, "teamId", team, "repositoryId", repository));
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
    private UUID join(Person owner, UUID org, Person target, String role) throws Exception {
        var invitation = invite(owner, org, target, invitationRole(role));
        return promoteIfNeeded(owner, org, join(owner, org, target, invitation), role);
    }
    private UUID join(Person owner, UUID org, Person target, String role, UUID team, UUID repository) throws Exception {
        var invitation = invite(owner, org, target, invitationRole(role), team, repository);
        return promoteIfNeeded(owner, org, join(owner, org, target, invitation), role);
    }
    private static String invitationRole(String role) { return "OWNER".equals(role) || "ADMIN".equals(role) ? "MEMBER" : role; }
    private UUID promoteIfNeeded(Person owner, UUID org, UUID membership, String role) throws Exception {
        if (!invitationRole(role).equals(role)) ok(204, change(owner, org, membership, role, "ACTIVE"));
        return membership;
    }
    private UUID join(Person owner, UUID org, Person target, Invitation invitation) throws Exception {
        ok(204, accept(target, org, invitation));
        ok(204, postJson(path(org)+"/invitations/"+invitation.id()+"/approve", owner.session(), Map.of()));
        return membership(owner, org, target);
    }
    private MvcResult change(Person actor, UUID org, UUID member, String role, String status) throws Exception { return postJson(path(org)+"/members/"+member, actor.session(), Map.of("role", role, "status", status)); }

    @Test void smtpInvitationRequiresMatchingVerifiedAccountAndIsConsumedOnce() throws Exception {
        var owner = person(true); var target = person(false); var wrong = person(true); var org = organization(owner);
        var invitation = invite(owner, org, target, "MEMBER");
        String hash = tenants.execute(org, () -> jdbc.queryForObject("select token_hash from membership_invitations where id = ?", String.class, invitation.id()));
        assertEquals(tokens.fingerprint(invitation.token()), hash); assertNotEquals(invitation.token(), hash);
        assertFalse(overview(owner, org).toString().contains(hash));
        ok(409, accept(wrong, org, invitation)); ok(409, accept(target, org, invitation));
        jdbc.update("update users set email_verified_at = current_timestamp, admission_status = 'APPROVED' where id = ?", target.id());
        ok(204, accept(target, org, invitation)); ok(409, accept(target, org, invitation));
        assertEquals(0, ok(200, mvc.perform(get("/api/organizations").session(target.session())).andReturn()).size(),
            "Accepting an invitation waits for explicit owner approval");
        ok(204, postJson(path(org)+"/invitations/"+invitation.id()+"/approve", owner.session(), Map.of()));
        assertEquals(1, ok(200, mvc.perform(get("/api/organizations").session(target.session())).andReturn()).size());
        for (var member : overview(owner, org).path("members")) if (member.path("userId").asText().equals(target.id().toString())) assertEquals("MEMBER", member.path("roles").get(0).asText());
        var duplicateTarget = invitationTarget(owner, org);
        ok(409, postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email(), "role", "MEMBER",
            "teamId", duplicateTarget.teamId(), "repositoryId", duplicateTarget.repositoryId())));
        var invitationAudit = tenants.execute(org, () -> {
            jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, target.id().toString());
            return jdbc.queryForList("select actor_id::text, action from audit_events where organization_id = ? order by occurred_at", org);
        });
        assertTrue(invitationAudit.stream().anyMatch(event -> target.id().toString().equals(event.get("actor_id"))
            && "invitation.accept".equals(event.get("action"))), invitationAudit.toString());
    }
    @Test void revokedExpiredTamperedAndDuplicateInvitationsCannotGrantAccess() throws Exception {
        var owner = person(true); var target = person(true); var org = organization(owner);
        var first = invite(owner, org, target, "VIEWER");
        var duplicateTarget = invitationTarget(owner, org);
        ok(409, postJson(path(org)+"/invitations", owner.session(), Map.of("email", target.email(), "role", "VIEWER",
            "teamId", duplicateTarget.teamId(), "repositoryId", duplicateTarget.repositoryId())));
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
        var deniedInvitationTarget = invitationTarget(owner, org);
        ok(403, postJson(path(org)+"/invitations", admin.session(), Map.of("email", "denied@example.test", "role", "OWNER",
            "teamId", deniedInvitationTarget.teamId(), "repositoryId", deniedInvitationTarget.repositoryId())));
        ok(204, change(admin, org, memberId, "VIEWER", "SUSPENDED")); ok(204, change(admin, org, memberId, "MEMBER", "ACTIVE"));
        mvc.perform(get(path(org)+"/access").session(member.session())).andExpect(status().isForbidden());
        ok(403, postJson(path(org)+"/teams", member.session(), Map.of("name", "Escalation")));
        ok(400, change(owner, org, memberId, "SUPERUSER", "ACTIVE"));
    }
    @Test void teamPermissionsAreIntersectedWithRolesAndRevocationsApplyToRealRequests() throws Exception {
        var owner = person(true); var member = person(true); var org = organization(owner);
        UUID repository = UUID.fromString(ok(201, postJson(path(org)+"/repositories", owner.session(), Map.of("provider", "demo", "externalId", UUID.randomUUID().toString(), "fullName", "fixture/permissions", "defaultBranch", "main"))).path("id").asText());
        UUID team = UUID.fromString(ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Readers"))).path("id").asText());
        UUID membership = join(owner, org, member, "VIEWER", team, repository);
        String teamPath = path(org)+"/teams/"+team, repositoryPath = path(org)+"/repositories/"+repository;
        var ownerGitHubStatus = ok(200, mvc.perform(get(path(org)+"/github/status").session(owner.session())).andReturn());
        assertTrue(ownerGitHubStatus.path("canManage").asBoolean());
        var memberGitHubStatus = ok(200, mvc.perform(get(path(org)+"/github/status").session(member.session())).andReturn());
        assertFalse(memberGitHubStatus.path("canManage").asBoolean());
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
        assertTrue(overview(owner, org).path("teams").findValuesAsText("name").stream().noneMatch("Renamed readers"::equals));
    }
    @Test void workspaceSettingsAreSharedAndRestrictedToOrganizationManagers() throws Exception {
        var owner = person(true); var member = person(true); var org = organization(owner);
        UUID memberId = tenants.execute(org, () -> {
            jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, owner.id().toString());
            UUID id = jdbc.queryForObject("insert into organization_memberships(organization_id,user_id) values (?,?) returning id", UUID.class, org, member.id());
            jdbc.update("insert into membership_roles(organization_id,membership_id,role_id) select ?,?,id from roles where code='MEMBER'", org, id);
            return id;
        });
        String settingsPath = path(org)+"/settings";
        var defaults = ok(200, mvc.perform(get(settingsPath).session(owner.session())).andReturn());
        assertEquals("SYSTEM", defaults.path("theme").asText()); assertEquals("ES", defaults.path("language").asText());
        assertTrue(defaults.path("canManage").asBoolean());
        var memberSettings = ok(200, mvc.perform(get(settingsPath).session(member.session())).andReturn());
        assertFalse(memberSettings.path("canManage").asBoolean());
        ok(403, postJson(settingsPath, member.session(), Map.of("theme", "DARK", "language", "EN")));
        var updated = ok(200, postJson(settingsPath, owner.session(), Map.of("theme", "DARK", "language", "EN")));
        assertEquals("DARK", updated.path("theme").asText()); assertEquals("EN", updated.path("language").asText());
        var shared = ok(200, mvc.perform(get(settingsPath).session(member.session())).andReturn());
        assertEquals("DARK", shared.path("theme").asText()); assertEquals("EN", shared.path("language").asText());
        assertEquals(1, tenants.execute(org, () -> jdbc.queryForObject("select count(*) from audit_events where organization_id=? and action='organization.settings.update'", Integer.class, org)));
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
        var failedTarget = invitationTarget(owner, org);
        ok(503, postJson(path(org)+"/invitations", owner.session(), Map.of("email", failedEmail, "role", "MEMBER",
            "teamId", failedTarget.teamId(), "repositoryId", failedTarget.repositoryId())));
        assertEquals(0, tenants.execute(org, () -> jdbc.queryForObject("select count(*) from membership_invitations where email_normalized = ?", Integer.class, failedEmail)));
        assertEquals(1, overview(owner, org).path("members").size(), "Acceptance awaits explicit approval, so the target is not a member yet");
    }
    @Test void inviterDemotionInvalidatesPrivilegeAndReturningMembersLoseOldTeamAccess() throws Exception {
        var owner = person(true); var admin = person(true); var target = person(true); var org = organization(owner);
        UUID adminMember = join(owner, org, admin, "OWNER"); var pending = invite(admin, org, target, "MEMBER");
        ok(204, change(owner, org, adminMember, "MEMBER", "ACTIVE")); ok(403, accept(target, org, pending));
        ok(204, postJson(path(org)+"/invitations/"+pending.id()+"/revoke", owner.session(), Map.of()));
        UUID membership = join(owner, org, target, "MEMBER");
        UUID team = UUID.fromString(ok(201, postJson(path(org)+"/teams", owner.session(), Map.of("name", "Old team"))).path("id").asText());
        ok(204, postJson(path(org)+"/teams/"+team+"/members/"+membership, owner.session(), Map.of("included", true)));
        ok(204, change(owner, org, membership, "MEMBER", "LEFT"));
        var returning = invite(owner, org, target, "VIEWER"); join(owner, org, target, returning);
        var data = overview(owner, org);
        var oldTeam = java.util.stream.StreamSupport.stream(data.path("teams").spliterator(), false)
            .filter(item -> "Old team".equals(item.path("name").asText())).findFirst().orElseThrow();
        assertEquals(0, oldTeam.path("membershipIds").size());
        for (var member : data.path("members")) if (member.path("userId").asText().equals(target.id().toString())) assertEquals("VIEWER", member.path("roles").get(0).asText());
    }
}
