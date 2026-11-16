package com.dependencyintelligence.identity.infrastructure.configuration;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.application.port.in.ManageExternalIdentities;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Set;
import java.util.UUID;

public class TransactionalExternalIdentities implements ManageExternalIdentities {
    private final ManageExternalIdentities delegate;
    private final TransactionTemplate transactions;

    public TransactionalExternalIdentities(ManageExternalIdentities delegate, PlatformTransactionManager manager) {
        this.delegate = delegate;
        this.transactions = new TransactionTemplate(manager);
    }
    @Override public AuthenticatedAccount signIn(ExternalIdentityProof proof) {
        return transactions.execute(status -> delegate.signIn(proof));
    }
    @Override public AuthenticatedAccount link(UUID id, long version, ExternalIdentityProof proof) {
        return transactions.execute(status -> delegate.link(id, version, proof));
    }
    @Override public ConnectedIdentities connections(UUID id) { return transactions.execute(status -> delegate.connections(id)); }
    @Override public void unlink(UUID id, long version, IdentityProvider provider, Set<IdentityProvider> enabled) {
        transactions.executeWithoutResult(status -> delegate.unlink(id, version, provider, enabled));
    }
}
