package com.dependencyintelligence.organization.application.port.out;

import com.dependencyintelligence.shared.domain.OrganizationId;

public interface OrganizationLookup {
    boolean exists(OrganizationId organizationId);
}
