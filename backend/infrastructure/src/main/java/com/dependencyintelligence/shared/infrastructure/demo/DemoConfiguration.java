package com.dependencyintelligence.shared.infrastructure.demo;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit local demo bootstrap; never runs in the default profile or overwrites existing rows. */
@Configuration
@Profile("demo")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoConfiguration {
    @Bean
    ApplicationRunner demoBootstrap(JdbcTemplate jdbc, DemoProperties demo, PlatformTransactionManager manager) {
        return arguments -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("""
                insert into organizations(id, name, slug) values (?, 'Dependency Intelligence Demo', 'local-demo')
                on conflict (id) do nothing
                """, demo.organizationId());
            Boolean organizationMatches = jdbc.queryForObject("""
                select exists(select 1 from organizations where id = ?
                    and name = 'Dependency Intelligence Demo' and slug = 'local-demo')
                """, Boolean.class, demo.organizationId());
            if (!Boolean.TRUE.equals(organizationMatches)) {
                throw new IllegalStateException("Demo organization id is already used by different data");
            }
            jdbc.update("""
                insert into repositories(id, organization_id, provider, external_id, full_name, default_branch)
                values (?, ?, 'demo', 'inventory-demo', 'example/inventory-demo', 'main')
                on conflict (id) do nothing
                """, demo.repositoryId(), demo.organizationId());
            Boolean repositoryMatches = jdbc.queryForObject("""
                select exists(select 1 from repositories where id = ? and organization_id = ?
                    and provider = 'demo' and external_id = 'inventory-demo'
                    and full_name = 'example/inventory-demo' and default_branch = 'main')
                """, Boolean.class, demo.repositoryId(), demo.organizationId());
            if (!Boolean.TRUE.equals(repositoryMatches)) {
                throw new IllegalStateException("Demo repository id is already used by different data");
            }
        });
    }
}
