package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.domain.Scan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;

@Component
public class JdbcScanAdapter implements ScanStore {
    private final JdbcTemplate jdbc;

    public JdbcScanAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Scan add(Scan scan) {
        jdbc.update("""
            insert into scans (id, organization_id, repository_id, git_reference, status,
                               requested_at, started_at, finished_at, failure_reason, origin)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, scan.id(), scan.organizationId().value(), scan.repositoryId().value(),
            scan.gitReference(), scan.status().name(), Timestamp.from(scan.requestedAt()),
            scan.startedAt() == null ? null : Timestamp.from(scan.startedAt()),
            scan.finishedAt() == null ? null : Timestamp.from(scan.finishedAt()), scan.failureReason(), scan.origin().name());
        return scan;
    }
}
