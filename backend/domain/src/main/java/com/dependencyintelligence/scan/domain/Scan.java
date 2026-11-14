package com.dependencyintelligence.scan.domain;

import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable scan aggregate; terminal states cannot be restarted or overwritten. */
public final class Scan {
    private final UUID id;
    private final OrganizationId organizationId;
    private final RepositoryId repositoryId;
    private final String gitReference;
    private final ScanStatus status;
    private final Instant requestedAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String failureReason;

    public Scan(UUID id, OrganizationId organizationId, RepositoryId repositoryId,
                String gitReference, ScanStatus status, Instant requestedAt,
                Instant startedAt, Instant finishedAt, String failureReason) {
        this.id = Objects.requireNonNull(id, "Scan id is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization id is required");
        this.repositoryId = Objects.requireNonNull(repositoryId, "Repository id is required");
        this.status = Objects.requireNonNull(status, "Status is required");
        this.requestedAt = Objects.requireNonNull(requestedAt, "Request time is required");
        if (gitReference == null || gitReference.isBlank() || gitReference.strip().length() > 255) {
            throw new IllegalArgumentException("Git reference must contain between 1 and 255 characters");
        }
        this.gitReference = gitReference.strip();
        boolean valid = switch (status) {
            case PENDING -> startedAt == null && finishedAt == null && failureReason == null;
            case RUNNING -> startedAt != null && finishedAt == null && failureReason == null;
            case COMPLETED -> startedAt != null && finishedAt != null && failureReason == null;
            case FAILED -> startedAt != null && finishedAt != null
                && failureReason != null && !failureReason.isBlank();
        };
        if (!valid || (startedAt != null && startedAt.isBefore(requestedAt))
                || (finishedAt != null && finishedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("Invalid scan state or chronology");
        }
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.failureReason = failureReason;
    }

    public UUID id() { return id; }
    public OrganizationId organizationId() { return organizationId; }
    public RepositoryId repositoryId() { return repositoryId; }
    public String gitReference() { return gitReference; }
    public ScanStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String failureReason() { return failureReason; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof Scan scan && id.equals(scan.id);
    }

    @Override public int hashCode() { return id.hashCode(); }

    public static Scan request(UUID id, OrganizationId organizationId, RepositoryId repositoryId,
                               String gitReference, Instant now) {
        return new Scan(id, organizationId, repositoryId, gitReference, ScanStatus.PENDING, now, null, null, null);
    }

    public Scan start(Instant now) {
        requireStatus(ScanStatus.PENDING);
        return new Scan(id, organizationId, repositoryId, gitReference, ScanStatus.RUNNING, requestedAt, now, null, null);
    }

    public Scan complete(Instant now) {
        requireStatus(ScanStatus.RUNNING);
        return new Scan(id, organizationId, repositoryId, gitReference, ScanStatus.COMPLETED, requestedAt, startedAt, now, null);
    }

    public Scan fail(Instant now, String reason) {
        requireStatus(ScanStatus.RUNNING);
        return new Scan(id, organizationId, repositoryId, gitReference, ScanStatus.FAILED, requestedAt, startedAt, now, reason);
    }

    private void requireStatus(ScanStatus expected) {
        if (status != expected) { throw new IllegalStateException("Scan must be " + expected); }
    }
}
