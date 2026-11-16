package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.application.exception.ExternalIdentityException;
import com.dependencyintelligence.identity.application.port.in.ValidateAccountSession;
import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import static com.dependencyintelligence.identity.application.exception.ExternalIdentityException.Reason.SESSION_EXPIRED;

@Component
public class RecentIdentitySession {
    private final Clock clock;
    private final Duration window;
    private final ValidateAccountSession sessions;

    public RecentIdentitySession(Clock clock, ValidateAccountSession sessions,
            @Value("${app.identity.reauthentication-window}") Duration window) {
        if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("Reauthentication window must be positive");
        this.clock = clock;
        this.sessions = sessions;
        this.window = window;
    }

    public boolean recent(SessionIdentity identity) {
        return identity != null && identity.authenticatedAt() != null
            && !identity.authenticatedAt().isAfter(clock.instant())
            && !identity.authenticatedAt().isBefore(clock.instant().minus(window));
    }

    public void requireCurrent(SessionIdentity identity) {
        if (!recent(identity) || !sessions.isCurrent(identity.userId(), identity.credentialVersion())) {
            throw new ExternalIdentityException(SESSION_EXPIRED);
        }
    }
}
