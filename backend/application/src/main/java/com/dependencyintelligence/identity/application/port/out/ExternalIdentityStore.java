package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityStore {
    Optional<UUID> findLinked(IdentityProvider provider, String subject);
    boolean emailExists(String normalizedEmail);
    ExternalAccountState lockAccount(UUID userId);
    AuthenticatedAccount create(String email, String name, boolean verified, Instant now);
    void add(UUID userId, IdentityProvider provider, String subject);
    List<IdentityProvider> providers(UUID userId);
    void remove(UUID userId, IdentityProvider provider);
    long incrementCredentialVersion(UUID userId);
}
