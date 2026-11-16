package com.dependencyintelligence.identity.infrastructure.oauth;

import com.dependencyintelligence.identity.infrastructure.security.SessionIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import static com.dependencyintelligence.identity.infrastructure.oauth.ExplicitAuthorizationRequests.CURRENT_IDENTITY;

public class CallbackIdentityCapture extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/oauth2/callback/")) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof SessionIdentity identity) {
                request.setAttribute(CURRENT_IDENTITY, identity);
            }
        }
        chain.doFilter(request, response);
    }
}
