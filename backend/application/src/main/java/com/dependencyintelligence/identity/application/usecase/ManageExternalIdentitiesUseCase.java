package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.application.exception.ExternalIdentityException;
import com.dependencyintelligence.identity.application.port.in.ManageExternalIdentities;
import com.dependencyintelligence.identity.application.port.out.ExternalIdentityStore;
import com.dependencyintelligence.identity.domain.EmailAddress;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import static com.dependencyintelligence.identity.application.exception.ExternalIdentityException.Reason.*;

public final class ManageExternalIdentitiesUseCase implements ManageExternalIdentities {
    private final ExternalIdentityStore store;
    private final Clock clock;

    public ManageExternalIdentitiesUseCase(ExternalIdentityStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override public AuthenticatedAccount signIn(ExternalIdentityProof proof) {
        var linked = store.findLinked(proof.provider(), proof.subject());
        if (linked.isPresent()) {
            var account = active(linked.get());
            // Unlink may have committed while the account row lock was being acquired.
            if (!store.findLinked(proof.provider(), proof.subject()).equals(linked)) {
                throw new ExternalIdentityException(IDENTITY_CONFLICT);
            }
            return account.authenticated();
        }
        String email;
        try { email = new EmailAddress(proof.email()).value(); }
        catch (IllegalArgumentException exception) { throw new ExternalIdentityException(EMAIL_REQUIRED); }
        if (store.emailExists(email)) throw new ExternalIdentityException(LINK_REQUIRED);
        String name = proof.displayName() == null ? "" : proof.displayName().strip();
        if (name.isBlank()) name = email.substring(0, email.indexOf('@'));
        if (name.length() > 200) name = name.substring(0, 200);
        name = new com.dependencyintelligence.identity.domain.PersonalName(name).value();
        var account = store.create(email, name, proof.trustedEmail(), clock.instant());
        store.add(account.account().id(), proof.provider(), proof.subject());
        return account;
    }

    @Override public AuthenticatedAccount link(UUID userId, long version, ExternalIdentityProof proof) {
        var account = current(userId, version);
        if (!account.authenticated().account().emailVerified()) throw new ExternalIdentityException(EMAIL_VERIFICATION_REQUIRED);
        var linked = store.findLinked(proof.provider(), proof.subject());
        if (linked.isPresent()) {
            if (!linked.get().equals(userId)) throw new ExternalIdentityException(IDENTITY_CONFLICT);
            return account.authenticated();
        }
        store.add(userId, proof.provider(), proof.subject());
        return new AuthenticatedAccount(account.authenticated().account(), store.incrementCredentialVersion(userId));
    }

    @Override public ConnectedIdentities connections(UUID userId) {
        var account = active(userId);
        return new ConnectedIdentities(store.providers(userId), account.passwordAvailable());
    }

    @Override public void unlink(UUID userId, long version, IdentityProvider provider, Set<IdentityProvider> enabled) {
        var account = current(userId, version);
        var providers = store.providers(userId);
        if (!providers.contains(provider)) return;
        boolean anotherProvider = providers.stream().anyMatch(other -> other != provider && enabled.contains(other));
        if (!account.passwordAvailable() && !anotherProvider) throw new ExternalIdentityException(LAST_SIGN_IN_METHOD);
        store.remove(userId, provider);
        store.incrementCredentialVersion(userId);
    }

    private ExternalAccountState active(UUID userId) {
        var account = store.lockAccount(userId);
        if (!account.active()) throw new ExternalIdentityException(ACCOUNT_DISABLED);
        return account;
    }

    private ExternalAccountState current(UUID userId, long version) {
        var account = active(userId);
        if (account.authenticated().credentialVersion() != version) throw new ExternalIdentityException(SESSION_EXPIRED);
        return account;
    }
}
