package com.dependencyintelligence.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared disposable PostgreSQL server with one clean database per integration-test class. */
@SpringBootTest(properties = "app.database.enforce-runtime-role=false")
abstract class PostgresIntegrationSupport {
    private static PostgreSQLContainer<?> postgres;
    private static String serverUrl;
    protected static String user;
    protected static String password;
    protected static final String RUNTIME_PASSWORD = "integration-runtime-only";
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final Set<String> CREATED_DATABASES = ConcurrentHashMap.newKeySet();
    private static boolean containerManaged;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PostgresIntegrationSupport::removeExternalTestDatabases,
            "remove-disposable-integration-databases"));
    }

    protected static synchronized void database(DynamicPropertyRegistry registry, boolean runtime, String testClass) {
        if (serverUrl == null) {
            serverUrl = System.getenv("TEST_DATABASE_URL");
            if (serverUrl == null || serverUrl.isBlank()) {
                var postgresImage = DockerImageName.parse("dependency-intelligence-postgres:17.7-alpine-pgvector-0.8.7")
                    .asCompatibleSubstituteFor("postgres");
                postgres = new PostgreSQLContainer<>(postgresImage);
                postgres.start();
                serverUrl = postgres.getJdbcUrl(); user = postgres.getUsername(); password = postgres.getPassword();
                containerManaged = true;
            } else {
                user = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD");
            }
        }
        String databaseName = "di_it_" + RUN_ID + "_" + testClass.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String url = databaseUrl(serverUrl, databaseName);
        createDatabase(databaseName);
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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 3);
    }

    private static void createDatabase(String databaseName) {
        if (!CREATED_DATABASES.add(databaseName)) return;
        try (var connection = DriverManager.getConnection(serverUrl, user, password);
             var statement = connection.createStatement()) {
            statement.execute("create database \"" + databaseName + "\"");
        } catch (Exception exception) {
            CREATED_DATABASES.remove(databaseName);
            throw new IllegalStateException("Unable to create isolated integration database " + databaseName
                + "; the TEST_DATABASE_USER must have CREATEDB on its disposable PostgreSQL server", exception);
        }
    }

    private static String databaseUrl(String baseUrl, String databaseName) {
        int queryIndex = baseUrl.indexOf('?');
        String address = queryIndex < 0 ? baseUrl : baseUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : baseUrl.substring(queryIndex);
        int databaseSeparator = address.indexOf('/', "jdbc:postgresql://".length());
        if (databaseSeparator < 0) throw new IllegalArgumentException("TEST_DATABASE_URL must include a database name");
        return address.substring(0, databaseSeparator + 1) + databaseName + query;
    }

    private static void removeExternalTestDatabases() {
        if (containerManaged || serverUrl == null || CREATED_DATABASES.isEmpty()) return;
        try (var connection = DriverManager.getConnection(serverUrl, user, password);
             var statement = connection.createStatement()) {
            for (String databaseName : CREATED_DATABASES) {
                statement.execute("select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + databaseName
                    + "' and pid <> pg_backend_pid()");
                statement.execute("drop database if exists \"" + databaseName + "\"");
            }
        } catch (Exception exception) {
            System.err.println("Could not remove disposable integration databases: " + exception.getMessage());
        }
    }
}
