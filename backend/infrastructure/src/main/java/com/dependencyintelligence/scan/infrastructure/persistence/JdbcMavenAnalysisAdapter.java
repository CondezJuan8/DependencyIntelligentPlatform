package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.dto.ManifestDependencyEdge;
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
        return tenants.execute(organizationId, () -> {
            jdbc.update("""
                insert into scans(id,organization_id,repository_id,git_reference,status,requested_at,started_at,finished_at,
                  origin,scanner_name,scanner_version,trigger_type,source_sha256,manifest_count,unresolved_count,coverage_status,coverage_message)
                values(?,?,?,?, 'COMPLETED',?,?,?, ?,?,?,'MANUAL',?,?,?,?,?)
                """, scanId, organizationId, repositoryId, commitSha, Timestamp.from(analyzedAt), Timestamp.from(analyzedAt),
                Timestamp.from(analyzedAt), "local".equals(provider) ? "LOCAL_UPLOAD_SCAN" : "REPOSITORY_SCAN", scannerName,
                scannerName.startsWith("cdxgen-") ? "13.8.0" : "1", sourceSha256, analysis.manifestCount(), analysis.unresolvedCount(),
                analysis.coverageStatus(), analysis.coverageMessage());
            if (commitSha != null) {
                jdbc.update("insert into repository_commits(organization_id,repository_id,commit_sha) values(?,?,?) on conflict do nothing",
                    organizationId, repositoryId, commitSha);
                jdbc.update("update scans set repository_commit_id=(select id from repository_commits where organization_id=? and repository_id=? and commit_sha=?) where organization_id=? and id=?",
                    organizationId, repositoryId, commitSha, organizationId, scanId);
            }

            Map<String, UUID> occurrences = new HashMap<>();
            Map<String, String> refsByPath = new HashMap<>();
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
                UUID scanComponentId = jdbc.queryForObject("select id from scan_components where organization_id=? and scan_id=? and bom_ref=?",
                    UUID.class, organizationId, scanId, bomRef);
                occurrences.put(bomRef, scanComponentId);
                refsByPath.put(dependency.manifestPath(), bomRef);
            }

            UUID relationTypeId = null;
            if (!analysis.edges().isEmpty()) {
                relationTypeId = jdbc.queryForObject("select id from dependency_relationship_types where code='depends_on'", UUID.class);
            }
            for (ManifestDependencyEdge edge : analysis.edges()) {
                String sourceRef = refsByPath.get(edge.parentManifestPath());
                String targetRef = refsByPath.get(edge.childManifestPath());
                UUID sourceId = sourceRef == null ? null : occurrences.get(sourceRef);
                UUID targetId = targetRef == null ? null : occurrences.get(targetRef);
                if (sourceId == null || targetId == null || sourceId.equals(targetId)) continue;
                jdbc.update("""
                    insert into dependency_edges(organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id)
                    values(?,?,?,?,?) on conflict(organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) do nothing
                    """, organizationId, scanId, sourceId, targetId, relationTypeId);
            }
            return readInventory(scannerName, organizationId, repositoryId, scanId).orElseThrow();
        });
    }

    @Override public Optional<MavenInventory> latest(String scannerName, UUID organizationId, UUID repositoryId) {
        return tenants.execute(organizationId, () -> readInventory(scannerName, organizationId, repositoryId, null));
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
