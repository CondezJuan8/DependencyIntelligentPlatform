package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.PendingAdmissionDecision;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Action;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Operation;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Overview;
import com.dependencyintelligence.identity.application.port.in.ManagePlatform;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.PlatformAdmissionDecisionStore;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class DecidePlatformAdmissionUseCaseTest {
    @Test void appliesTheSingleUseApproveDecisionAsTheIssuingAdministrator() {
        assertDecision("APPROVE", Operation.APPROVE);
    }

    @Test void appliesTheSingleUseRejectDecisionAsTheIssuingAdministrator() {
        assertDecision("REJECT", Operation.REJECT);
    }

    private void assertDecision(String storedDecision, Operation expected) {
        UUID administratorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Action[] executed = new Action[1];
        UUID[] actor = new UUID[1];
        PlatformAdmissionDecisionStore store = (fingerprint, now) -> {
            assertEquals("stored-fingerprint", fingerprint);
            assertEquals(Instant.parse("2026-11-18T12:00:00Z"), now);
            return Optional.of(new PendingAdmissionDecision(administratorId, targetId, storedDecision));
        };
        AccountTokenCodec tokens = new AccountTokenCodec() {
            @Override public String generate() { return "unused"; }
            @Override public String fingerprint(String token) { assertEquals("one-time-token", token); return "stored-fingerprint"; }
        };
        ManagePlatform platform = new ManagePlatform() {
            @Override public Overview overview(UUID user) { return null; }
            @Override public void execute(UUID user, Action action) { actor[0] = user; executed[0] = action; }
            @Override public void updateName(UUID user, String name) { fail("Name update is unrelated"); }
        };
        var useCase = new DecidePlatformAdmissionUseCase(store, tokens, platform,
            Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));

        var result = useCase.execute("one-time-token");

        assertEquals(expected.name(), result.decision());
        assertSame(administratorId, actor[0]);
        assertEquals(targetId, executed[0].id());
        assertEquals(expected, executed[0].operation());
    }
}
