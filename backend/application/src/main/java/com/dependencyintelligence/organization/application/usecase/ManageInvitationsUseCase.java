package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.PasswordHashing;
import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.domain.PersonalName;
import com.dependencyintelligence.identity.domain.PasswordPolicy;
import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.identity.domain.EmailAddress;
import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import com.dependencyintelligence.organization.application.port.in.ManageInvitations;
import com.dependencyintelligence.organization.application.port.out.*;
import com.dependencyintelligence.organization.domain.OrganizationRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import static com.dependencyintelligence.organization.application.usecase.OrganizationAccessRules.*;

public final class ManageInvitationsUseCase implements ManageInvitations {
    private final OrganizationAccessStore access;
    private final InvitationStore store;
    private final InvitationDelivery delivery;
    private final AccountTokenCodec tokens;
    private final Clock clock;
    private final Duration lifetime;
    private final AccountStore accounts;
    private final PasswordHashing passwords;
    public ManageInvitationsUseCase(OrganizationAccessStore access, InvitationStore store, InvitationDelivery delivery,
            AccountTokenCodec tokens, Clock clock, Duration lifetime, AccountStore accounts, PasswordHashing passwords) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("La vigencia debe ser positiva");
        this.access = access; this.store = store; this.delivery = delivery; this.tokens = tokens; this.clock = clock; this.lifetime = lifetime;
        this.accounts=accounts; this.passwords=passwords;
    }
    @Override public Invitation invite(UUID user, UUID org, String address, String roleCode, UUID team, UUID repository) {
        var actor = manager(access, org, user);
        if (!actor.owner()) throw new PermissionDeniedException();
        var role = OrganizationRole.parse(roleCode);
        if (role != OrganizationRole.MEMBER && role != OrganizationRole.VIEWER) throw new IllegalArgumentException("Las invitaciones solo admiten miembros o lectores");
        store.target(org,team,repository);
        String email = new EmailAddress(address).value();
        String token = tokens.generate();
        var invitation = store.create(org, email, tokens.fingerprint(token), actor.membershipId(), role, clock.instant(), clock.instant().plus(lifetime),team,repository);
        delivery.send(email, access.lockOrganization(org), org, invitation.id(), token);
        access.audit(org, user, "invitation.create", "invitation", invitation.id());
        return invitation;
    }
    @Override public void revoke(UUID user, UUID org, UUID id) {
        var actor = manager(access, org, user);
        var invitation = store.find(org, id);
        if (!actor.owner() || !actor.membershipId().equals(invitation.inviterMembershipId())) throw new PermissionDeniedException();
        store.revoke(org, id);
        access.audit(org, user, "invitation.revoke", "invitation", id);
    }
    @Override public void accept(UUID user, UUID org, UUID id, String token) {
        access.lockOrganization(org);
        var invitation = store.find(org, id);
        validate(invitation,token);
        store.target(org,invitation.teamId(),invitation.repositoryId());
        var account = store.account(user);
        if (!account.verified() || !account.email().equals(invitation.email())) {
            throw new AccessConflictException("Inicia sesión con el correo invitado y verifícalo antes de aceptar.");
        }
        if (invitation.roles().size() != 1) throw new AccessConflictException("Solicita una invitación nueva con un único rol.");
        var role = invitation.roles().getFirst();
        if (!access.actorByMembership(org, invitation.inviterMembershipId()).owner()) throw new PermissionDeniedException();
        store.awaitOwner(org,id,user);
        access.audit(org, user, "invitation.accept", "invitation", id);
    }
    @Override public void approve(UUID user, UUID org, UUID id) {
        access.lockOrganization(org);
        var invitation=store.find(org,id);
        var actor=manager(access,org,user);
        if(!actor.owner() || !actor.membershipId().equals(invitation.inviterMembershipId())) throw new PermissionDeniedException();
        if(!invitation.status().equals("AWAITING_OWNER") || invitation.acceptedByUserId()==null || !invitation.expiresAt().isAfter(clock.instant())) throw new AccessConflictException("La invitación no está pendiente de aprobación o ha caducado");
        var account=accounts.findActive(invitation.acceptedByUserId()).orElseThrow(PermissionDeniedException::new);
        if(!account.emailVerified() || !account.admissionStatus().equals("APPROVED") || !account.email().equalsIgnoreCase(invitation.email())) throw new PermissionDeniedException();
        store.target(org,invitation.teamId(),invitation.repositoryId());
        store.accept(org,id,account.id(),store.inviterUserId(org,invitation.inviterMembershipId()),invitation.roles().getFirst(),user);
        access.audit(org,user,"invitation.approve","invitation",id);
    }
    @Override public AccountResult registerInvited(InvitedRegistrationCommand command) {
        access.lockOrganization(command.organizationId());
        var invitation=store.find(command.organizationId(),command.invitationId());
        validate(invitation,command.token());
        store.target(command.organizationId(),invitation.teamId(),invitation.repositoryId());
        if(!access.actorByMembership(command.organizationId(),invitation.inviterMembershipId()).owner()) throw new PermissionDeniedException();
        String name=new PersonalName(command.displayName()).value();
        PasswordPolicy.validate(command.password());
        return accounts.createInvited(invitation.email(),name,passwords.hash(command.password()));
    }
    private void validate(PendingInvitation invitation, String token) {
        String hash=tokens.fingerprint(token);
        if(!invitation.status().equals("PENDING") || !invitation.expiresAt().isAfter(clock.instant()) || invitation.roles().size()!=1
            || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),invitation.hash().getBytes(StandardCharsets.US_ASCII))) throw new AccessConflictException("La invitación no es válida, ha caducado o ya fue utilizada.");
    }
}
