package com.dependencyintelligence.organization.infrastructure.mail;

import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.dependencyintelligence.organization.application.port.out.InvitationDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.UUID;

@Component
public class SmtpInvitationDelivery implements InvitationDelivery {
    private final JavaMailSender sender;
    private final String from;
    private final String origin;
    public SmtpInvitationDelivery(JavaMailSender sender, @Value("${app.mail.from}") String from,
            @Value("${app.mail.public-base-url}") String baseUrl) {
        var uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("El origen público del correo debe ser HTTP(S), sin ruta ni credenciales");
        }
        this.sender = sender; this.from = from; this.origin = baseUrl.replaceAll("/+$", "");
    }
    @Override public void send(String email, String organization, UUID organizationId, UUID invitationId, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Invitación a una organización en Dependency Intelligence");
        message.setText("Has recibido una invitación para unirte a " + organization + ".\n\n"
            + origin + "/#organization=" + organizationId + "&invitation=" + invitationId + "&invite-token=" + token
            + "\n\nInicia sesión con el correo invitado y verifícalo antes de aceptar. El enlace caduca y solo se puede usar una vez."
            + "\nSi usas acceso externo o necesitas verificar el correo en otra pestaña, vuelve a abrir este enlace después."
            + "\nSi no esperabas esta invitación, puedes ignorarla.");
        try { sender.send(message); }
        catch (MailException exception) { throw new AccountMailUnavailableException(); }
    }
}
