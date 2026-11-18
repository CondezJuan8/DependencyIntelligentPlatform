package com.dependencyintelligence.identity.infrastructure.persistence;

import com.dependencyintelligence.identity.application.dto.PendingAdmissionDecision;
import com.dependencyintelligence.identity.application.port.out.PlatformAdmissionDecisionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcPlatformAdmissionDecisionAdapter implements PlatformAdmissionDecisionStore {
    private final JdbcTemplate jdbc;

    public JdbcPlatformAdmissionDecisionAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<PendingAdmissionDecision> consume(String fingerprint, Instant now) {
        var decision = jdbc.query("""
            WITH available AS (
                SELECT id FROM user_access_tokens
                WHERE token_hash = ? AND purpose = 'PLATFORM_ADMISSION_DECISION'
                  AND consumed_at IS NULL AND expires_at > ?
                FOR UPDATE
            )
            UPDATE user_access_tokens token
            SET consumed_at = ?
            FROM available
            WHERE token.id = available.id
            RETURNING token.user_id, token.target_user_id, token.admission_decision
            """, (row, index) -> new PendingAdmissionDecision(
                row.getObject("user_id", java.util.UUID.class),
                row.getObject("target_user_id", java.util.UUID.class),
                row.getString("admission_decision")), fingerprint, Timestamp.from(now), Timestamp.from(now))
            .stream().findFirst();

        decision.ifPresent(value -> jdbc.update("""
            UPDATE user_access_tokens
            SET consumed_at = ?
            WHERE purpose = 'PLATFORM_ADMISSION_DECISION'
              AND target_user_id = ? AND consumed_at IS NULL
            """, Timestamp.from(now), value.targetUserId()));
        return decision;
    }
}
