package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpPendingAdmissionNotifierTest {
    @Test void sendsScopedApproveAndRejectLinksOnlyToPlatformAdministrator() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        AccountTokenCodec tokens = mock(AccountTokenCodec.class);
        UUID administratorId = UUID.randomUUID();
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", administratorId, "email", "admin@example.test")));
        when(tokens.generate()).thenReturn("A".repeat(43), "B".repeat(43));
        when(tokens.fingerprint("A".repeat(43))).thenReturn("a".repeat(64));
        when(tokens.fingerprint("B".repeat(43))).thenReturn("b".repeat(64));
        var notifier = new SmtpPendingAdmissionNotifier(jdbc, sender, tokens,
            Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC), "no-reply@example.test",
            "http://localhost:8089", Duration.ofHours(24));

        notifier.notify(new AccountResult(UUID.randomUUID(), "applicant@example.test", "Jane Smith", false,
            "OWNER", "PENDING_REVIEW", false));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("admin@example.test", message.getValue().getTo()[0]);
        assertTrue(message.getValue().getText().contains("/#admission-decision=" + "A".repeat(43)));
        assertTrue(message.getValue().getText().contains("/#admission-decision=" + "B".repeat(43)));
        assertTrue(message.getValue().getText().contains("APROBAR"));
        assertTrue(message.getValue().getText().contains("RECHAZAR"));
        verify(tokens, org.mockito.Mockito.times(2)).generate();
    }

    @Test void doesNotSendForAccountsThatAreNotPendingOwners() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        AccountTokenCodec tokens = mock(AccountTokenCodec.class);
        var notifier = new SmtpPendingAdmissionNotifier(jdbc, sender, tokens, Clock.systemUTC(),
            "no-reply@example.test", "http://localhost:8089", Duration.ofHours(24));

        notifier.notify(new AccountResult(UUID.randomUUID(), "member@example.test", "Jane Smith", true,
            "MEMBER", "APPROVED", false));

        verifyNoInteractions(jdbc, sender, tokens);
    }
}
