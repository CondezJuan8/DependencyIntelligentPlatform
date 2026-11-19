package com.dependencyintelligence.integration.application.usecase;

import com.dependencyintelligence.integration.application.dto.GitHubModels.Candidate;
import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStateStore;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStore;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ManageGitHubInstallationUseCaseTest {
    private final UUID organization = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");

    @Test void beginsAndCompletesInstallationBoundToUserOrganizationAndBrowserSession() {
        var states = new States(); var store = new Installations(); var app = new App();
        var useCase = new ManageGitHubInstallationUseCase(app, states, store, Clock.fixed(now, ZoneOffset.UTC));
        String url = useCase.begin(organization, user, "session-1").url();
        assertTrue(url.startsWith("https://github.com/apps/dependency-intelligence/installations/new?state="));
        String state = url.substring(url.indexOf("state=") + 6);
        assertEquals(43, state.length());

        assertThrows(InvalidGitHubInstallStateException.class,
            () -> useCase.authorizeInstallation(organization, user, "other-session", state, 77));
        assertTrue(store.connected.isEmpty());

        var authUrl = useCase.authorizeInstallation(organization, user, "session-1", state, 77).url();
        assertTrue(authUrl.startsWith("https://github.com/login/oauth/authorize?"));
        useCase.completeFromCallback(user, "session-1", state, "one-use-code");
        var workspace = useCase.workspace(organization, user);
        assertEquals("CondezJuan8", workspace.installations().getFirst().accountLogin());
        assertEquals("90210", workspace.installations().getFirst().repositories().getFirst().externalId());
        assertEquals(77, store.connected.getFirst().id());
        assertFalse(states.values.containsKey(state));
    }

    @Test void rejectsRepositoryIdentifiersOutsideTheConnectedInstallation() {
        var useCase = new ManageGitHubInstallationUseCase(new App(), new States(), new Installations(), Clock.fixed(now, ZoneOffset.UTC));
        assertThrows(InvalidGitHubRepositorySelectionException.class,
            () -> useCase.importRepositories(organization, user, List.of("999")));
    }

    @Test void connectsAnAlreadyInstalledAppOnlyAfterTheSameBrowserAuthorizesIt() {
        var states = new States(); var store = new Installations(); var app = new App();
        var useCase = new ManageGitHubInstallationUseCase(app, states, store, Clock.fixed(now, ZoneOffset.UTC));
        var url = useCase.connectExisting(organization, user, "session-1", 77).url();
        assertTrue(url.contains("code_challenge="));
        String state = url.split("state=")[1].split("&")[0];
        assertThrows(InvalidGitHubInstallStateException.class,
            () -> useCase.completeFromCallback(user, "other-session", state, "one-use-code"));
        assertTrue(store.connected.isEmpty());
        useCase.completeFromCallback(user, "session-1", state, "one-use-code");
        assertEquals(77, store.connected.getFirst().id());
        assertThrows(InvalidGitHubInstallStateException.class,
            () -> useCase.completeFromCallback(user, "session-1", state, "one-use-code"));
    }

    @Test void installButtonUsesOAuthWhenTheAppHasOneUnlinkedInstallation() {
        var states = new States(); var store = new Installations();
        var app = new App() {
            @Override public List<InstallationInfo> installationsForConnection() {
                return List.of(new InstallationInfo(77, "CondezJuan8", "USER"));
            }
        };
        var useCase = new ManageGitHubInstallationUseCase(app, states, store, Clock.fixed(now, ZoneOffset.UTC));
        String url = useCase.begin(organization, user, "session-1").url();
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"));
        String state = url.split("state=")[1].split("&")[0];
        useCase.completeFromCallback(user, "session-1", state, "one-use-code");
        assertEquals(77, store.connected.getFirst().id());
    }

    private final class States implements GitHubInstallationStateStore {
        final Map<String, Pending> values = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void save(String state, Pending pending) { values.put(state, pending); }
        @Override public Optional<Pending> find(String state) { return Optional.ofNullable(values.get(state)); }
        @Override public boolean consume(String state, Pending pending) { return values.remove(state, pending); }
        @Override public boolean attachAuthorization(String state, Pending current, Pending authorized) { return values.replace(state, current, authorized); }
    }
    private class App implements GitHubAppGateway {
        @Override public String installationUrl(String state) { return "https://github.com/apps/dependency-intelligence/installations/new?state=" + state; }
        @Override public String userAuthorizationUrl(String state, String challenge) { return "https://github.com/login/oauth/authorize?client_id=client&state="+state+"&code_challenge="+challenge; }
        @Override public String exchangeAuthorizationCode(String code, String verifier) { assertEquals("one-use-code",code); assertNotNull(verifier); return "temporary-user-token"; }
        @Override public void verifyUserCanAccessInstallation(String token, long id) { assertEquals("temporary-user-token",token); assertEquals(77,id); }
        @Override public InstallationInfo verifyInstallation(long id) { return new InstallationInfo(id, "CondezJuan8", "USER"); }
        @Override public List<InstallationInfo> installationsForConnection() { return List.of(); }
        @Override public List<Candidate> repositories(long id) { return List.of(new Candidate("90210", "CondezJuan8/demo", "main", "https://github.com/CondezJuan8/demo", "PRIVATE", false)); }
        @Override public String downloadArchive(long id, String repositoryExternalId, String fullName, java.nio.file.Path target) { throw new UnsupportedOperationException(); }
    }
    private final class Installations implements GitHubInstallationStore {
        final List<GitHubAppGateway.InstallationInfo> connected = new ArrayList<>();
        @Override public void connect(UUID org, UUID actor, GitHubAppGateway.InstallationInfo installation) { connected.add(installation); }
        @Override public List<GitHubAppGateway.InstallationInfo> connected(UUID org, UUID actor) { return List.copyOf(connected); }
        @Override public List<String> importedRepositoryIds(UUID org, UUID actor) { return List.of(); }
        @Override public int importRepositories(UUID org, UUID actor, Map<Long, List<Candidate>> repositories) { return repositories.values().stream().mapToInt(List::size).sum(); }
    }
}
