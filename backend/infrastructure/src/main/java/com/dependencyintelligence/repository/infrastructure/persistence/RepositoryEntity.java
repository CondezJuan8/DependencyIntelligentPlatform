package com.dependencyintelligence.repository.infrastructure.persistence;

import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repositories")
public class RepositoryEntity {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 40) private String provider;
    @Column(name = "external_id", nullable = false) private String externalId;
    @Column(name = "full_name", nullable = false, length = 512) private String fullName;
    @Column(name = "default_branch", nullable = false) private String defaultBranch;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RepositoryEntity() { }

    static RepositoryEntity from(Repository repository) {
        var entity = new RepositoryEntity();
        entity.id = repository.id().value();
        entity.organizationId = repository.organizationId().value();
        entity.provider = repository.provider();
        entity.externalId = repository.externalId();
        entity.fullName = repository.fullName();
        entity.defaultBranch = repository.defaultBranch();
        entity.createdAt = repository.createdAt();
        return entity;
    }

    Repository toDomain() {
        return new Repository(new RepositoryId(id), new OrganizationId(organizationId), provider,
            externalId, fullName, defaultBranch, createdAt);
    }
}
