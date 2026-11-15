package com.dependencyintelligence.identity.api;

import com.dependencyintelligence.identity.application.dto.*;
import com.dependencyintelligence.identity.application.port.in.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AccountController {
    private final RegisterAccount registration;
    private final AuthenticateAccount authentication;
    private final FindActiveAccount currentAccount;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public AccountController(RegisterAccount registration, AuthenticateAccount authentication, FindActiveAccount currentAccount,
                             SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.registration = registration;
        this.authentication = authentication;
        this.currentAccount = currentAccount;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResult register(@Valid @RequestBody RegistrationRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = registration.execute(new RegisterAccountCommand(body.email(), body.displayName(), body.password()));
        signIn(account, request, response);
        return account;
    }

    @PostMapping("/login")
    public AccountResult login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = authentication.execute(body.email(), body.password());
        signIn(account, request, response);
        return account;
    }

    @GetMapping("/me")
    public AccountResult me(Principal principal) { return currentAccount.execute(UUID.fromString(principal.getName())); }

    private void signIn(AccountResult account, HttpServletRequest request, HttpServletResponse response) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(account.id().toString(), null, List.of());
        sessions.onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    public record CsrfResponse(String headerName, String token) { }
    public record RegistrationRequest(@NotBlank @Email @Size(max = 320) String email,
                                      @NotBlank @Size(max = 200) String displayName,
                                      @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "RegistrationRequest[REDACTED]"; }
    }
    public record LoginRequest(@NotBlank @Size(max = 320) String email, @NotNull @Size(max = 128) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
}
