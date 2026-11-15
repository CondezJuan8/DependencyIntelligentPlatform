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

/** Bounded, per-process fixed window for expensive anonymous password operations. */
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private record Window(long minute, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int maximum;

    public AuthenticationRateLimitFilter(Clock clock, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Authentication rate limit must be positive");
        this.clock = clock;
        this.maximum = maximum;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(request.getServletPath().equals("/api/auth/login")
            || request.getServletPath().equals("/api/auth/register"));
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
