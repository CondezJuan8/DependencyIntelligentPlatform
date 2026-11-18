package com.dependencyintelligence.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded, per-process fixed window for authentication and invitation delivery/acceptance. */
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private record Window(long minute, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int maximum;
    private static final Set<String> PASSWORD_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/register-invited",
        "/api/auth/password-reset/request", "/api/auth/password-reset/confirm",
        "/api/auth/email-verification/request", "/api/auth/email-verification/confirm", "/api/platform/admission-decisions");

    public AuthenticationRateLimitFilter(Clock clock, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Authentication rate limit must be positive");
        this.clock = clock;
        this.maximum = maximum;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(PASSWORD_PATHS.contains(request.getServletPath())
            || request.getServletPath().startsWith("/api/auth/providers/")
            || request.getServletPath().matches("/api/organizations/[^/]+/invitations(?:/.*)?"));
    }

    private synchronized boolean allow(String address) {
        long minute = clock.instant().getEpochSecond() / 60;
        windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
        Window previous = windows.get(address);
        if ((previous != null && previous.count() >= maximum) || (previous == null && windows.size() >= 4096)) return false;
        windows.put(address, new Window(minute, previous == null ? 1 : previous.count() + 1));
        return true;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter(request, response);
    }
}
