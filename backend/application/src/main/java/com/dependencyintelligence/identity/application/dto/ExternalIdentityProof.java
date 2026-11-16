package com.dependencyintelligence.identity.application.dto;

import com.dependencyintelligence.identity.domain.IdentityProvider;
import java.util.Objects;

/** Created only by the adapter after the provider protocol has authenticated the subject. */
public record ExternalIdentityProof(IdentityProvider provider, String subject, String email,
                                    boolean trustedEmail, String displayName) {
    public ExternalIdentityProof {
        Objects.requireNonNull(provider);
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new IllegalArgumentException("Invalid external subject");
        }
    }
    @Override public String toString() { return "ExternalIdentityProof[REDACTED]"; }
}
