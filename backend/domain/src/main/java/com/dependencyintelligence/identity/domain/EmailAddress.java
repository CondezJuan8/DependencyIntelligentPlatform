package com.dependencyintelligence.identity.domain;

import java.util.Locale;

public record EmailAddress(String value) {
    public EmailAddress {
        if (value == null) throw new IllegalArgumentException("El correo es obligatorio");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("El correo no es válido");
        }
    }
}
