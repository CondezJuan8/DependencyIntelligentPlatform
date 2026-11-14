package com.dependencyintelligence.organization.infrastructure.persistence;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOrganizationLookup implements OrganizationLookup {
    private final JdbcTemplate jdbc;

    public JdbcOrganizationLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean exists(OrganizationId organizationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organizations where id = ?)",
            Boolean.class, organizationId.value()));
    }
}
