package com.dependencyintelligence.organization.infrastructure.configuration;

import com.dependencyintelligence.organization.application.dto.WorkspaceSettingsResult;
import com.dependencyintelligence.organization.application.port.in.ManageWorkspaceSettings;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import com.dependencyintelligence.organization.application.port.out.WorkspaceSettingsStore;
import com.dependencyintelligence.organization.application.usecase.ManageWorkspaceSettingsUseCase;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

@Configuration
public class WorkspaceSettingsConfiguration {
    @Bean ManageWorkspaceSettings manageWorkspaceSettings(OrganizationAccessStore access, WorkspaceSettingsStore store,
            TenantTransactions tenants, JdbcTemplate jdbc) {
        var useCase = new ManageWorkspaceSettingsUseCase(access, store);
        return new ManageWorkspaceSettings() {
            private <T> T inTenant(UUID actor, UUID organization, java.util.function.Supplier<T> operation) {
                return tenants.execute(organization, () -> {
                    jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, actor.toString());
                    return operation.get();
                });
            }
            public WorkspaceSettingsResult get(UUID actor, UUID organization) {
                return inTenant(actor, organization, () -> useCase.get(actor, organization));
            }
            public WorkspaceSettingsResult update(UUID actor, UUID organization, String theme, String language) {
                return inTenant(actor, organization, () -> useCase.update(actor, organization, theme, language));
            }
        };
    }
}
