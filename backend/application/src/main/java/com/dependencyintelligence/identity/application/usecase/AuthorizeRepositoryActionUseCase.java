package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.identity.application.port.in.AuthorizeRepositoryAction;
import com.dependencyintelligence.identity.application.port.out.RepositoryPermissionStore;
import java.util.UUID;

public final class AuthorizeRepositoryActionUseCase implements AuthorizeRepositoryAction {
    private final RepositoryPermissionStore permissions;

    public AuthorizeRepositoryActionUseCase(RepositoryPermissionStore permissions) { this.permissions = permissions; }

    @Override public void check(UUID userId, UUID organizationId, UUID repositoryId, String permission) {
        if (!permissions.permits(userId, organizationId, repositoryId, permission)) throw new PermissionDeniedException();
    }
}
