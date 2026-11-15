package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.dto.RecoveryRecipient;
import com.dependencyintelligence.identity.application.port.out.AccountRecoveryStore;
import com.dependencyintelligence.identity.domain.AccountTokenPurpose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Every mutation is serialized through the owning user row by the recovery transaction. */
@Repository
public class JdbcAccountRecoveryAdapter implements AccountRecoveryStore {
    private final JdbcTemplate jdbc;
    private static final String RECIPIENT = """
        select account.id, account.email, account.email_verified_at,
          exists(select 1 from user_password_credentials credential where credential.user_id = account.id) as local_credentials
        from users account where account.is_active and
        """;

    public JdbcAccountRecoveryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Optional<RecoveryRecipient> lock(String predicate, Object value) {
        return jdbc.query(RECIPIENT + predicate + " for update of account", (row, index) ->
            new RecoveryRecipient(row.getObject("id", UUID.class), row.getString("email"),
                row.getTimestamp("email_verified_at") != null, row.getBoolean("local_credentials")), value).stream().findFirst();
    }

    @Override public Optional<RecoveryRecipient> lockUser(UUID id) { return lock("account.id = ?", id); }
    @Override public Optional<RecoveryRecipient> lockUserByEmail(String email) { return lock("account.email_normalized = ?", email); }

    @Override public Optional<RecoveryRecipient> lockTokenOwner(String hash, AccountTokenPurpose purpose, Instant now) {
        var users = jdbc.query("select user_id from user_access_tokens where token_hash = ? and purpose = ?",
            (row, index) -> row.getObject("user_id", UUID.class), hash, purpose.name());
        if (users.isEmpty()) return Optional.empty();
        var recipient = lockUser(users.getFirst());
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from user_access_tokens where token_hash = ? and purpose = ?
              and consumed_at is null and expires_at > ? and expires_at > clock_timestamp())
            """, Boolean.class, hash, purpose.name(), Timestamp.from(now)));
        return valid ? recipient : Optional.empty();
    }

    @Override public boolean issuedSince(UUID id, AccountTokenPurpose purpose, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from user_access_tokens where user_id = ? and purpose = ? and created_at > ?)
            """, Boolean.class, id, purpose.name(), Timestamp.from(since)));
    }

    @Override public void issue(UUID id, AccountTokenPurpose purpose, String hash, Instant now, Instant expires) {
        jdbc.update("""
            insert into user_access_tokens(user_id, purpose, token_hash, created_at, expires_at) values (?, ?, ?, ?, ?)
            """, id, purpose.name(), hash, Timestamp.from(now), Timestamp.from(expires));
    }

    @Override public void consume(UUID id, AccountTokenPurpose purpose, Instant now) {
        jdbc.update("update user_access_tokens set consumed_at = ? where user_id = ? and purpose = ? and consumed_at is null",
            Timestamp.from(now), id, purpose.name());
    }

    @Override public void markVerified(UUID id, Instant now) {
        jdbc.update("update users set email_verified_at = coalesce(email_verified_at, ?), updated_at = ? where id = ?",
            Timestamp.from(now), Timestamp.from(now), id);
    }

    @Override public void replacePassword(UUID id, String hash, Instant now) {
        int changed = jdbc.update("update user_password_credentials set password_hash = ?, changed_at = ? where user_id = ?",
            hash, Timestamp.from(now), id);
        if (changed != 1) throw new IllegalStateException("Local credentials are required for password recovery");
        jdbc.update("update users set credential_version = credential_version + 1, updated_at = ? where id = ?", Timestamp.from(now), id);
        jdbc.update("update user_access_tokens set consumed_at = ? where user_id = ? and purpose = 'REFRESH_SESSION' and consumed_at is null",
            Timestamp.from(now), id);
    }
}
