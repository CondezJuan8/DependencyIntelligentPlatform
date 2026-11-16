package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
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
    public ManageInvitationsUseCase(OrganizationAccessStore access, InvitationStore store, InvitationDelivery delivery,
            AccountTokenCodec tokens, Clock clock, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("La vigencia debe ser positiva");
        this.access = access; this.store = store; this.delivery = delivery; this.tokens = tokens; this.clock = clock; this.lifetime = lifetime;
    }
    @Override public Invitation invite(UUID user, UUID org, String address, String roleCode) {
        var actor = manager(access, org, user);
        var role = OrganizationRole.parse(roleCode);
        assignable(actor, role);
        String email = new EmailAddress(address).value();
        if (store.activeMember(org, email)) throw new AccessConflictException("Ese correo ya tiene una membresía activa.");
        String token = tokens.generate();
        var invitation = store.create(org, email, tokens.fingerprint(token), actor.membershipId(), role, clock.instant(), clock.instant().plus(lifetime));
        delivery.send(email, access.lockOrganization(org), org, invitation.id(), token);
        access.audit(org, user, "invitation.create", "invitation", invitation.id());
        return invitation;
    }
    @Override public void revoke(UUID user, UUID org, UUID id) {
        var actor = manager(access, org, user);
        var invitation = store.find(org, id);
        invitation.roles().forEach(role -> assignable(actor, role));
        store.revoke(org, id);
        access.audit(org, user, "invitation.revoke", "invitation", id);
    }
    @Override public void accept(UUID user, UUID org, UUID id, String token) {
        access.lockOrganization(org);
        var invitation = store.find(org, id);
        String hash = tokens.fingerprint(token);
        if (!invitation.status().equals("PENDING") || !invitation.expiresAt().isAfter(clock.instant())
                || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII), invitation.hash().getBytes(StandardCharsets.US_ASCII))) {
            throw new AccessConflictException("La invitación no es válida, ha caducado o ya fue utilizada.");
        }
        var account = store.account(user);
        if (!account.verified() || !account.email().equals(invitation.email())) {
            throw new AccessConflictException("Inicia sesión con el correo invitado y verifícalo antes de aceptar.");
        }
        if (invitation.roles().size() != 1) throw new AccessConflictException("Solicita una invitación nueva con un único rol.");
        var role = invitation.roles().getFirst();
        assignable(access.actorByMembership(org, invitation.inviterMembershipId()), role);
        if (store.activeMember(org, account.email())) throw new AccessConflictException("Ya perteneces a esta organización.");
        store.accept(org, id, user, store.inviterUserId(org, invitation.inviterMembershipId()), role);
        access.audit(org, user, "invitation.accept", "invitation", id);
    }
}
