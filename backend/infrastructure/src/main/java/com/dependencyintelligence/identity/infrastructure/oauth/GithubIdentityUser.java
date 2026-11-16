package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.application.dto.ExternalIdentityProof;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import java.util.List;
import java.util.Map;

public class GithubIdentityUser extends DefaultOAuth2User {
    private final ExternalIdentityProof proof;

    public GithubIdentityUser(ExternalIdentityProof proof) {
        super(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), Map.of("id", proof.subject()), "id");
        this.proof = proof;
    }
    public ExternalIdentityProof proof() { return proof; }
    @Override public String toString() { return "GithubIdentityUser[REDACTED]"; }
}
