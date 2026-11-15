package com.dependencyintelligence.identity.api;

import com.dependencyintelligence.identity.application.dto.ResetPasswordCommand;
import com.dependencyintelligence.identity.application.port.in.RecoverAccount;
import com.dependencyintelligence.identity.infrastructure.mail.PasswordResetRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {
    private final RecoverAccount recovery;
    private final PasswordResetRequests requests;

    public AccountRecoveryController(RecoverAccount recovery, PasswordResetRequests requests) {
        this.recovery = recovery;
        this.requests = requests;
    }

    @PostMapping("/email-verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestVerification(Principal principal) { recovery.requestVerification(UUID.fromString(principal.getName())); }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody TokenRequest body) { recovery.verifyEmail(body.token()); }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody EmailRequest body) { requests.request(body.email()); }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        recovery.resetPassword(new ResetPasswordCommand(body.token(), body.password()));
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record TokenRequest(@NotBlank @Size(max = 128) String token) {
        @Override public String toString() { return "TokenRequest[REDACTED]"; }
    }
    public record PasswordResetRequest(@NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "PasswordResetRequest[REDACTED]"; }
    }
}
