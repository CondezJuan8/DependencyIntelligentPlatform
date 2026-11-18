package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.dto.RegisterAccountCommand;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.application.port.out.PendingAdmissionNotifier;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RegisterAccountUseCaseTest {
    @Test void notifiesPlatformOwnerAfterCreatingPendingOwnerAccount() {
        UUID id = UUID.randomUUID();
        AccountResult created = new AccountResult(id, "owner@example.test", "Jane Smith", false,
            "OWNER", "PENDING_REVIEW", false);
        AccountStore store = new AccountStore() {
            @Override public AccountResult create(String email, String name, String hash) { return created; }
            @Override public AccountResult createInvited(String email, String name, String hash) { throw new AssertionError(); }
            @Override public Optional<com.dependencyintelligence.identity.application.dto.AccountCredentials> credentials(String email) { return Optional.empty(); }
            @Override public Optional<AccountResult> findActive(UUID userId) { return Optional.empty(); }
            @Override public boolean sessionCurrent(UUID userId, long version) { return false; }
        };
        PasswordHashing hashing = new PasswordHashing() {
            @Override public String hash(String password) { return "encoded"; }
            @Override public boolean matches(String password, String hash) { return false; }
        };
        AccountResult[] notified = new AccountResult[1];
        PendingAdmissionNotifier notifier = account -> notified[0] = account;

        AccountResult result = new RegisterAccountUseCase(store, hashing, notifier)
            .execute(new RegisterAccountCommand("owner@example.test", "Jane Smith", "A-long-enough-passphrase-42"));

        assertSame(created, result);
        assertSame(created, notified[0]);
        assertEquals("PENDING_REVIEW", notified[0].admissionStatus());
    }
}
