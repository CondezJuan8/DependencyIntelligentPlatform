package com.dependencyintelligence.identity.infrastructure.security;

import com.dependencyintelligence.identity.application.exception.AuthenticationFailedException;
import com.dependencyintelligence.identity.application.port.in.FindActiveAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final FindActiveAccount accounts;

    public ActiveAccountFilter(FindActiveAccount accounts) { this.accounts = accounts; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            try { accounts.execute(UUID.fromString(auth.getName())); }
            catch (AuthenticationFailedException | IllegalArgumentException exception) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
