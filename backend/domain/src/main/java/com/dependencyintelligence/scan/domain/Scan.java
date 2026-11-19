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
    private final ScanOrigin origin;

    public Scan(UUID id, OrganizationId organizationId, RepositoryId repositoryId,
                String gitReference, ScanStatus status, Instant requestedAt,
                Instant startedAt, Instant finishedAt, String failureReason) {
        this(id, organizationId, repositoryId, gitReference, status, requestedAt,
            startedAt, finishedAt, failureReason, ScanOrigin.REPOSITORY_SCAN);
    }

    public Scan(UUID id, OrganizationId organizationId, RepositoryId repositoryId,
                String gitReference, ScanStatus status, Instant requestedAt,
                Instant startedAt, Instant finishedAt, String failureReason, ScanOrigin origin) {
        this.id = Objects.requireNonNull(id, "Scan id is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization id is required");
        this.repositoryId = Objects.requireNonNull(repositoryId, "Repository id is required");
        this.status = Objects.requireNonNull(status, "Status is required");
        this.requestedAt = Objects.requireNonNull(requestedAt, "Request time is required");
        this.origin = Objects.requireNonNull(origin, "Origin is required");
        if (origin == ScanOrigin.REPOSITORY_SCAN &&
                (gitReference == null || gitReference.isBlank() || gitReference.strip().length() > 255)) {
            throw new IllegalArgumentException("Git reference must contain between 1 and 255 characters");
        }
        if (origin == ScanOrigin.SBOM_IMPORT && (gitReference != null || status != ScanStatus.COMPLETED)) {
            throw new IllegalArgumentException("Manual import must be completed without a fabricated Git reference");
        }
        if (origin == ScanOrigin.LOCAL_UPLOAD_SCAN && gitReference != null) {
            throw new IllegalArgumentException("Local source must not have a Git reference");
        }
        this.gitReference = gitReference == null ? null : gitReference.strip();
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
    public ScanOrigin origin() { return origin; }

    public static Scan imported(UUID id, OrganizationId organizationId, RepositoryId repositoryId, Instant importedAt) {
        return new Scan(id, organizationId, repositoryId, null, ScanStatus.COMPLETED,
            importedAt, importedAt, importedAt, null, ScanOrigin.SBOM_IMPORT);
    }

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
