package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.application.dto.AccountCredentials;
import com.dependencyintelligence.identity.application.dto.AccountResult;
import java.util.Optional;
import java.util.UUID;

public interface AccountStore {
    AccountResult create(String email, String displayName, String passwordHash);
    Optional<AccountCredentials> credentials(String email);
    Optional<AccountResult> findActive(UUID userId);
    boolean sessionCurrent(UUID userId, long credentialVersion);
}
