package com.dependencyintelligence.identity.infrastructure.configuration;

import com.dependencyintelligence.identity.application.exception.AuthenticationFailedException;
import com.dependencyintelligence.identity.application.port.in.*;
import com.dependencyintelligence.identity.application.port.out.*;
import com.dependencyintelligence.identity.application.usecase.*;
import com.dependencyintelligence.organization.application.port.in.ManageOrganizations;
import com.dependencyintelligence.organization.application.port.out.OrganizationStore;
import com.dependencyintelligence.organization.application.usecase.ManageOrganizationsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class IdentityConfiguration {
    @Bean BootstrapPlatformAdministrator bootstrapPlatformAdministrator(PlatformAdministratorBootstrapStore store, PasswordHashing hashing) {
        return new BootstrapPlatformAdministratorUseCase(store, hashing);
    }
    @Bean RegisterAccount registerAccount(AccountStore store, PasswordHashing hashing, PendingAdmissionNotifier notifier) {
        return new RegisterAccountUseCase(store, hashing, notifier);
    }
    @Bean AuthenticateAccount authenticateAccount(AccountStore store, PasswordHashing hashing) {
        return new AuthenticateAccountUseCase(store, hashing);
    }
    @Bean FindActiveAccount findActiveAccount(AccountStore store) {
        return id -> store.findActive(id).orElseThrow(AuthenticationFailedException::new);
    }
    @Bean ValidateAccountSession validateAccountSession(AccountStore store) { return store::sessionCurrent; }
    @Bean ManageExternalIdentities manageExternalIdentities(ExternalIdentityStore store, Clock clock, PlatformTransactionManager manager) {
        return new TransactionalExternalIdentities(new ManageExternalIdentitiesUseCase(store, clock), manager);
    }
    @Bean RecoverAccount recoverAccount(AccountRecoveryStore store, AccountMail mail, AccountTokenCodec tokens,
            PasswordHashing passwords, Clock clock, PlatformTransactionManager manager,
            @Value("${app.security.account-tokens.verification-lifetime}") Duration verificationLifetime,
            @Value("${app.security.account-tokens.reset-lifetime}") Duration resetLifetime,
            @Value("${app.security.account-tokens.resend-cooldown}") Duration cooldown) {
        var useCase = new RecoverAccountUseCase(store, mail, tokens, passwords, clock, verificationLifetime, resetLifetime, cooldown);
        return new TransactionalAccountRecovery(useCase, manager);
    }
    @Bean ManageOrganizations manageOrganizations(OrganizationStore store) {
        return new ManageOrganizationsUseCase(store);
    }
    @Bean AuthorizeRepositoryAction authorizeRepositoryAction(RepositoryPermissionStore store) {
        return new AuthorizeRepositoryActionUseCase(store);
    }
}
