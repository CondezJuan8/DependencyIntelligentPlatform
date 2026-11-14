package com.dependencyintelligence.sbom.application.dto;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public record ImportSbomCommand(UUID organizationId, UUID repositoryId, InputStream content) {
    public ImportSbomCommand {
        Objects.requireNonNull(organizationId, "Organization is required");
        Objects.requireNonNull(repositoryId, "Repository is required");
        Objects.requireNonNull(content, "SBOM content is required");
    }
}
