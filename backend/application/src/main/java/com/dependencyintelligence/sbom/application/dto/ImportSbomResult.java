package com.dependencyintelligence.sbom.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportSbomResult(UUID scanId, UUID organizationId, UUID repositoryId,
                               int componentCount, Instant importedAt, String sha256) { }
