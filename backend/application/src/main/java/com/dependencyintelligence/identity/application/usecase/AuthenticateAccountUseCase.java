package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.exception.AuthenticationFailedException;
import com.dependencyintelligence.identity.application.port.in.AuthenticateAccount;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.domain.EmailAddress;

public final class AuthenticateAccountUseCase implements AuthenticateAccount {
    private final AccountStore accounts;
    private final PasswordHashing passwords;
    private final String dummyHash;

    public AuthenticateAccountUseCase(AccountStore accounts, PasswordHashing passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.dummyHash = passwords.hash(java.util.UUID.randomUUID().toString());
    }

    @Override public AccountResult execute(String email, String password) {
        if (password == null || password.length() > 128) throw new AuthenticationFailedException();
        String normalized;
        try { normalized = new EmailAddress(email).value(); }
        catch (IllegalArgumentException exception) { throw new AuthenticationFailedException(); }
        var credentials = accounts.credentials(normalized);
        boolean matches = passwords.matches(password, credentials.map(value -> value.passwordHash()).orElse(dummyHash));
        if (credentials.isEmpty() || !matches || !credentials.get().active()) throw new AuthenticationFailedException();
        return credentials.get().account();
    }
}
