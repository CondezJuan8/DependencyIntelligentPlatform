package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.PlatformAdmissionDecisionResult;

public interface DecidePlatformAdmission {
    PlatformAdmissionDecisionResult execute(String token);
}
