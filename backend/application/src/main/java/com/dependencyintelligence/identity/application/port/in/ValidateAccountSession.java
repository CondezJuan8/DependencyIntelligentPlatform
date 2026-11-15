package com.dependencyintelligence.identity.application.port.in;

import java.util.UUID;

public interface ValidateAccountSession {
    boolean isCurrent(UUID userId, long credentialVersion);
}
