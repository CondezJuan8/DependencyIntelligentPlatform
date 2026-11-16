package com.dependencyintelligence.identity.application.dto;

import com.dependencyintelligence.identity.domain.IdentityProvider;
import java.util.List;

public record ConnectedIdentities(List<IdentityProvider> providers, boolean passwordAvailable) {
    public ConnectedIdentities { providers = List.copyOf(providers); }
}
