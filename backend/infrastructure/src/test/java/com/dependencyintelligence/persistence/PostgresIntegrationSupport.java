package com.dependencyintelligence.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;

/** Shared disposable database; fixture setup uses the migration login, runtime tests opt into the real app login. */
@SpringBootTest(properties = "app.database.enforce-runtime-role=false")
abstract class PostgresIntegrationSupport {
    private static PostgreSQLContainer<?> postgres;
    protected static String url;
    protected static String user;
    protected static String password;
    protected static final String RUNTIME_PASSWORD = "integration-runtime-only";

    protected static synchronized void database(DynamicPropertyRegistry registry, boolean runtime) {
        if (url == null) {
            url = System.getenv("TEST_DATABASE_URL");
            if (url == null || url.isBlank()) {
                postgres = new PostgreSQLContainer<>("postgres:17.7-alpine");
                postgres.start();
                url = postgres.getJdbcUrl(); user = postgres.getUsername(); password = postgres.getPassword();
            } else {
                user = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD");
            }
        }
        if (runtime) {
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("""
                    do $$ begin
                      if not exists(select 1 from pg_roles where rolname = 'dependency_app') then
                        create role dependency_app login nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
                      end if;
                      if exists(select 1 from pg_roles where rolname = 'dependency_runtime') then
                        grant dependency_runtime to dependency_app;
                      end if;
                    end $$
                    """);
                statement.execute("alter role dependency_app password '" + RUNTIME_PASSWORD + "'");
            } catch (Exception exception) { throw new IllegalStateException("Unable to provision disposable runtime fixture", exception); }
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> runtime ? "dependency_app" : user);
        registry.add("spring.datasource.password", () -> runtime ? RUNTIME_PASSWORD : password);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> user);
        registry.add("spring.flyway.password", () -> password);
    }
}
