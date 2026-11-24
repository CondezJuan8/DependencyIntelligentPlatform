package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.dto.AnalysisJobStatus;
import com.dependencyintelligence.scan.application.dto.AnalysisJobTicket;
import com.dependencyintelligence.scan.application.port.out.AnalysisJobStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcAnalysisJobAdapter implements AnalysisJobStore {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcAnalysisJobAdapter(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Override public AnalysisJobTicket enqueue(UUID organizationId, UUID repositoryId, String idempotencyKey,
            Instant requestedAt) {
        return tenants.execute(organizationId, () -> {
            UUID requestedId = UUID.randomUUID();
            String scopedIdempotencyKey = repositoryId + ":" + idempotencyKey;
            UUID inserted = jdbc.query(connection -> {
                var statement = connection.prepareStatement("""
                    insert into background_jobs(id, organization_id, job_type, status, payload, deduplication_key, created_at)
                    values(?, ?, 'SCAN', 'PENDING', jsonb_build_object('repositoryId', ?), ?, ?)
                    on conflict do nothing returning id
                    """);
                statement.setObject(1, requestedId);
                statement.setObject(2, organizationId);
                statement.setString(3, repositoryId.toString());
                statement.setString(4, scopedIdempotencyKey);
                statement.setTimestamp(5, Timestamp.from(requestedAt));
                return statement;
            }, result -> result.next() ? result.getObject(1, UUID.class) : null);
            if (inserted != null) return new AnalysisJobTicket(inserted, "PENDING");
            return jdbc.queryForObject("""
                select id, status from background_jobs
                 where organization_id=? and job_type='SCAN'
                   and (deduplication_key=? or (payload->>'repositoryId'=? and status in ('PENDING','RUNNING','RETRYING')))
                 order by case when deduplication_key=? then 0 else 1 end, created_at desc
                 limit 1
                """, (row, index) -> new AnalysisJobTicket(row.getObject(1, UUID.class), row.getString(2)),
                organizationId, scopedIdempotencyKey, repositoryId.toString(), scopedIdempotencyKey);
        });
    }

    @Override public Optional<AnalysisJobStatus> find(UUID organizationId, UUID repositoryId, UUID jobId) {
        return tenants.execute(organizationId, () -> {
            var jobs = jdbc.query("""
                select job.status, job.created_at, min(attempt.started_at), max(attempt.finished_at), job.last_error_code
                  from background_jobs job
                  left join background_job_attempts attempt
                    on attempt.organization_id=job.organization_id and attempt.job_id=job.id
                 where job.organization_id=? and job.id=? and job.job_type='SCAN'
                   and job.payload->>'repositoryId'=?
                 group by job.organization_id, job.id
                """, (row, index) -> new JobRow(row.getString(1), row.getTimestamp(2).toInstant(),
                    row.getTimestamp(3) == null ? null : row.getTimestamp(3).toInstant(),
                    row.getTimestamp(4) == null ? null : row.getTimestamp(4).toInstant(), row.getString(5)),
                organizationId, jobId, repositoryId.toString());
            if (jobs.isEmpty()) return Optional.empty();
            JobRow job = jobs.getFirst();
            List<String> stages = jdbc.query("""
                select sanitized_metadata->>'stage' from background_job_events
                 where organization_id=? and job_id=? and event_type='STAGE_COMPLETED'
                 order by occurred_at, id
                """, (row, index) -> row.getString(1), organizationId, jobId);
            List<AnalysisJobStatus.ScanResult> scans = jdbc.query("""
                select scanner_name, id, status, coalesce(manifest_count,0),
                       (select count(*) from scan_components component
                         where component.organization_id=scan.organization_id and component.scan_id=scan.id),
                       coalesce(unresolved_count,0), coverage_status, coverage_message, failure_reason
                  from scans scan
                 where organization_id=? and repository_id=? and background_job_id=?
                 order by scanner_name
                """, (row, index) -> new AnalysisJobStatus.ScanResult(
                    "cdxgen-npm".equals(row.getString(1)) ? "NPM" : "MAVEN",
                    row.getObject(2, UUID.class), "COMPLETED".equals(row.getString(3)) && row.getInt(4) == 0
                        ? "NO_MANIFEST" : row.getString(3), row.getInt(4), row.getInt(5), row.getInt(6),
                    row.getString(7), row.getString(8), row.getString(9)), organizationId, repositoryId, jobId);
            boolean terminal = List.of("COMPLETED", "FAILED", "CANCELLED").contains(job.status());
            return Optional.of(new AnalysisJobStatus(jobId, job.status(), job.requestedAt(), job.startedAt(),
                terminal ? job.finishedAt() : null, job.errorCode(), stages, scans));
        });
    }

    @Override public Optional<AnalysisJobStatus> findActive(UUID organizationId, UUID repositoryId) {
        return tenants.execute(organizationId, () -> {
            List<UUID> jobs = jdbc.query("""
                select id from background_jobs
                 where organization_id=? and job_type='SCAN'
                   and payload->>'repositoryId'=? and status in ('PENDING','RUNNING','RETRYING')
                 order by created_at desc limit 1
                """, (row, index) -> row.getObject(1, UUID.class), organizationId, repositoryId.toString());
            return jobs.isEmpty() ? Optional.empty() : find(organizationId, repositoryId, jobs.getFirst());
        });
    }

    @Override public boolean cancel(UUID organizationId, UUID repositoryId, UUID jobId) {
        return tenants.execute(organizationId, () -> {
            int changed = jdbc.update("""
                update background_jobs set status='CANCELLED', last_error_code='CANCELLED',
                       locked_at=null, locked_by=null
                 where organization_id=? and id=? and job_type='SCAN'
                   and payload->>'repositoryId'=? and status in ('PENDING','RUNNING','RETRYING')
                """, organizationId, jobId, repositoryId.toString());
            if (changed == 0) return false;
            jdbc.update("""
                update background_job_attempts set status='CANCELLED', finished_at=clock_timestamp(),
                       error_code='CANCELLED', error_message='Cancelled by authorized request'
                 where organization_id=? and job_id=? and status='RUNNING'
                """, organizationId, jobId);
            return true;
        });
    }

    @Override public List<ClaimedJob> claim(String worker) {
        return jdbc.query("select organization_id, job_id, repository_id, attempt_number from app_private.claim_next_scan_analysis_job(?)",
            (row, index) -> new ClaimedJob(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class), row.getInt(4), worker), worker);
    }

    @Override public boolean pinSource(UUID organizationId, UUID jobId, String worker, String provider,
            String sha256, String commitSha) {
        return tenants.execute(organizationId, () -> jdbc.update("""
            update background_jobs set payload = payload || jsonb_build_object(
                'sourceProvider', ?, 'sourceSha256', ?, 'commitSha', ?::text)
             where organization_id=? and id=? and job_type='SCAN' and status='RUNNING' and locked_by=?
               and (payload->>'sourceSha256' is null or
                    (payload->>'sourceProvider'=? and payload->>'sourceSha256'=? and
                     coalesce(payload->>'commitSha','')=coalesce(?::text,'')))
            """, provider, sha256, commitSha, organizationId, jobId, worker,
            provider, sha256, commitSha) == 1);
    }

    @Override public boolean isCancelled(UUID organizationId, UUID jobId) {
        return tenants.execute(organizationId, () -> Boolean.TRUE.equals(jdbc.queryForObject("""
            select not exists(select 1 from background_jobs where organization_id=? and id=?
                               and job_type='SCAN' and status='RUNNING')
            """, Boolean.class, organizationId, jobId)));
    }

    @Override public void recordStage(UUID organizationId, UUID jobId, String stage) {
        tenants.execute(organizationId, () -> {
            jdbc.update("""
                insert into background_job_events(organization_id,job_id,event_type,sanitized_metadata)
                select ?,?,'STAGE_COMPLETED',jsonb_build_object('stage',?)
                 where exists(select 1 from background_jobs where organization_id=? and id=? and status='RUNNING')
                   and not exists(select 1 from background_job_events where organization_id=? and job_id=?
                                   and event_type='STAGE_COMPLETED' and sanitized_metadata->>'stage'=?)
                """, organizationId, jobId, stage, organizationId, jobId, organizationId, jobId, stage);
            return null;
        });
    }

    @Override public void complete(UUID organizationId, UUID jobId, String worker, int attemptNumber) {
        tenants.execute(organizationId, () -> {
            int changed = jdbc.update("""
                update background_jobs set status='COMPLETED', locked_at=null, locked_by=null
                 where organization_id=? and id=? and status='RUNNING' and locked_by=?
                """, organizationId, jobId, worker);
            if (changed == 1) jdbc.update("""
                update background_job_attempts set status='COMPLETED', finished_at=clock_timestamp()
                 where organization_id=? and job_id=? and attempt_number=? and status='RUNNING'
                """, organizationId, jobId, attemptNumber);
            return null;
        });
    }

    @Override public void retryOrFail(UUID organizationId, UUID jobId, String worker, int attemptNumber,
            String errorCode) {
        tenants.execute(organizationId, () -> {
            int changed = jdbc.update("""
                update background_jobs set status=case when ? < max_attempts then 'RETRYING' else 'FAILED' end,
                       available_at=clock_timestamp() + (? * INTERVAL '30 seconds'),
                       last_error_code=?, locked_at=null, locked_by=null
                 where organization_id=? and id=? and status='RUNNING' and locked_by=?
                """, attemptNumber, attemptNumber, errorCode, organizationId, jobId, worker);
            if (changed == 1) jdbc.update("""
                update background_job_attempts set status='FAILED', finished_at=clock_timestamp(),
                       error_code=?, error_message='The analysis worker could not complete this attempt'
                 where organization_id=? and job_id=? and attempt_number=? and status='RUNNING'
                """, errorCode, organizationId, jobId, attemptNumber);
            return null;
        });
    }

    private record JobRow(String status, Instant requestedAt, Instant startedAt, Instant finishedAt, String errorCode) { }
}
