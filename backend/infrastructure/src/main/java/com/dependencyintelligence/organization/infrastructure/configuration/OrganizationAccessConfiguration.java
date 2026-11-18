package com.dependencyintelligence.organization.infrastructure.configuration;

import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.port.in.*;
import com.dependencyintelligence.organization.application.port.out.*;
import com.dependencyintelligence.organization.application.usecase.*;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Configuration
public class OrganizationAccessConfiguration {
    @Bean ManageOrganizationAccess manageOrganizationAccess(OrganizationAccessStore store, TenantTransactions transactions) {
        var delegate = new ManageOrganizationAccessUseCase(store);
        return new ManageOrganizationAccess() {
            public Overview overview(UUID actor, UUID org) { return transactions.execute(org, () -> delegate.overview(actor, org)); }
            public void changeMember(UUID actor, UUID org, UUID member, String role, String status) {
                transactions.execute(org, () -> { delegate.changeMember(actor, org, member, role, status); return null; });
            }
            public UUID createTeam(UUID actor, UUID org, String name) { return transactions.execute(org, () -> delegate.createTeam(actor, org, name)); }
            public void renameTeam(UUID actor, UUID org, UUID team, String name) {
                transactions.execute(org, () -> { delegate.renameTeam(actor, org, team, name); return null; });
            }
            public void deleteTeam(UUID actor, UUID org, UUID team) {
                transactions.execute(org, () -> { delegate.deleteTeam(actor, org, team); return null; });
            }
            public void setTeamMember(UUID actor, UUID org, UUID team, UUID member, boolean included) {
                transactions.execute(org, () -> { delegate.setTeamMember(actor, org, team, member, included); return null; });
            }
            public void setRepositoryPermissions(UUID actor, UUID org, UUID team, UUID repository, List<String> permissions) {
                transactions.execute(org, () -> { delegate.setRepositoryPermissions(actor, org, team, repository, permissions); return null; });
            }
            public void setMemberRepositoryPermissions(UUID actor, UUID org, UUID membership, UUID repository, List<String> permissions) {
                transactions.execute(org, () -> { delegate.setMemberRepositoryPermissions(actor, org, membership, repository, permissions); return null; });
            }
        };
    }
    @Bean ManageInvitations manageInvitations(OrganizationAccessStore access, InvitationStore store, InvitationDelivery delivery,
            AccountTokenCodec tokens, Clock clock, TenantTransactions transactions,
            com.dependencyintelligence.identity.application.port.out.AccountStore accounts,
            com.dependencyintelligence.identity.application.port.out.PasswordHashing passwords,
            @Value("${app.organization.invitation-lifetime:PT72H}") Duration lifetime) {
        var delegate = new ManageInvitationsUseCase(access, store, delivery, tokens, clock, lifetime,accounts,passwords);
        return new ManageInvitations() {
            public Invitation invite(UUID actor, UUID org, String email, String role, UUID team, UUID repository) {
                return transactions.execute(org, () -> delegate.invite(actor, org, email, role,team,repository));
            }
            public void revoke(UUID actor, UUID org, UUID invitation) {
                transactions.execute(org, () -> { delegate.revoke(actor, org, invitation); return null; });
            }
            public void accept(UUID actor, UUID org, UUID invitation, String token) {
                transactions.execute(org, () -> { delegate.accept(actor, org, invitation, token); return null; });
            }
            public void approve(UUID actor, UUID org, UUID invitation) {
                transactions.execute(org, () -> { delegate.approve(actor,org,invitation); return null; });
            }
            public com.dependencyintelligence.identity.application.dto.AccountResult registerInvited(com.dependencyintelligence.identity.application.dto.InvitedRegistrationCommand command) {
                return transactions.execute(command.organizationId(), () -> delegate.registerInvited(command));
            }
        };
    }
}
