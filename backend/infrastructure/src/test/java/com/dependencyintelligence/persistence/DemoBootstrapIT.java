package com.dependencyintelligence.persistence;

import com.dependencyintelligence.shared.infrastructure.demo.DemoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoBootstrapIT extends PostgresIntegrationSupport {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) { database(registry, false, DemoBootstrapIT.class.getSimpleName()); }

    @Autowired JdbcTemplate jdbc;
    @Autowired DemoProperties demo;
    @Autowired ApplicationRunner demoBootstrap;
    @Autowired MockMvc mvc;

    @Test void seedsOnceAndPreservesSecurity() throws Exception {
        ApplicationArguments arguments = new DefaultApplicationArguments();
        demoBootstrap.run(arguments);
        demoBootstrap.run(arguments);
        assertEquals(1, jdbc.queryForObject("select count(*) from organizations where id = ?", Integer.class, demo.organizationId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from repositories where id = ? and organization_id = ?", Integer.class, demo.repositoryId(), demo.organizationId()));
        var health = mvc.perform(get("/actuator/health")).andReturn();
        assertTrue(health.getResponse().getStatus() == 200 || health.getResponse().getStatus() == 503,
            "health remains public and reports availability even when a dependency is down");
        assertTrue(health.getResponse().getContentAsString().contains("status"));
        var business = mvc.perform(get("/api/organizations/" + demo.organizationId() + "/repositories/" + demo.repositoryId()))
            .andReturn();
        assertTrue(business.getResponse().getStatus() == 401 || business.getResponse().getStatus() == 403,
            "demo seeding never grants business access to an unauthenticated caller");
    }

    @Test void refusesToOverwriteExistingData() {
        jdbc.update("update repositories set full_name = 'different/repository' where id = ?", demo.repositoryId());
        try {
            assertThrows(IllegalStateException.class, () -> demoBootstrap.run(new DefaultApplicationArguments()));
            assertEquals("different/repository", jdbc.queryForObject("select full_name from repositories where id = ?", String.class, demo.repositoryId()));
        } finally {
            jdbc.update("update repositories set full_name = 'example/inventory-demo' where id = ?", demo.repositoryId());
        }
    }
}
