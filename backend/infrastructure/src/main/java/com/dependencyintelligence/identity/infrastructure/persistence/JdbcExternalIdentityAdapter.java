package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.application.exception.ExternalIdentityException;
import com.dependencyintelligence.identity.application.port.out.ExternalIdentityStore;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.dependencyintelligence.identity.application.exception.ExternalIdentityException.Reason.*;

@Repository
public class JdbcExternalIdentityAdapter implements ExternalIdentityStore {
    private final JdbcTemplate jdbc;

    public JdbcExternalIdentityAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<UUID> findLinked(IdentityProvider provider, String subject) {
        return jdbc.query("select user_id from user_identities where provider = ? and provider_subject = ?",
            (row, index) -> row.getObject("user_id", UUID.class), provider.code(), subject).stream().findFirst();
    }

    @Override public boolean emailExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where email_normalized = ?)", Boolean.class, email));
    }

    @Override public ExternalAccountState lockAccount(UUID userId) {
        return jdbc.query("""
            select account.*, exists(select 1 from user_password_credentials credential where credential.user_id = account.id) as password_available
            from users account where account.id = ? for update of account
            """, (row, index) -> new ExternalAccountState(new AuthenticatedAccount(new AccountResult(userId,
                row.getString("email"), row.getString("display_name"), row.getTimestamp("email_verified_at") != null),
                row.getLong("credential_version")), row.getBoolean("is_active"), row.getBoolean("password_available")), userId)
            .stream().findFirst().orElseThrow(() -> new ExternalIdentityException(ACCOUNT_DISABLED));
    }

    @Override public AuthenticatedAccount create(String email, String name, boolean verified, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into users(id, email, display_name, email_verified_at) values (?, ?, ?, ?)",
                id, email, name, verified ? Timestamp.from(now) : null);
        } catch (DuplicateKeyException exception) { throw new ExternalIdentityException(IDENTITY_CONFLICT); }
        return new AuthenticatedAccount(new AccountResult(id, email, name, verified), 0);
    }

    @Override public void add(UUID id, IdentityProvider provider, String subject) {
        try { jdbc.update("insert into user_identities(user_id, provider, provider_subject) values (?, ?, ?)", id, provider.code(), subject); }
        catch (DuplicateKeyException exception) { throw new ExternalIdentityException(IDENTITY_CONFLICT); }
    }

    @Override public List<IdentityProvider> providers(UUID id) {
        return jdbc.query("select provider from user_identities where user_id = ? and provider in ('google','github') order by provider",
            (row, index) -> IdentityProvider.fromCode(row.getString(1)), id);
    }

    @Override public void remove(UUID id, IdentityProvider provider) {
        jdbc.update("delete from user_identities where user_id = ? and provider = ?", id, provider.code());
    }

    @Override public long incrementCredentialVersion(UUID id) {
        return jdbc.queryForObject("update users set credential_version = credential_version + 1, updated_at = current_timestamp where id = ? returning credential_version",
            Long.class, id);
    }
}
