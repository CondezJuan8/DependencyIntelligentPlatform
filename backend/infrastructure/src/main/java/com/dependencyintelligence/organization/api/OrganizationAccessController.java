package com.dependencyintelligence.organization.api;

import com.dependencyintelligence.organization.application.dto.AccessModels.*;
import com.dependencyintelligence.organization.application.port.in.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}")
public class OrganizationAccessController {
    private final ManageOrganizationAccess access;
    private final ManageInvitations invitations;
    public OrganizationAccessController(ManageOrganizationAccess access, ManageInvitations invitations) { this.access = access; this.invitations = invitations; }
    private UUID actor(Principal principal) { return UUID.fromString(principal.getName()); }

    @GetMapping("/access") public Overview overview(Principal principal, @PathVariable UUID organizationId) { return access.overview(actor(principal), organizationId); }
    @PostMapping("/members/{membershipId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMember(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID membershipId, @Valid @RequestBody ChangeMember request) {
        access.changeMember(actor(principal), organizationId, membershipId, request.role(), request.status());
    }
    @PostMapping("/invitations") @ResponseStatus(HttpStatus.CREATED)
    public Invitation invite(Principal principal, @PathVariable UUID organizationId, @Valid @RequestBody Invite request) {
        return invitations.invite(actor(principal), organizationId, request.email(), request.role(),request.teamId(),request.repositoryId());
    }
    @PostMapping("/invitations/{invitationId}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID invitationId) { invitations.revoke(actor(principal), organizationId, invitationId); }
    @PostMapping("/invitations/{invitationId}/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID invitationId, @Valid @RequestBody Accept request) {
        invitations.accept(actor(principal), organizationId, invitationId, request.token());
    }
    @PostMapping("/invitations/{invitationId}/approve") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(Principal principal,@PathVariable UUID organizationId,@PathVariable UUID invitationId) { invitations.approve(actor(principal),organizationId,invitationId); }
    @PostMapping("/teams") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> createTeam(Principal principal, @PathVariable UUID organizationId, @Valid @RequestBody TeamName request) {
        return Map.of("id", access.createTeam(actor(principal), organizationId, request.name()));
    }
    @PostMapping("/teams/{teamId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameTeam(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID teamId, @Valid @RequestBody TeamName request) { access.renameTeam(actor(principal), organizationId, teamId, request.name()); }
    @PostMapping("/teams/{teamId}/delete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID teamId) { access.deleteTeam(actor(principal), organizationId, teamId); }
    @PostMapping("/teams/{teamId}/members/{membershipId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void teamMember(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID teamId, @PathVariable UUID membershipId, @Valid @RequestBody TeamMember request) {
        access.setTeamMember(actor(principal), organizationId, teamId, membershipId, request.included());
    }
    @PostMapping("/teams/{teamId}/repositories/{repositoryId}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permissions(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID teamId, @PathVariable UUID repositoryId, @Valid @RequestBody Permissions request) {
        access.setRepositoryPermissions(actor(principal), organizationId, teamId, repositoryId, request.permissions());
    }
    @PostMapping("/members/{membershipId}/repositories/{repositoryId}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void memberPermissions(Principal principal, @PathVariable UUID organizationId, @PathVariable UUID membershipId, @PathVariable UUID repositoryId, @Valid @RequestBody Permissions request) {
        access.setMemberRepositoryPermissions(actor(principal), organizationId, membershipId, repositoryId, request.permissions());
    }
    public record ChangeMember(@NotBlank @Size(max = 10) String role, @NotBlank @Size(max = 10) String status) { }
    public record Invite(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 10) String role,@NotNull UUID teamId,@NotNull UUID repositoryId) { }
    public record Accept(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "Accept[REDACTED]"; }
    }
    public record TeamName(@NotBlank @Size(max = 200) String name) { }
    public record TeamMember(@NotNull Boolean included) { }
    public record Permissions(@NotNull List<@NotBlank @Size(max = 50) String> permissions) { }
}
