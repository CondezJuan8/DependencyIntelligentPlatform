package com.dependencyintelligence.identity.domain;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validate(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15
                || password.length() > 128) {
            throw new IllegalArgumentException("La contraseña debe tener entre 15 y 128 caracteres");
        }
    }
}
