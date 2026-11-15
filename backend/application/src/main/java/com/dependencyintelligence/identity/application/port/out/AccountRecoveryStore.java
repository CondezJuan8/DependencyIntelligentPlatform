package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.application.dto.RecoveryRecipient;
import com.dependencyintelligence.identity.domain.AccountTokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRecoveryStore {
    Optional<RecoveryRecipient> lockUser(UUID userId);
    Optional<RecoveryRecipient> lockUserByEmail(String email);
    Optional<RecoveryRecipient> lockTokenOwner(String hash, AccountTokenPurpose purpose, Instant now);
    boolean issuedSince(UUID userId, AccountTokenPurpose purpose, Instant since);
    void issue(UUID userId, AccountTokenPurpose purpose, String hash, Instant now, Instant expires);
    void consume(UUID userId, AccountTokenPurpose purpose, Instant now);
    void markVerified(UUID userId, Instant now);
    void replacePassword(UUID userId, String hash, Instant now);
}
