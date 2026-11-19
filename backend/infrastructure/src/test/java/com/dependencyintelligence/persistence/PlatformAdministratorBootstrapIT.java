package com.dependencyintelligence.persistence;

import com.dependencyintelligence.identity.application.dto.BootstrapPlatformAdministratorCommand;
import com.dependencyintelligence.identity.application.port.in.BootstrapPlatformAdministrator;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlatformAdministratorBootstrapIT extends PostgresIntegrationSupport {
    private static final String INITIAL_PASSWORD = "Local-bootstrap-passphrase-2026";

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) { database(registry, false); }

    @Autowired BootstrapPlatformAdministrator bootstrap;
    @Autowired PasswordHashing passwords;
    @Autowired JdbcTemplate jdbc;

    @Test void createsVerifiedOwnerAndPlatformAdministratorOnlyOnce() {
        UUID id = bootstrap.execute(new BootstrapPlatformAdministratorCommand(
            " Platform.Admin@example.test ", "Jane Smith", INITIAL_PASSWORD, INITIAL_PASSWORD));

        var account = jdbc.queryForMap("""
            select email, display_name, email_verified_at, account_type, admission_status, is_active
            from users where id = ?
            """, id);
        assertEquals("platform.admin@example.test", account.get("email"));
        assertEquals("Jane Smith", account.get("display_name"));
        assertNotNull(account.get("email_verified_at"));
        assertEquals("OWNER", account.get("account_type"));
        assertEquals("APPROVED", account.get("admission_status"));
        assertEquals(true, account.get("is_active"));
        String hash = jdbc.queryForObject("select password_hash from user_password_credentials where user_id = ?", String.class, id);
        assertTrue(passwords.matches(INITIAL_PASSWORD, hash));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_administrators where user_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_audit_events where actor_id = ? and action = 'CREATE'", Integer.class, id));

        assertThrows(IllegalStateException.class, () -> bootstrap.execute(new BootstrapPlatformAdministratorCommand(
            "another.admin@example.test", "John Smith", INITIAL_PASSWORD, INITIAL_PASSWORD)));
        assertEquals(1, jdbc.queryForObject("select count(*) from users", Integer.class));
    }
}
