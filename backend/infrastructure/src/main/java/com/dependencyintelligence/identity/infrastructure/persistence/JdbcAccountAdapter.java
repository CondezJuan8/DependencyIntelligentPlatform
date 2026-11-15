package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.dto.AccountCredentials;
import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.exception.AccountAlreadyExistsException;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountAdapter implements AccountStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcAccountAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override public AccountResult create(String email, String displayName, String passwordHash) {
        try {
            return transactions.execute(status -> {
                UUID id = UUID.randomUUID();
                jdbc.update("insert into users(id, email, display_name) values (?, ?, ?)", id, email, displayName);
                jdbc.update("insert into user_password_credentials(user_id, password_hash) values (?, ?)", id, passwordHash);
                return new AccountResult(id, email, displayName);
            });
        } catch (DuplicateKeyException exception) { throw new AccountAlreadyExistsException(); }
    }

    @Override public Optional<AccountCredentials> credentials(String email) {
        return jdbc.query("""
            select u.id, u.email, u.display_name, u.is_active, c.password_hash
            from users u join user_password_credentials c on c.user_id = u.id where u.email_normalized = ?
            """, (row, index) -> new AccountCredentials(new AccountResult(row.getObject("id", UUID.class),
                row.getString("email"), row.getString("display_name")), row.getString("password_hash"), row.getBoolean("is_active")), email)
            .stream().findFirst();
    }

    @Override public Optional<AccountResult> findActive(UUID userId) {
        return jdbc.query("select id, email, display_name from users where id = ? and is_active",
            (row, index) -> new AccountResult(row.getObject("id", UUID.class), row.getString("email"), row.getString("display_name")), userId)
            .stream().findFirst();
    }
}
