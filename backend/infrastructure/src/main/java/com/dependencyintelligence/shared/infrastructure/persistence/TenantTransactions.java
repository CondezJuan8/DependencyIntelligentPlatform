package com.dependencyintelligence.shared.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Tenant context is local to the same transaction/connection used by JDBC and JPA. */
@Component
public class TenantTransactions {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public TenantTransactions(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public <T> T execute(UUID organizationId, Supplier<T> operation) {
        Objects.requireNonNull(organizationId, "Organization context is required");
        return transactions.execute(status -> {
            String current = jdbc.queryForObject("select nullif(current_setting('app.organization_id', true), '')", String.class);
            if (current != null && !organizationId.toString().equals(current)) {
                throw new IllegalStateException("Cannot switch organization inside an active tenant transaction");
            }
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, organizationId.toString());
            return operation.get();
        });
    }
}
