package com.dependencyintelligence.scan.domain;

import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ScanTest {
    private final Instant now = Instant.parse("2026-11-14T12:00:00Z");

    private Scan pending() {
        return Scan.request(UUID.randomUUID(), new OrganizationId(UUID.randomUUID()),
            new RepositoryId(UUID.randomUUID()), "main", now);
    }

    @Test void completedScanCannotBeRestarted() {
        var original = pending();
        var completed = original.start(now.plusSeconds(1)).complete(now.plusSeconds(2));
        assertEquals(original, completed);
        assertEquals(original.hashCode(), completed.hashCode());
        assertEquals(ScanStatus.COMPLETED, completed.status());
        assertThrows(IllegalStateException.class, () -> completed.start(now.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> completed.fail(now.plusSeconds(3), "late failure"));
    }

    @Test void pendingScanCannotCompleteBeforeStarting() {
        assertThrows(IllegalStateException.class, () -> pending().complete(now));
    }

    @Test void rejectsInvalidChronologyAndMissingFailureReason() {
        assertThrows(IllegalArgumentException.class, () -> pending().start(now.minusSeconds(1)));
        var running = pending().start(now.plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> running.complete(now));
        assertThrows(IllegalArgumentException.class, () -> running.fail(now.plusSeconds(2), " "));
        assertEquals(ScanStatus.FAILED, running.fail(now.plusSeconds(2), "Scanner timed out").status());
    }

    @Test void rejectsInvalidReferencesAndForgedStates() {
        assertThrows(IllegalArgumentException.class, () -> Scan.request(UUID.randomUUID(),
            new OrganizationId(UUID.randomUUID()), new RepositoryId(UUID.randomUUID()), " ", now));
        var scan = pending();
        assertThrows(IllegalArgumentException.class, () -> new Scan(scan.id(), scan.organizationId(),
            scan.repositoryId(), "main", ScanStatus.COMPLETED, now, null, now, null));
    }
}
