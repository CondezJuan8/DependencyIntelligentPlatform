package com.dependencyintelligence.identity.infrastructure.security;

import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Pbkdf2PasswordAdapter implements PasswordHashing {
    private static final String PREFIX = "{pbkdf2-sha256-600000}";
    private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 16, 600_000, 256);

    @Override public String hash(String password) { return PREFIX + encoder.encode(password); }

    @Override public boolean matches(String password, String hash) {
        return hash.startsWith(PREFIX) && encoder.matches(password, hash.substring(PREFIX.length()));
    }
}
