package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.dto.AccountResult;
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
import java.util.List;

@Component
public class SmtpPendingAdmissionNotifier implements PendingAdmissionNotifier {
    private static final Logger log = LoggerFactory.getLogger(SmtpPendingAdmissionNotifier.class);

    private final JdbcTemplate jdbc;
    private final JavaMailSender sender;
    private final String from;
    private final String baseUrl;

    public SmtpPendingAdmissionNotifier(JdbcTemplate jdbc, JavaMailSender sender,
            @Value("${app.mail.from}") String from, @Value("${app.mail.public-base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override public void notify(AccountResult account) {
        if (!"OWNER".equals(account.accountType()) || !"PENDING_REVIEW".equals(account.admissionStatus())) return;
        try {
            List<String> administrators = jdbc.queryForList("""
                select u.email
                from platform_administrators a
                join users u on u.id = a.user_id
                where u.is_active and u.email_verified_at is not null
                order by u.email
                """, String.class);
            if (administrators.isEmpty()) {
                log.warn("No se enviÃ³ aviso de nueva solicitud: no hay administradores activos con correo confirmado");
                return;
            }

            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(administrators.toArray(String[]::new));
            message.setSubject("Nueva solicitud de acceso a Dependency Intelligence");
            message.setText("Hay una nueva solicitud de acceso pendiente de revisiÃ³n."
                + "\n\nNombre: " + account.displayName()
                + "\nCorreo: " + account.email()
                + "\n\nInicia sesiÃ³n como administrador de plataforma para confirmar el correo y aprobar o rechazar la solicitud."
                + "\n\n" + baseUrl);
            sender.send(message);
        } catch (DataAccessException | MailException exception) {
            log.warn("No se pudo enviar el aviso de nueva solicitud a los administradores de plataforma");
        }
    }
}
