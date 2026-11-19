package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.BootstrapPlatformAdministratorCommand;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.application.port.out.PlatformAdministratorBootstrapStore;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapPlatformAdministratorUseCaseTest {
    @Test void normalizesIdentityHashesPasswordAndBootstrapsOnlyAfterConfirmation() {
        AtomicBoolean stored = new AtomicBoolean();
        PlatformAdministratorBootstrapStore store = (id, email, name, hash) -> {
            assertEquals("admin@example.test", email);
            assertEquals("Jane Smith", name);
            assertEquals("encoded", hash);
            assertNotNull(id);
            stored.set(true);
        };
        PasswordHashing passwords = new PasswordHashing() {
            @Override public String hash(String value) { assertEquals("A-long-enough-passphrase-42", value); return "encoded"; }
            @Override public boolean matches(String value, String hash) { return false; }
        };

        UUID id = new BootstrapPlatformAdministratorUseCase(store, passwords).execute(
            new BootstrapPlatformAdministratorCommand(" Admin@Example.Test ", "Jane Smith", "A-long-enough-passphrase-42", "A-long-enough-passphrase-42"));

        assertNotNull(id);
        assertTrue(stored.get());
    }

    @Test void rejectsMismatchedPasswordBeforeHashingOrWriting() {
        AtomicBoolean touched = new AtomicBoolean();
        PlatformAdministratorBootstrapStore store = (id, email, name, hash) -> touched.set(true);
        PasswordHashing passwords = new PasswordHashing() {
            @Override public String hash(String value) { touched.set(true); return "encoded"; }
            @Override public boolean matches(String value, String hash) { return false; }
        };

        assertThrows(IllegalArgumentException.class, () -> new BootstrapPlatformAdministratorUseCase(store, passwords).execute(
            new BootstrapPlatformAdministratorCommand("admin@example.test", "Jane Smith", "A-long-enough-passphrase-42", "different-password-42")));
        assertFalse(touched.get());
    }
}
