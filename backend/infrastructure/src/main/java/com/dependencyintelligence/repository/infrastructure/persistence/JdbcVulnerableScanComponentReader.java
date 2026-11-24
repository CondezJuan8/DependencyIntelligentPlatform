package com.dependencyintelligence.repository.infrastructure.persistence;

import com.dependencyintelligence.repository.application.dto.VulnerableScanComponent;
import com.dependencyintelligence.repository.application.port.out.VulnerableScanComponentReader;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads the package, manifest and OSV record from one exact tenant-scoped scan occurrence. */
@Component
public final class JdbcVulnerableScanComponentReader implements VulnerableScanComponentReader {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcVulnerableScanComponentReader(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc; this.tenants = tenants;
    }

    @Override public Optional<VulnerableScanComponent> find(UUID organizationId, UUID repositoryId, UUID scanId,
            UUID scanComponentId, String advisoryId) {
        return tenants.execute(organizationId, () -> jdbc.query("""
            select s.id, sc.id, s.source_sha256, s.git_reference, s.origin, assessment.assessment_status,
                   ecosystem.code, component.namespace, component.name, version.version, sc.source_manifest_path
            from scans s
            join scan_components sc on sc.organization_id=s.organization_id and sc.scan_id=s.id
            join component_versions version on version.organization_id=sc.organization_id and version.id=sc.component_version_id
            join components component on component.organization_id=version.organization_id and component.id=version.component_id
            join package_ecosystems ecosystem on ecosystem.id=component.package_ecosystem_id
            join lateral (
                select evidence.assessment_status
                from scan_vulnerability_assessments evidence
                cross join lateral jsonb_array_elements(evidence.advisories) advisory(item)
                where evidence.organization_id=sc.organization_id and evidence.scan_id=sc.scan_id
                  and evidence.scan_component_id=sc.id
                  and upper(advisory.item->>'id')=upper(?)
                order by evidence.checked_at desc, evidence.id desc limit 1
            ) assessment on true
            where s.organization_id=? and s.repository_id=? and s.id=? and sc.id=?
              and s.status='COMPLETED' and s.source_sha256 is not null
            limit 1
            """, (row, index) -> new VulnerableScanComponent(row.getObject(1, UUID.class),
                row.getObject(2, UUID.class), row.getString(3), row.getString(4),
                "LOCAL_UPLOAD_SCAN".equals(row.getString(5)) ? "local" : "github", row.getString(6),
                row.getString(7), row.getString(8), row.getString(9), row.getString(10), row.getString(11)),
            advisoryId, organizationId, repositoryId, scanId, scanComponentId).stream().findFirst());
    }
}
