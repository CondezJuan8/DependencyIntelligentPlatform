package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.dto.ScanHistoryItem;
import com.dependencyintelligence.scan.application.port.out.ScanHistoryReader;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcScanHistoryAdapter implements ScanHistoryReader {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcScanHistoryAdapter(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Override public List<ScanHistoryItem> find(UUID organizationId, UUID repositoryId, int limit) {
        return tenants.execute(organizationId, () -> jdbc.query("""
            select s.id, s.repository_id, r.full_name, s.git_reference, c.commit_sha, s.status,
                   s.requested_at, s.started_at, s.finished_at, s.failure_reason, s.origin,
                   s.scanner_name, s.scanner_version, s.source_sha256, s.manifest_count,
                   (select count(*) from scan_components component where component.organization_id=s.organization_id and component.scan_id=s.id),
                   s.unresolved_count, s.coverage_status, s.coverage_message, s.display_name, s.sequence_number
            from (
                select scans.*, row_number() over (partition by organization_id, repository_id order by requested_at, id) sequence_number
                from scans where organization_id=? and repository_id=?
            ) s
            join repositories r on r.organization_id=s.organization_id and r.id=s.repository_id
            left join repository_commits c on c.organization_id=s.organization_id and c.repository_id=s.repository_id and c.id=s.repository_commit_id
            where s.organization_id=? and s.repository_id=? and r.is_enabled
            order by s.requested_at desc, s.id desc
            limit ?
            """, (row, index) -> new ScanHistoryItem(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getString(3), row.getString(4), row.getString(5), row.getString(6),
                row.getTimestamp(7).toInstant(), row.getTimestamp(8) == null ? null : row.getTimestamp(8).toInstant(),
                row.getTimestamp(9) == null ? null : row.getTimestamp(9).toInstant(), row.getString(10),
                row.getString(11), row.getString(12), row.getString(13), row.getString(14),
                row.getObject(15, Integer.class) == null ? 0 : row.getInt(15), row.getInt(16),
                row.getObject(17, Integer.class) == null ? 0 : row.getInt(17), row.getString(18), row.getString(19),
                row.getString(20), row.getLong(21)),
            organizationId, repositoryId, organizationId, repositoryId, Math.max(1, Math.min(limit, 100))));
    }
}
