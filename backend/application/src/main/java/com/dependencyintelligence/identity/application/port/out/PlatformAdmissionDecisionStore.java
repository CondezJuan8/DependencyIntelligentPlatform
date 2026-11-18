package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.application.dto.PendingAdmissionDecision;
import java.time.Instant;
import java.util.Optional;

public interface PlatformAdmissionDecisionStore {
    Optional<PendingAdmissionDecision> consume(String tokenFingerprint, Instant now);
}
