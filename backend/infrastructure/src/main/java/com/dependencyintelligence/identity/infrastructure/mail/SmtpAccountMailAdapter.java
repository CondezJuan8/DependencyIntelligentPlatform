package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.dependencyintelligence.identity.application.port.out.AccountMail;
import com.dependencyintelligence.identity.domain.AccountTokenPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class SmtpAccountMailAdapter implements AccountMail {
    private final JavaMailSender sender;
    private final String from;
    private final String baseUrl;

    public SmtpAccountMailAdapter(JavaMailSender sender, @Value("${app.mail.from}") String from,
            @Value("${app.mail.public-base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Public base URL must be an absolute HTTP(S) URL without credentials/query/fragment");
        }
        this.sender = sender;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override public void sendLink(String email, AccountTokenPurpose purpose, String token) {
        boolean verification = purpose == AccountTokenPurpose.VERIFY_EMAIL;
        String action = verification ? "verify-email" : "reset-password";
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(verification ? "Verifica tu correo en Dependency Intelligence" : "Recupera tu contraseña de Dependency Intelligence");
        message.setText((verification ? "Confirma tu correo mediante este enlace" : "Elige una contraseña nueva mediante este enlace")
            + "\n\n" + baseUrl + "/#" + action + "=" + token
            + "\n\nEl enlace caduca y se puede usar una sola vez. Si no solicitaste este correo, puedes ignorarlo.");
        try { sender.send(message); }
        catch (MailException exception) { throw new AccountMailUnavailableException(); }
    }
}
