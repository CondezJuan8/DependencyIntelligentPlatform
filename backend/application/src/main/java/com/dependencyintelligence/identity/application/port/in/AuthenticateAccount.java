package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.AccountResult;

public interface AuthenticateAccount {
    AccountResult execute(String email, String password);
}
