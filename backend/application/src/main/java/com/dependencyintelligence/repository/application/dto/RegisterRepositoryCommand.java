package com.dependencyintelligence.repository.application.dto;

import java.util.UUID;

public record RegisterRepositoryCommand(UUID organizationId, String provider, String externalId,
                                        String fullName, String defaultBranch) { }
