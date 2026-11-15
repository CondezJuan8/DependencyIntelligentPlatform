package com.dependencyintelligence.organization.api;

import com.dependencyintelligence.organization.application.dto.OrganizationResult;
import com.dependencyintelligence.organization.application.port.in.ManageOrganizations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final ManageOrganizations organizations;

    public OrganizationController(ManageOrganizations organizations) { this.organizations = organizations; }

    @GetMapping
    public List<OrganizationResult> list(Principal principal) {
        return organizations.list(UUID.fromString(principal.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResult create(Principal principal, @Valid @RequestBody CreateOrganizationRequest request) {
        return organizations.create(UUID.fromString(principal.getName()), request.name());
    }

    public record CreateOrganizationRequest(@NotBlank @Size(max = 200) String name) { }
}
