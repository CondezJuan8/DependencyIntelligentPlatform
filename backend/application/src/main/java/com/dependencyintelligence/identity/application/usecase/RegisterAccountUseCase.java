package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.dto.RegisterAccountCommand;
import com.dependencyintelligence.identity.application.port.in.RegisterAccount;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.domain.EmailAddress;
import com.dependencyintelligence.identity.domain.PasswordPolicy;

public final class RegisterAccountUseCase implements RegisterAccount {
    private final AccountStore accounts;
    private final PasswordHashing passwords;

    public RegisterAccountUseCase(AccountStore accounts, PasswordHashing passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override public AccountResult execute(RegisterAccountCommand command) {
        String email = new EmailAddress(command.email()).value();
        String name = new com.dependencyintelligence.identity.domain.PersonalName(command.displayName()).value();
        String password = command.password();
        PasswordPolicy.validate(password);
        return accounts.create(email, name, passwords.hash(password));
    }
}
