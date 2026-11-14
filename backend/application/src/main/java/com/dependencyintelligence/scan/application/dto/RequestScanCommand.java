package com.dependencyintelligence.scan.application.dto;

import java.util.UUID;

public record RequestScanCommand(UUID organizationId, UUID repositoryId, String gitReference) { }
