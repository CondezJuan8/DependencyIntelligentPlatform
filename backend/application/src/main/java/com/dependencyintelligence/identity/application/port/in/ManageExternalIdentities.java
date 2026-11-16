package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.domain.IdentityProvider;
import java.util.Set;
import java.util.UUID;

public interface ManageExternalIdentities {
    AuthenticatedAccount signIn(ExternalIdentityProof proof);
    AuthenticatedAccount link(UUID userId, long credentialVersion, ExternalIdentityProof proof);
    ConnectedIdentities connections(UUID userId);
    void unlink(UUID userId, long credentialVersion, IdentityProvider provider, Set<IdentityProvider> enabledProviders);
}
