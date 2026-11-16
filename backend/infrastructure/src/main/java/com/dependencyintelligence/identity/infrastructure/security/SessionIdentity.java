package com.dependencyintelligence.identity.infrastructure.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;
import java.time.Instant;

public record SessionIdentity(UUID userId, long credentialVersion, Instant authenticatedAt) implements Principal, Serializable {
    @Override public String getName() { return userId.toString(); }
}
