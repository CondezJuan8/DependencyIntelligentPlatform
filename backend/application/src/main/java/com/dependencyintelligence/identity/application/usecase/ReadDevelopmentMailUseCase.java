package com.dependencyintelligence.identity.application.usecase;
import com.dependencyintelligence.identity.application.port.in.ReadDevelopmentMail;
import com.dependencyintelligence.identity.application.port.out.AccountStore;
import com.dependencyintelligence.identity.application.port.out.DevelopmentMailbox;
import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.organization.application.port.out.InvitationStore;
import com.dependencyintelligence.organization.application.port.out.OrganizationAccessStore;
import java.util.List;
import java.util.UUID;
public final class ReadDevelopmentMailUseCase implements ReadDevelopmentMail{
    private final AccountStore accounts;private final DevelopmentMailbox mailbox;private final InvitationStore invitations;private final OrganizationAccessStore access;
    public ReadDevelopmentMailUseCase(AccountStore accounts,DevelopmentMailbox mailbox,InvitationStore invitations,OrganizationAccessStore access){this.accounts=accounts;this.mailbox=mailbox;this.invitations=invitations;this.access=access;}
    private boolean administrator(com.dependencyintelligence.identity.application.dto.AccountResult user){return user.platformAdmin()&&user.emailVerified()&&user.admissionStatus().equals("APPROVED");}
    public List<Message> account(UUID actor,UUID recipient){
        var user=accounts.findActive(actor).orElseThrow(PermissionDeniedException::new);
        if(!administrator(user)&&(!actor.equals(recipient)||!user.accountType().equals("OWNER")))throw new PermissionDeniedException();
        var owner=accounts.findActive(recipient).orElseThrow(PermissionDeniedException::new);
        return mailbox.find(owner.email(),"#");
    }
    public List<Message> invitation(UUID actor,UUID organization,UUID id){
        var user=accounts.findActive(actor).orElseThrow(PermissionDeniedException::new);
        access.lockOrganization(organization);var invitation=invitations.find(organization,id);
        if(!administrator(user)){
            var member=access.actor(organization,actor);
            if(!member.owner()||!member.membershipId().equals(invitation.inviterMembershipId()))throw new PermissionDeniedException();
        }
        return mailbox.find(invitation.email(),"#organization="+organization+"&invitation="+id+"&invite-token=");
    }
}
