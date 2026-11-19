package com.dependencyintelligence.identity.application.port.out;

import java.util.UUID;

public interface PlatformAdministratorBootstrapStore {
    void createOnlyWhenDatabaseIsEmpty(UUID id, String email, String displayName, String passwordHash);
}
