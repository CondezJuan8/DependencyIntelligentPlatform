package com.dependencyintelligence.identity.infrastructure.security;

import com.dependencyintelligence.identity.application.port.in.AuthorizeRepositoryAction;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.stereotype.Component;
import java.security.Principal;
import java.util.UUID;
import java.util.function.Supplier;

/** Checks permission and performs the operation within the same tenant transaction. */
@Component
public class AuthorizedRepositoryActions {
    private final TenantTransactions transactions;
    private final AuthorizeRepositoryAction authorization;

    public AuthorizedRepositoryActions(TenantTransactions transactions, AuthorizeRepositoryAction authorization) {
        this.transactions = transactions;
        this.authorization = authorization;
    }

    public <T> T execute(Principal principal, UUID organization, UUID repository, String permission, Supplier<T> operation) {
        return transactions.execute(organization, () -> {
            authorization.check(UUID.fromString(principal.getName()), organization, repository, permission);
            return operation.get();
        });
    }
}
