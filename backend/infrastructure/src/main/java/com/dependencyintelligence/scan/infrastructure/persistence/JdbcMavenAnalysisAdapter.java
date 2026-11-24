package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public final class JdbcMavenAnalysisAdapter implements MavenAnalysisStore {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcMavenAnalysisAdapter(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Override public MavenInventory save(String scannerName, UUID scanId, UUID organizationId, UUID repositoryId,
            String provider, String sourceSha256, String commitSha, Instant analyzedAt, ManifestAnalysis analysis) {
        return saveInternal(null, scannerName, scanId, organizationId, repositoryId, provider, sourceSha256,
            commitSha, analyzedAt, analysis);
    }

    @Override public MavenInventory saveForJob(UUID jobId, String scannerName, UUID scanId, UUID organizationId,
            UUID repositoryId, String provider, String sourceSha256, String commitSha, Instant analyzedAt,
            ManifestAnalysis analysis) {
        return saveInternal(jobId, scannerName, scanId, organizationId, repositoryId, provider, sourceSha256,
            commitSha, analyzedAt, analysis);
    }

    private MavenInventory saveInternal(UUID jobId, String scannerName, UUID scanId, UUID organizationId,
            UUID repositoryId, String provider, String sourceSha256, String commitSha, Instant analyzedAt,
            ManifestAnalysis analysis) {
        return tenants.execute(organizationId, () -> {
            UUID repositoryCommitId = "local".equals(provider) ? null
                : repositoryCommitId(organizationId, repositoryId, commitSha);
            int inserted = jdbc.update("""
                insert into scans(id,organization_id,repository_id,repository_commit_id,git_reference,status,requested_at,started_at,finished_at,
                  origin,scanner_name,scanner_version,trigger_type,source_sha256,manifest_count,unresolved_count,
                  coverage_status,coverage_message,background_job_id)
                values(?,?,?,?,?, 'COMPLETED',?,?,?, ?,?,?,'MANUAL',?,?,?,?,?,?) on conflict do nothing
                """, scanId, organizationId, repositoryId, repositoryCommitId, commitSha,
                Timestamp.from(analyzedAt), Timestamp.from(analyzedAt), Timestamp.from(analyzedAt),
                "local".equals(provider) ? "LOCAL_UPLOAD_SCAN" : "REPOSITORY_SCAN", scannerName,
                scannerName.startsWith("cdxgen-") ? "13.8.0" : "1", sourceSha256, analysis.manifestCount(), analysis.unresolvedCount(),
                analysis.coverageStatus(), analysis.coverageMessage(), jobId);
            if (inserted == 0) {
                if (jobId != null) return readInventoryForJob(jobId, scannerName, organizationId, repositoryId).orElseThrow();
                throw new IllegalStateException("No se pudo guardar el resultado del análisis.");
            }
            for (ManifestDependency dependency : analysis.dependencies()) {
                PackageCoordinates coordinates = dependency.coordinates();
                UUID ecosystemId = jdbc.queryForObject("select id from package_ecosystems where code=?", UUID.class, coordinates.ecosystem());
                UUID componentId = jdbc.queryForObject("""
                    insert into components(organization_id,package_ecosystem_id,namespace,name) values(?,?,?,?)
                    on conflict on constraint uq_component_identity do update set id=components.id returning id
                    """, UUID.class, organizationId, ecosystemId, coordinates.namespace(), coordinates.name());
                UUID versionId = jdbc.queryForObject("""
                    insert into component_versions(organization_id,component_id,version) values(?,?,?)
                    on conflict(organization_id,component_id,version) do update set id=component_versions.id returning id
                    """, UUID.class, organizationId, componentId, coordinates.version());
                jdbc.update("insert into component_identifiers(organization_id,component_version_id,identifier_type,value) values(?,?,'PURL',?) on conflict do nothing",
                    organizationId, versionId, coordinates.purl());
                String bomRef = bomRef(dependency);
                jdbc.update("""
                    insert into scan_components(organization_id,scan_id,bom_ref,component_version_id,dependency_type,dependency_scope_id,source_manifest_path)
                    values(?,?,?,?,?,(select id from dependency_scopes where code=?),?) on conflict do nothing
                    """, organizationId, scanId, bomRef, versionId, dependency.dependencyType(), dependency.scope(), dependency.manifestPath());
            }
            return readInventory(scannerName, organizationId, repositoryId, scanId).orElseThrow();
        });
    }

    @Override public Optional<MavenInventory> latest(String scannerName, UUID organizationId, UUID repositoryId) {
        return tenants.execute(organizationId, () -> readInventory(scannerName, organizationId, repositoryId, null));
    }

    @Override public Optional<MavenInventory> findById(String scannerName, UUID organizationId, UUID repositoryId,
            UUID scanId) {
        return tenants.execute(organizationId, () -> readInventory(scannerName, organizationId, repositoryId, scanId));
    }

    @Override public void saveFailure(String scannerName, UUID organizationId, UUID repositoryId, String provider,
            String sourceSha256, String commitSha, Instant failedAt, String reason) {
        saveFailureInternal(null, scannerName, organizationId, repositoryId, provider, sourceSha256, commitSha,
            failedAt, reason);
    }

    @Override public void saveFailureForJob(UUID jobId, String scannerName, UUID organizationId, UUID repositoryId,
            String provider, String sourceSha256, String commitSha, Instant failedAt, String reason) {
        saveFailureInternal(jobId, scannerName, organizationId, repositoryId, provider, sourceSha256, commitSha,
            failedAt, reason);
    }

    private void saveFailureInternal(UUID jobId, String scannerName, UUID organizationId, UUID repositoryId,
            String provider, String sourceSha256, String commitSha, Instant failedAt, String reason) {
        tenants.execute(organizationId, () -> {
            UUID scanId = UUID.randomUUID();
            boolean local = "local".equals(provider);
            UUID repositoryCommitId = local ? null : repositoryCommitId(organizationId, repositoryId, commitSha);
            String safeReason = reason == null || reason.isBlank() ? "El análisis falló sin detalle adicional." : reason;
            jdbc.update("""
                insert into scans(id,organization_id,repository_id,repository_commit_id,git_reference,status,requested_at,started_at,finished_at,
                  failure_reason,origin,scanner_name,scanner_version,trigger_type,source_sha256,manifest_count,unresolved_count,
                  coverage_status,coverage_message,background_job_id)
                values(?,?,?,?,?, 'FAILED',?,?,?, ?,?, ?,?,'MANUAL',?,0,0,'PARTIAL',?,?) on conflict do nothing
                """, scanId, organizationId, repositoryId, repositoryCommitId, local ? null : commitSha, Timestamp.from(failedAt),
                 Timestamp.from(failedAt), Timestamp.from(failedAt), safeReason, local ? "LOCAL_UPLOAD_SCAN" : "REPOSITORY_SCAN",
                 scannerName, scannerName.startsWith("cdxgen-") ? "13.8.0" : "1", sourceSha256, safeReason, jobId);
            return null;
        });
    }

    private UUID repositoryCommitId(UUID organizationId, UUID repositoryId, String commitSha) {
        if (commitSha == null) return null;
        jdbc.update("insert into repository_commits(organization_id,repository_id,commit_sha) values(?,?,?) on conflict do nothing",
            organizationId, repositoryId, commitSha);
        return jdbc.queryForObject("select id from repository_commits where organization_id=? and repository_id=? and commit_sha=?",
            UUID.class, organizationId, repositoryId, commitSha);
    }

    @Override public Optional<MavenInventory> findForJob(UUID jobId, String scannerName, UUID organizationId,
            UUID repositoryId) {
        return tenants.execute(organizationId, () -> readInventoryForJob(jobId, scannerName, organizationId, repositoryId));
    }

    private Optional<MavenInventory> readInventoryForJob(UUID jobId, String scannerName, UUID organizationId,
            UUID repositoryId) {
        var matches = jdbc.query("""
            select id from scans where organization_id=? and repository_id=? and background_job_id=?
              and scanner_name in (?,?) and status='COMPLETED' order by id limit 1
            """, (row, index) -> row.getObject(1, UUID.class), organizationId, repositoryId, jobId,
            scannerName, legacyScannerName(scannerName));
        return matches.isEmpty() ? Optional.empty()
            : readInventory(scannerName, organizationId, repositoryId, matches.getFirst());
    }

    private Optional<MavenInventory> readInventory(String scannerName, UUID organizationId, UUID repositoryId, UUID scanId) {
        var scans = jdbc.query("""
            select id,origin,source_sha256,git_reference,finished_at,manifest_count,unresolved_count,coverage_status,coverage_message
            from scans where organization_id=? and repository_id=? and scanner_name in (?,?) and status='COMPLETED'
              and (?::uuid is null or id=?)
            order by finished_at desc, id desc limit 1
            """, (row, index) -> new ScanRow(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                row.getString(4), row.getTimestamp(5).toInstant(), row.getInt(6), row.getInt(7), row.getString(8), row.getString(9)),
            organizationId, repositoryId, scannerName, legacyScannerName(scannerName), scanId, scanId);
        if (scans.isEmpty()) return Optional.empty();
        ScanRow scan = scans.getFirst();
        var dependencies = jdbc.query("""
            select sc.bom_ref,pe.code,c.namespace,c.name,cv.version,ds.code,sc.dependency_type,sc.source_manifest_path
            from scan_components sc
            join component_versions cv on cv.organization_id=sc.organization_id and cv.id=sc.component_version_id
            join components c on c.organization_id=cv.organization_id and c.id=cv.component_id
            join package_ecosystems pe on pe.id=c.package_ecosystem_id
            join dependency_scopes ds on ds.id=sc.dependency_scope_id
            where sc.organization_id=? and sc.scan_id=? order by c.name
            """, (row, index) -> {
                String ref = row.getString(1);
                int marker = ref.indexOf('#');
                String ecosystem = row.getString(2), namespace = row.getString(3), name = row.getString(4), version = row.getString(5);
                String purl = "npm".equals(ecosystem) ? npmPurl(name, version)
                    : "pkg:maven/" + namespace + "/" + name + "@" + version;
                String path = row.getString(8);
                if (path == null) path = marker < 0 ? "package-lock.json" : ref.substring(0, marker);
                return new ManifestDependency(path,
                    new PackageCoordinates(ecosystem, namespace, name, version, purl), row.getString(6), row.getString(7));
            }, organizationId, scan.id());
        return Optional.of(new MavenInventory(scan.id(), "LOCAL_UPLOAD_SCAN".equals(scan.origin()) ? "local" : "github",
            scan.sourceSha256(), scan.commitSha(), scan.analyzedAt(), scan.manifestCount(), scan.unresolvedCount(),
            scan.coverageStatus(), scan.coverageMessage(), dependencies));
    }

    private static String bomRef(ManifestDependency dependency) {
        PackageCoordinates coordinates = dependency.coordinates();
        String identity = String.join("\\n", dependency.manifestPath(), coordinates.purl(), dependency.scope());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return "manifest:" + java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static String npmPurl(String name, String version) {
        String path = java.util.Arrays.stream(name.split("/", -1))
            .map(part -> java.net.URLEncoder.encode(part, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
            .reduce((left, right) -> left + "/" + right).orElse("");
        return "pkg:npm/" + path + "@" + version;
    }

    private static String legacyScannerName(String scannerName) {
        return switch (scannerName) {
            case "cdxgen-maven" -> "maven-manifest";
            case "cdxgen-npm" -> "npm-lockfile";
            default -> scannerName;
        };
    }

    private record ScanRow(UUID id, String origin, String sourceSha256, String commitSha, Instant analyzedAt,
                           int manifestCount, int unresolvedCount, String coverageStatus, String coverageMessage) { }
}
