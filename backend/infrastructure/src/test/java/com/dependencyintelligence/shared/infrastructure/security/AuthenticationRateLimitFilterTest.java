package com.dependencyintelligence.shared.infrastructure.security;

import com.dependencyintelligence.identity.infrastructure.security.AuthenticationRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationRateLimitFilterTest {
    @Test void limitsInvitationDeliveryAndAcceptanceInTheSharedBudget() throws Exception {
        var filter = new AuthenticationRateLimitFilter(Clock.systemUTC(), 1);
        for (var path : java.util.List.of("/api/organizations/example/invitations", "/api/organizations/example/invitations/id/accept")) {
            var request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            request.setRemoteAddr("127.0.0.3");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});
            assertEquals(path.endsWith("accept") ? 429 : 200, response.getStatus());
        }
    }
    @Test void limitsPasswordRequestsPerAddressAndReleasesTheNextWindow() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(120));
        var filter = new AuthenticationRateLimitFilter(clock, 1);
        assertEquals(200, attempt(filter, "127.0.0.1").getStatus());
        var rejected = attempt(filter, "127.0.0.1");
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertEquals(200, attempt(filter, "127.0.0.2").getStatus());
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(180));
        assertEquals(200, attempt(filter, "127.0.0.1").getStatus());
    }

    private MockHttpServletResponse attempt(AuthenticationRateLimitFilter filter, String address) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});
        return response;
    }
}
