package com.dependencyintelligence.integration.application.usecase;

import com.dependencyintelligence.integration.application.dto.GitHubModels.*;
import com.dependencyintelligence.integration.application.port.in.ManageGitHubInstallation;
import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStateStore;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStateStore.Pending;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStore;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public final class ManageGitHubInstallationUseCase implements ManageGitHubInstallation {
    private final GitHubAppGateway app;
    private final GitHubInstallationStateStore states;
    private final GitHubInstallationStore installations;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ManageGitHubInstallationUseCase(GitHubAppGateway app, GitHubInstallationStateStore states,
            GitHubInstallationStore installations, Clock clock) {
        this.app = app; this.states = states; this.installations = installations; this.clock = clock;
    }

    @Override public InstallationLink begin(UUID organization, UUID user, String browserSession) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(state, new Pending(organization, user, browserSession, clock.instant().plus(Duration.ofMinutes(10)), null, null));
        return new InstallationLink(app.installationUrl(state));
    }

    @Override public InstallationLink connectExisting(UUID organization, UUID user, String browserSession, long installationId) {
        if (installationId < 1) throw new IllegalArgumentException("El identificador de instalación debe ser positivo.");
        installations.connected(organization, user);
        app.verifyInstallation(installationId);
        byte[] stateBytes = new byte[32]; random.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        byte[] verifierBytes = new byte[32]; random.nextBytes(verifierBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String challenge;
        try { challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable."); }
        states.save(state, new Pending(organization, user, browserSession, clock.instant().plus(Duration.ofMinutes(10)), installationId, verifier));
        return new InstallationLink(app.userAuthorizationUrl(state, challenge));
    }

    @Override public InstallationLink authorizeInstallation(UUID organization, UUID user, String browserSession, String state, long installationId) {
        var pending = pending(state, organization, user, browserSession);
        if (pending.installationId() != null || installationId < 1) throw new InvalidGitHubInstallStateException();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String challenge;
        try { challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable."); }
        var authorized = new Pending(organization, user, browserSession, pending.expiresAt(), installationId, verifier);
        if (!states.attachAuthorization(state, pending, authorized)) throw new InvalidGitHubInstallStateException();
        return new InstallationLink(app.userAuthorizationUrl(state, challenge));
    }

    @Override public void completeFromCallback(UUID user, String browserSession, String state, String code) {
        var pending = pending(state, null, user, browserSession);
        if (pending.installationId() == null || pending.codeVerifier() == null || code == null || code.isBlank()
                || !states.consume(state, pending)) throw new InvalidGitHubInstallStateException();
        String userAccessToken = app.exchangeAuthorizationCode(code, pending.codeVerifier());
        app.verifyUserCanAccessInstallation(userAccessToken, pending.installationId());
        var installation = app.verifyInstallation(pending.installationId());
        installations.connect(pending.organizationId(), user, installation);
    }

    private Pending pending(String state, UUID organization, UUID user, String browserSession) {
        var pending = states.find(state).filter(item -> (organization == null || item.organizationId().equals(organization))
            && item.userId().equals(user) && item.browserSession().equals(browserSession)
            && item.expiresAt().isAfter(clock.instant())).orElseThrow(InvalidGitHubInstallStateException::new);
        return pending;
    }

    @Override public Workspace workspace(UUID organization, UUID user) {
        var items = installations.connected(organization, user).stream().map(installation -> new Installation(
            installation.id(), installation.accountLogin(), installation.accountType(), app.repositories(installation.id()))).toList();
        return new Workspace(items, installations.importedRepositoryIds(organization, user));
    }

    @Override public ImportResult importRepositories(UUID organization, UUID user, List<String> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty() || selectedIds.size() > 500
                || selectedIds.stream().anyMatch(id -> id == null || !id.matches("[0-9]{1,20}"))
                || selectedIds.stream().distinct().count() != selectedIds.size()) {
            throw new IllegalArgumentException("Selecciona entre 1 y 500 repositorios válidos sin repetir.");
        }
        var installationsForOrg = installations.connected(organization, user);
        Map<Long, List<Candidate>> selected = new HashMap<>();
        var resolvedIds = new HashSet<String>();
        for (var installation : installationsForOrg) {
            var matches = app.repositories(installation.id()).stream().filter(repository -> selectedIds.contains(repository.externalId())
                && resolvedIds.add(repository.externalId())).toList();
            if (!matches.isEmpty()) selected.put(installation.id(), matches);
        }
        int resolved = selected.values().stream().mapToInt(List::size).sum();
        if (resolved != selectedIds.size()) throw new InvalidGitHubRepositorySelectionException();
        return new ImportResult(installations.importRepositories(organization, user, Map.copyOf(selected)));
    }
}
