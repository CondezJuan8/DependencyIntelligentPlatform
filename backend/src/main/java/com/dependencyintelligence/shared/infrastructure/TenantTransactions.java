package com.dependencyintelligence.shared.infrastructure;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Entry point for an authenticated, membership-authorized tenant use case. */
@Component
public class TenantTransactions {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TenantTransactions(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        // An inner use case must never change the tenant of an outer transaction.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(UUID organizationId, Supplier<T> useCase) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(useCase, "useCase");
        return transaction.execute(status -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)",
                    String.class, organizationId.toString());
            return useCase.get();
        });
    }
}
