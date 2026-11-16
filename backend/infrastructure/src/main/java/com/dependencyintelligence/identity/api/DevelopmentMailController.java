package com.dependencyintelligence.identity.api;
import com.dependencyintelligence.identity.application.port.in.ReadDevelopmentMail;
import com.dependencyintelligence.identity.application.port.in.FindActiveAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
public class DevelopmentMailController{
 private final ReadDevelopmentMail mail;private final FindActiveAccount accounts;private final boolean enabled;
 public DevelopmentMailController(ReadDevelopmentMail mail,FindActiveAccount accounts,@Value("${app.mail.preview-enabled:false}")boolean enabled){this.mail=mail;this.accounts=accounts;this.enabled=enabled;}
 @GetMapping("/api/account/mail-preview/availability") public Map<String,Boolean> availability(Principal principal){var account=accounts.execute(UUID.fromString(principal.getName()));return Map.of("enabled",enabled,"ownMail",account.accountType().equals("OWNER")||account.platformAdmin());}
 @GetMapping("/api/account/mail-preview") public List<ReadDevelopmentMail.Message> own(Principal principal){UUID user=UUID.fromString(principal.getName());return mail.account(user,user);}
 @GetMapping("/api/platform/accounts/{userId}/mail-preview") public List<ReadDevelopmentMail.Message> owner(Principal principal,@PathVariable UUID userId){UUID actor=UUID.fromString(principal.getName());var account=accounts.execute(actor);if(!account.platformAdmin()||!account.emailVerified()||!account.admissionStatus().equals("APPROVED"))throw new com.dependencyintelligence.identity.application.exception.PermissionDeniedException();return mail.account(actor,userId);}
 @GetMapping("/api/organizations/{organizationId}/invitations/{invitationId}/mail-preview")public List<ReadDevelopmentMail.Message> invitation(Principal principal,@PathVariable UUID organizationId,@PathVariable UUID invitationId){return mail.invitation(UUID.fromString(principal.getName()),organizationId,invitationId);}
}
