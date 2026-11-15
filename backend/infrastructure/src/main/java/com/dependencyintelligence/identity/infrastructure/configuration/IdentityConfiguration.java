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

@Configuration
public class IdentityConfiguration {
    @Bean RegisterAccount registerAccount(AccountStore store, PasswordHashing hashing) {
        return new RegisterAccountUseCase(store, hashing);
    }
    @Bean AuthenticateAccount authenticateAccount(AccountStore store, PasswordHashing hashing) {
        return new AuthenticateAccountUseCase(store, hashing);
    }
    @Bean FindActiveAccount findActiveAccount(AccountStore store) {
        return id -> store.findActive(id).orElseThrow(AuthenticationFailedException::new);
    }
    @Bean ManageOrganizations manageOrganizations(OrganizationStore store) {
        return new ManageOrganizationsUseCase(store);
    }
    @Bean AuthorizeRepositoryAction authorizeRepositoryAction(RepositoryPermissionStore store) {
        return new AuthorizeRepositoryActionUseCase(store);
    }
}
