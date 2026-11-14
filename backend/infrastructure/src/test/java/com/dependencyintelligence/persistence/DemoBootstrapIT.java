package com.dependencyintelligence.persistence;

import com.dependencyintelligence.shared.infrastructure.demo.DemoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoBootstrapIT {
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:17.7-alpine");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
        }
    }

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
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/organizations/" + demo.organizationId() + "/repositories/" + demo.repositoryId()))
            .andExpect(status().isForbidden());
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
