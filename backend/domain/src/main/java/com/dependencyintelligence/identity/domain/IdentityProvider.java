package com.dependencyintelligence.identity.domain;

import java.util.Locale;

public enum IdentityProvider {
    GOOGLE, GITHUB;

    public String code() { return name().toLowerCase(Locale.ROOT); }
    public static IdentityProvider fromCode(String code) {
        for (var provider : values()) {
            if (provider.code().equals(code)) return provider;
        }
        throw new IllegalArgumentException("Proveedor de identidad no válido");
    }
}
