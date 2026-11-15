package com.dependencyintelligence.shared.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.database.enforce-runtime-role", havingValue = "true", matchIfMissing = true)
public class RuntimeDatabaseRoleGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public RuntimeDatabaseRoleGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments arguments) {
        Boolean unsafe = jdbc.queryForObject("""
            select role.rolsuper or role.rolbypassrls or role.rolcreatedb or role.rolcreaterole or role.rolreplication
                or pg_has_role(current_user, 'dependency_migrator', 'member')
                or pg_has_role(current_user, 'dependency_audit_admin', 'member')
                or has_schema_privilege(current_user, current_schema(), 'CREATE')
                or exists(select 1 from pg_class relation join pg_namespace namespace on namespace.oid = relation.relnamespace
                    where namespace.nspname = current_schema() and relation.relowner = role.oid)
            from pg_roles role where role.rolname = current_user
            """, Boolean.class);
        if (!Boolean.FALSE.equals(unsafe)) {
            throw new IllegalStateException("Datasource must use a non-owner runtime role without DDL, migration, audit-admin or BYPASSRLS privileges");
        }
    }
}
