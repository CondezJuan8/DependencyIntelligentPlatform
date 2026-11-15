package com.dependencyintelligence.identity.application.port.out;

public interface AccountTokenCodec {
    String generate();
    String fingerprint(String token);
}
