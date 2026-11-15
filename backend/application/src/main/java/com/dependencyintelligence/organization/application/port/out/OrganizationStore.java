package com.dependencyintelligence.organization.application.port.out;

import com.dependencyintelligence.organization.application.dto.OrganizationResult;
import java.util.List;
import java.util.UUID;

public interface OrganizationStore {
    OrganizationResult createWithOwner(UUID userId, String name);
    List<OrganizationResult> listForUser(UUID userId);
}
