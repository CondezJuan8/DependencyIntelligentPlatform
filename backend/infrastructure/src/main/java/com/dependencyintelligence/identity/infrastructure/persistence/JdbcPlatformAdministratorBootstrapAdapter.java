package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.port.out.PlatformAdministratorBootstrapStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Component
public class JdbcPlatformAdministratorBootstrapAdapter implements PlatformAdministratorBootstrapStore {
    private static final long BOOTSTRAP_LOCK = 7_315_420_006_119L;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcPlatformAdministratorBootstrapAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override public void createOnlyWhenDatabaseIsEmpty(UUID id, String email, String displayName, String passwordHash) {
        transactions.executeWithoutResult(status -> {
            jdbc.execute("select pg_advisory_xact_lock(" + BOOTSTRAP_LOCK + ")");
            Boolean empty = jdbc.queryForObject("""
                select not exists(select 1 from users)
                   and not exists(select 1 from organizations)
                   and not exists(select 1 from platform_administrators)
                """, Boolean.class);
            if (!Boolean.TRUE.equals(empty)) {
                throw new IllegalStateException("El administrador inicial solo puede crearse cuando no hay cuentas ni organizaciones.");
            }

            jdbc.update("""
                insert into users(id, email, display_name, email_verified_at, account_type, admission_status)
                values (?, ?, ?, clock_timestamp(), 'OWNER', 'APPROVED')
                """, id, email, displayName);
            jdbc.update("insert into user_password_credentials(user_id, password_hash) values (?, ?)", id, passwordHash);
            jdbc.update("insert into platform_administrators(user_id) values (?)", id);
            jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, id.toString());
            jdbc.update("""
                insert into platform_audit_events(actor_id, action, resource_type, resource_id, reason)
                values (?, 'CREATE', 'USER', ?, 'Initial platform administrator bootstrap')
                """, id, id);
        });
    }
}
