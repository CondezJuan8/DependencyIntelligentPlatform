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
        return create(email,displayName,passwordHash,"OWNER","PENDING_REVIEW");
    }
    @Override public AccountResult createInvited(String email, String displayName, String passwordHash) {
        return create(email,displayName,passwordHash,"MEMBER","APPROVED");
    }
    private AccountResult create(String email, String displayName, String passwordHash, String type, String admission) {
        try {
            return transactions.execute(status -> {
                UUID id = UUID.randomUUID();
                jdbc.update("insert into users(id,email,display_name,account_type,admission_status) values (?,?,?,?,?)", id,email,displayName,type,admission);
                jdbc.update("insert into user_password_credentials(user_id, password_hash) values (?, ?)", id, passwordHash);
                return new AccountResult(id,email,displayName,false,type,admission,false);
            });
        } catch (DuplicateKeyException exception) { throw new AccountAlreadyExistsException(); }
    }

    @Override public Optional<AccountCredentials> credentials(String email) {
        return jdbc.query("""
            select u.*, c.password_hash, exists(select 1 from platform_administrators a where a.user_id=u.id) as platform_admin
            from users u join user_password_credentials c on c.user_id = u.id where u.email_normalized = ?
            """, (row, index) -> new AccountCredentials(account(row),
                row.getString("password_hash"), row.getBoolean("is_active"), row.getLong("credential_version")), email)
            .stream().findFirst();
    }

    @Override public Optional<AccountResult> findActive(UUID userId) {
        return jdbc.query("select u.*,exists(select 1 from platform_administrators a where a.user_id=u.id) as platform_admin from users u where u.id = ? and u.is_active",
            (row, index) -> account(row), userId)
            .stream().findFirst();
    }

    @Override public boolean sessionCurrent(UUID userId, long version) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists(select 1 from users where id = ? and is_active and credential_version = ?)", Boolean.class, userId, version));
    }
    private AccountResult account(java.sql.ResultSet row) throws java.sql.SQLException {
        return new AccountResult(row.getObject("id",UUID.class),row.getString("email"),row.getString("display_name"),
            row.getTimestamp("email_verified_at")!=null,row.getString("account_type"),row.getString("admission_status"),row.getBoolean("platform_admin"));
    }
}
