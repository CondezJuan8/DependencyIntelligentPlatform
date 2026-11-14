package com.dependencyintelligence.repository.application.port.out;

import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.util.Optional;

/** All operations require organization context; no unscoped read is exposed. */
public interface RepositoryStore {
    Repository add(Repository repository);
    Optional<Repository> find(OrganizationId organizationId, RepositoryId repositoryId);
}
