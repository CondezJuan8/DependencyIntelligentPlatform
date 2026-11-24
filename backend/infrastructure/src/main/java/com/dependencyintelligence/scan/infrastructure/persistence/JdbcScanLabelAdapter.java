package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.port.out.ScanLabelWriter;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcScanLabelAdapter implements ScanLabelWriter {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcScanLabelAdapter(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Override public boolean update(UUID organizationId, UUID repositoryId, UUID scanId, String displayName) {
        return tenants.execute(organizationId, () -> jdbc.update("""
            update scans set display_name=?
            where organization_id=? and repository_id=? and id=? and status='COMPLETED'
            """, displayName, organizationId, repositoryId, scanId) == 1);
    }
}
