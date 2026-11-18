package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.dto.PlatformModels.Operation;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.PendingAdmissionNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SmtpPendingAdmissionNotifier implements PendingAdmissionNotifier {
    private static final Logger log = LoggerFactory.getLogger(SmtpPendingAdmissionNotifier.class);

    private final JdbcTemplate jdbc;
    private final JavaMailSender sender;
    private final String from;
    private final String baseUrl;
    private final AccountTokenCodec tokens;
    private final Clock clock;
    private final Duration lifetime;

    public SmtpPendingAdmissionNotifier(JdbcTemplate jdbc, JavaMailSender sender,
            AccountTokenCodec tokens, Clock clock, @Value("${app.mail.from}") String from,
            @Value("${app.mail.public-base-url}") String baseUrl,
            @Value("${app.security.account-tokens.admission-decision-lifetime}") Duration lifetime) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.tokens = tokens;
        this.clock = clock;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("Admission decision lifetime must be positive");
        this.lifetime = lifetime;
    }

    @Override public void notify(AccountResult account) {
        if (!"OWNER".equals(account.accountType()) || !"PENDING_REVIEW".equals(account.admissionStatus())) return;
        final List<Map<String, Object>> administrators;
        try {
            administrators = jdbc.queryForList("""
                select u.id, u.email
                from platform_administrators a
                join users u on u.id = a.user_id
                where u.is_active and u.email_verified_at is not null and u.admission_status = 'APPROVED'
                order by u.email
                """);
            if (administrators.isEmpty()) {
                log.warn("No se envio aviso de nueva solicitud: no hay administradores activos con correo confirmado");
                return;
            }
        } catch (DataAccessException exception) {
            log.warn("No se pudo consultar administradores para avisar una nueva solicitud");
            return;
        }

        for (Map<String, Object> administrator : administrators) {
            try {
                UUID administratorId = (UUID) administrator.get("id");
                String email = (String) administrator.get("email");
                String approveToken = issue(administratorId, account.id(), Operation.APPROVE);
                String rejectToken = issue(administratorId, account.id(), Operation.REJECT);

                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(email);
                message.setSubject("Nueva solicitud de acceso a Dependency Intelligence");
                message.setText("Hay una nueva solicitud de acceso pendiente de revisi\u00f3n."
                    + "\n\nNombre: " + account.displayName()
                    + "\nCorreo: " + account.email()
                    + "\n\nAPROBAR (al abrir el enlace, la solicitud se aprobar\u00e1 directamente):\n"
                    + baseUrl + "/#admission-decision=" + approveToken
                    + "\n\nRECHAZAR (al abrir el enlace, la solicitud se rechazar\u00e1 directamente):\n"
                    + baseUrl + "/#admission-decision=" + rejectToken
                    + "\n\nCada enlace es de un solo uso y caduca en " + expiryText() + ". Si no esperabas esta solicitud, ignora el mensaje.");
                sender.send(message);
            } catch (DataAccessException | MailException exception) {
                log.warn("No se pudo enviar el aviso de nueva solicitud a un administrador de plataforma ({})",
                    exception.getClass().getSimpleName());
            }
        }
    }

    private String expiryText() {
        long hours = lifetime.toHours();
        return hours > 0 ? hours + " horas" : Math.max(1, lifetime.toMinutes()) + " minutos";
    }

    private String issue(UUID administratorId, UUID targetUserId, Operation operation) {
        Instant created = clock.instant();
        String token = tokens.generate();
        jdbc.update("""
            INSERT INTO user_access_tokens(user_id, purpose, token_hash, target_user_id, admission_decision, created_at, expires_at)
            VALUES (?, 'PLATFORM_ADMISSION_DECISION', ?, ?, ?, ?, ?)
            """, administratorId, tokens.fingerprint(token), targetUserId, operation.name(), Timestamp.from(created), Timestamp.from(created.plus(lifetime)));
        return token;
    }
}
