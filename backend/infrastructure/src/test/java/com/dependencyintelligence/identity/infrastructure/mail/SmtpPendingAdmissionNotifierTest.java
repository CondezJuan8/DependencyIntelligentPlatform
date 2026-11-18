package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpPendingAdmissionNotifierTest {
    @Test void sendsPendingOwnerDetailsOnlyToActivePlatformAdministrators() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("admin@example.test"));
        var notifier = new SmtpPendingAdmissionNotifier(jdbc, sender, "no-reply@example.test", "http://localhost:8089");

        notifier.notify(new AccountResult(UUID.randomUUID(), "applicant@example.test", "Jane Smith", false,
            "OWNER", "PENDING_REVIEW", false));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals(List.of("admin@example.test"), List.of(message.getValue().getTo()));
        assertEquals("Nueva solicitud de acceso a Dependency Intelligence", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("applicant@example.test"));
        assertTrue(message.getValue().getText().contains("http://localhost:8089"));
    }

    @Test void doesNotSendForAccountsThatAreNotPendingOwners() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        var notifier = new SmtpPendingAdmissionNotifier(jdbc, sender, "no-reply@example.test", "http://localhost:8089");

        notifier.notify(new AccountResult(UUID.randomUUID(), "member@example.test", "Jane Smith", true,
            "MEMBER", "APPROVED", false));

        verifyNoInteractions(jdbc, sender);
    }
}
