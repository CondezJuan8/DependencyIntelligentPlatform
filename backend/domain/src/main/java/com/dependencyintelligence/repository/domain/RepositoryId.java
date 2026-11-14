package com.dependencyintelligence.repository.domain;

import java.util.Objects;
import java.util.UUID;

public record RepositoryId(UUID value) {
    public RepositoryId { Objects.requireNonNull(value, "Repository id is required"); }
}
