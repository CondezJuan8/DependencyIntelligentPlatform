package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.BootstrapPlatformAdministratorCommand;
import com.dependencyintelligence.identity.application.port.in.BootstrapPlatformAdministrator;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.application.port.out.PlatformAdministratorBootstrapStore;
import com.dependencyintelligence.identity.domain.EmailAddress;
import com.dependencyintelligence.identity.domain.PasswordPolicy;
import com.dependencyintelligence.identity.domain.PersonalName;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class BootstrapPlatformAdministratorUseCase implements BootstrapPlatformAdministrator {
    private final PlatformAdministratorBootstrapStore administrators;
    private final PasswordHashing passwords;

    public BootstrapPlatformAdministratorUseCase(PlatformAdministratorBootstrapStore administrators, PasswordHashing passwords) {
        this.administrators = administrators;
        this.passwords = passwords;
    }

    @Override public UUID execute(BootstrapPlatformAdministratorCommand command) {
        if (command == null) throw new IllegalArgumentException("Los datos del administrador inicial son obligatorios");
        String email = new EmailAddress(command.email()).value();
        String displayName = new PersonalName(command.displayName()).value();
        PasswordPolicy.validate(command.password());
        if (command.password() == null || command.passwordConfirmation() == null || !MessageDigest.isEqual(
                command.password().getBytes(StandardCharsets.UTF_8),
                command.passwordConfirmation().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("La confirmación de contraseña no coincide");
        }
        UUID id = UUID.randomUUID();
        administrators.createOnlyWhenDatabaseIsEmpty(id, email, displayName, passwords.hash(command.password()));
        return id;
    }
}
