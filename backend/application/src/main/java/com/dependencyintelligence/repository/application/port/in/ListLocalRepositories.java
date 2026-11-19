package com.dependencyintelligence.repository.application.port.in;

import com.dependencyintelligence.repository.application.dto.LocalRepositorySummary;
import java.util.List;
import java.util.UUID;

public interface ListLocalRepositories {
    List<LocalRepositorySummary> execute(UUID organizationId);
}
