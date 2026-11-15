package com.dependencyintelligence.shared.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.dependencyintelligence.identity.application.port.in.ValidateAccountSession;
import java.time.Clock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigurationTest.ProbeController.class)
@Import({SecurityConfiguration.class, SecurityConfigurationTest.ProbeController.class, SecurityConfigurationTest.TestClock.class})
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ValidateAccountSession accounts;

    @Test
    void healthIsAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anonymousBusinessRequestsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/repositories")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestClock {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/actuator/health") String health() { return "UP"; }
        @GetMapping("/api/repositories") String repositories() { return "[]"; }
    }
}
