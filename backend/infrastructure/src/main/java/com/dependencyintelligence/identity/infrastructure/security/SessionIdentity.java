package com.dependencyintelligence.identity.infrastructure.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record SessionIdentity(UUID userId, long credentialVersion) implements Principal, Serializable {
    @Override public String getName() { return userId.toString(); }
}
