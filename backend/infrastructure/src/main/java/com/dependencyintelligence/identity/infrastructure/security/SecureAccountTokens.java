package com.dependencyintelligence.identity.infrastructure.security;

import com.dependencyintelligence.identity.application.exception.InvalidAccountTokenException;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SecureAccountTokens implements AccountTokenCodec {
    private final SecureRandom random = new SecureRandom();

    @Override public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override public String fingerprint(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new InvalidAccountTokenException();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
}
