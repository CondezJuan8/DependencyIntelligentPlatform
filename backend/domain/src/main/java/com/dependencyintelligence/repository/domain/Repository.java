package com.dependencyintelligence.repository.domain;

import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Instant;
import java.util.Objects;

/** Aggregate root. Provider identity is unique within an organization. */
public final class Repository {
    private final RepositoryId id;
    private final OrganizationId organizationId;
    private final String provider;
    private final String externalId;
    private final String fullName;
    private final String defaultBranch;
    private final Instant createdAt;

    public Repository(RepositoryId id, OrganizationId organizationId, String provider,
                      String externalId, String fullName, String defaultBranch, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Repository id is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization id is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.provider = required(provider, "Provider", 40).toLowerCase(java.util.Locale.ROOT);
        this.externalId = required(externalId, "External id", 255);
        this.fullName = required(fullName, "Full name", 512);
        this.defaultBranch = required(defaultBranch, "Default branch", 255);
    }

    public RepositoryId id() { return id; }
    public OrganizationId organizationId() { return organizationId; }
    public String provider() { return provider; }
    public String externalId() { return externalId; }
    public String fullName() { return fullName; }
    public String defaultBranch() { return defaultBranch; }
    public Instant createdAt() { return createdAt; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof Repository repository && id.equals(repository.id);
    }

    @Override public int hashCode() { return id.hashCode(); }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.strip().length() > maximum) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + maximum + " characters");
        }
        return value.strip();
    }
}
