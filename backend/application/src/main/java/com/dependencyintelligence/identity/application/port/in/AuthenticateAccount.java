package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.AuthenticatedAccount;

public interface AuthenticateAccount {
    AuthenticatedAccount execute(String email, String password);
}
