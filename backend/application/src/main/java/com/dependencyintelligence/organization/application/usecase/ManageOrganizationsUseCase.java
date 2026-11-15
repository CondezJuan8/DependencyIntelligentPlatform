package com.dependencyintelligence.organization.application.usecase;

import com.dependencyintelligence.organization.application.dto.OrganizationResult;
import com.dependencyintelligence.organization.application.port.in.ManageOrganizations;
import com.dependencyintelligence.organization.application.port.out.OrganizationStore;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ManageOrganizationsUseCase implements ManageOrganizations {
    private final OrganizationStore organizations;

    public ManageOrganizationsUseCase(OrganizationStore organizations) { this.organizations = organizations; }

    @Override public OrganizationResult create(UUID userId, String name) {
        Objects.requireNonNull(userId);
        if (name == null || name.isBlank() || name.strip().length() > 200) {
            throw new IllegalArgumentException("El nombre de organización debe tener entre 1 y 200 caracteres");
        }
        return organizations.createWithOwner(userId, name.strip());
    }

    @Override public List<OrganizationResult> list(UUID userId) {
        return organizations.listForUser(Objects.requireNonNull(userId));
    }
}
