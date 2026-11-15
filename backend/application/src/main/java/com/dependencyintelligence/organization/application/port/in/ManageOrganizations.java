package com.dependencyintelligence.organization.application.port.in;

import com.dependencyintelligence.organization.application.dto.OrganizationResult;
import java.util.List;
import java.util.UUID;

public interface ManageOrganizations {
    OrganizationResult create(UUID userId, String name);
    List<OrganizationResult> list(UUID userId);
}
