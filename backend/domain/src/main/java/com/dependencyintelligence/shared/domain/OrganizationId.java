package com.dependencyintelligence.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {
    public OrganizationId { Objects.requireNonNull(value, "Organization id is required"); }
}
