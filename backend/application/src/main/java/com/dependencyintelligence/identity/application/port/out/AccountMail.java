package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.domain.AccountTokenPurpose;

public interface AccountMail {
    void sendLink(String email, AccountTokenPurpose purpose, String token);
}
