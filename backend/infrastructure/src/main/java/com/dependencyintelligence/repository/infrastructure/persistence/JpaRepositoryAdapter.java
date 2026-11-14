package com.dependencyintelligence.repository.infrastructure.persistence;

import com.dependencyintelligence.repository.application.exception.RepositoryAlreadyExistsException;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class JpaRepositoryAdapter implements RepositoryStore {
    private final EntityManager entityManager;

    public JpaRepositoryAdapter(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    @Transactional
    public Repository add(Repository repository) {
        try {
            entityManager.persist(RepositoryEntity.from(repository));
            entityManager.flush();
            return repository;
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_repository_provider_identity".equals(violation.getConstraintName())) {
                    throw new RepositoryAlreadyExistsException();
                }
            }
            throw exception;
        }
    }

    @Override
    public Optional<Repository> find(OrganizationId organizationId, RepositoryId repositoryId) {
        try (var results = entityManager.createQuery("select r from RepositoryEntity r where r.organizationId = :organizationId and r.id = :id", RepositoryEntity.class)
                .setParameter("organizationId", organizationId.value()).setParameter("id", repositoryId.value())
                .getResultStream()) {
            return results.findFirst().map(RepositoryEntity::toDomain);
        }
    }
}
