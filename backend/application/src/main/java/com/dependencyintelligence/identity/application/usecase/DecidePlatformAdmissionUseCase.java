package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.PlatformAdmissionDecisionResult;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Action;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Operation;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Resource;
import com.dependencyintelligence.identity.application.exception.InvalidAccountTokenException;
import com.dependencyintelligence.identity.application.port.in.DecidePlatformAdmission;
import com.dependencyintelligence.identity.application.port.in.ManagePlatform;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.PlatformAdmissionDecisionStore;
import java.time.Clock;

public final class DecidePlatformAdmissionUseCase implements DecidePlatformAdmission {
    private final PlatformAdmissionDecisionStore decisions;
    private final AccountTokenCodec tokens;
    private final ManagePlatform platform;
    private final Clock clock;

    public DecidePlatformAdmissionUseCase(PlatformAdmissionDecisionStore decisions, AccountTokenCodec tokens,
            ManagePlatform platform, Clock clock) {
        this.decisions = decisions;
        this.tokens = tokens;
        this.platform = platform;
        this.clock = clock;
    }

    @Override public PlatformAdmissionDecisionResult execute(String token) {
        var decision = decisions.consume(tokens.fingerprint(token), clock.instant())
            .orElseThrow(InvalidAccountTokenException::new);
        Operation operation;
        try { operation = Operation.valueOf(decision.decision()); }
        catch (IllegalArgumentException exception) { throw new InvalidAccountTokenException(); }
        if (operation != Operation.APPROVE && operation != Operation.REJECT) throw new InvalidAccountTokenException();

        String reason = operation == Operation.APPROVE
            ? "Aprobado mediante enlace de correo de administrador"
            : "Rechazado mediante enlace de correo de administrador";
        platform.execute(decision.administratorId(), new Action(Resource.USER, decision.targetUserId(), operation, reason));
        return new PlatformAdmissionDecisionResult(operation.name());
    }
}
