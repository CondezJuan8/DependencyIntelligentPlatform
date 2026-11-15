package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.dto.RegisterAccountCommand;
import com.dependencyintelligence.identity.application.port.in.RegisterAccount;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.domain.EmailAddress;

public final class RegisterAccountUseCase implements RegisterAccount {
    private final AccountStore accounts;
    private final PasswordHashing passwords;

    public RegisterAccountUseCase(AccountStore accounts, PasswordHashing passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override public AccountResult execute(RegisterAccountCommand command) {
        String email = new EmailAddress(command.email()).value();
        String name = command.displayName() == null ? "" : command.displayName().strip();
        if (name.isEmpty() || name.length() > 200) {
            throw new IllegalArgumentException("El nombre debe tener entre 1 y 200 caracteres");
        }
        String password = command.password();
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15
                || password.length() > 128) {
            throw new IllegalArgumentException("La contraseña debe tener entre 15 y 128 caracteres");
        }
        return accounts.create(email, name, passwords.hash(password));
    }
}
