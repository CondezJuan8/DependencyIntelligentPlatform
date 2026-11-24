package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.dto.ScanComparison;
import com.dependencyintelligence.scan.application.dto.ScanComparison.DependencyDelta;
import com.dependencyintelligence.scan.application.dto.ScanComparison.Recommendation;
import com.dependencyintelligence.scan.application.dto.ScanComparison.ScanPoint;
import com.dependencyintelligence.scan.application.dto.ScanComparison.SecurityDelta;
import com.dependencyintelligence.scan.application.port.out.ScanComparisonReader;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only, tenant-scoped comparison over immutable completed snapshots. */
@Component
public final class JdbcScanComparisonAdapter implements ScanComparisonReader {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final ObjectMapper json;
    public JdbcScanComparisonAdapter(JdbcTemplate jdbc, TenantTransactions tenants, ObjectMapper json) {
        this.jdbc = jdbc; this.tenants = tenants; this.json = json;
    }

    @Override public ScanComparison compare(UUID organization, UUID repository, UUID baseId, UUID targetId) {
        return tenants.execute(organization, () -> {
            var points = jdbc.query("""
                select s.id, s.display_name, s.sequence_number, c.commit_sha, s.finished_at, s.coverage_status, s.coverage_message
                from (
                    select scans.*, row_number() over (partition by organization_id, repository_id order by requested_at, id) sequence_number
                    from scans where organization_id=? and repository_id=?
                ) s
                left join repository_commits c on c.organization_id=s.organization_id and c.repository_id=s.repository_id and c.id=s.repository_commit_id
                join repositories r on r.organization_id=s.organization_id and r.id=s.repository_id and r.is_enabled
                where s.organization_id=? and s.repository_id=? and s.id in (?,?) and s.status='COMPLETED'
                """, (row, index) -> point(row), organization, repository, organization, repository, baseId, targetId);
            Map<UUID, ScanPoint> byId = new HashMap<>(); points.forEach(point -> byId.put(point.id(), point));
            if (!byId.containsKey(baseId) || !byId.containsKey(targetId))
                throw new IllegalArgumentException("Uno de los anÃ¡lisis no existe, no estÃ¡ completado o pertenece a otro repositorio.");

            var dependencies = new TreeMap<PackageKey, MutablePackage>(Comparator.comparing(PackageKey::ecosystem)
                .thenComparing(PackageKey::namespace).thenComparing(PackageKey::name, String.CASE_INSENSITIVE_ORDER));
            jdbc.query("""
                select o.scan_id, e.code ecosystem, coalesce(c.namespace,'') namespace, c.name, v.version,
                       o.dependency_type, a.assessment_status, a.advisories::text advisories
                from scan_components o
                join component_versions v on v.organization_id=o.organization_id and v.id=o.component_version_id
                join components c on c.organization_id=v.organization_id and c.id=v.component_id
                join package_ecosystems e on e.id=c.package_ecosystem_id
                left join lateral (
                    select assessment_status, advisories from scan_vulnerability_assessments
                    where organization_id=o.organization_id and scan_id=o.scan_id and scan_component_id=o.id and source='OSV'
                    order by checked_at desc, id desc limit 1
                ) a on true
                where o.organization_id=? and o.scan_id in (?,?)
                order by e.code, c.namespace, c.name, v.version
                """, row -> {
                    UUID scanId = row.getObject("scan_id", UUID.class);
                    PackageKey key = new PackageKey(row.getString("ecosystem"), row.getString("namespace"), row.getString("name"));
                    dependencies.computeIfAbsent(key, ignored -> new MutablePackage()).add(scanId.equals(baseId), row);
                }, organization, baseId, targetId);

            List<DependencyDelta> deltas = new ArrayList<>();
            List<SecurityDelta> security = new ArrayList<>();
            List<Recommendation> recommendations = new ArrayList<>();
            int added = 0, removed = 0, updated = 0, unchanged = 0;
            for (var entry : dependencies.entrySet()) {
                PackageKey key = entry.getKey(); MutablePackage value = entry.getValue();
                String change = value.base.versions.isEmpty() ? "ADDED" : value.target.versions.isEmpty() ? "REMOVED"
                    : value.base.versions.equals(value.target.versions) ? "UNCHANGED" : "UPDATED";
                switch (change) { case "ADDED" -> added++; case "REMOVED" -> removed++; case "UPDATED" -> updated++; default -> unchanged++; }
                Set<String> newAdvisories = new HashSet<>(value.target.advisories.keySet());
                newAdvisories.removeAll(value.base.advisories.keySet());
                Set<String> resolvedAdvisories = new HashSet<>(value.base.advisories.keySet());
                resolvedAdvisories.removeAll(value.target.advisories.keySet());
                newAdvisories.stream().sorted().forEach(id -> security.add(new SecurityDelta(id, "NEW", key.display(), value.target.advisories.get(id))));
                resolvedAdvisories.stream().sorted().forEach(id -> security.add(new SecurityDelta(id, "NO_LONGER_REPORTED", key.display(), value.base.advisories.get(id))));
                deltas.add(new DependencyDelta(key.ecosystem, key.namespace, key.name, change,
                    value.base.sortedVersions(), value.target.sortedVersions(), value.base.occurrences, value.target.occurrences,
                    value.base.sortedAdvisories(), value.target.sortedAdvisories(), value.base.assessment(), value.target.assessment()));
                if (change.equals("UNCHANGED")) continue;
                String packageName = key.display();
                if (!newAdvisories.isEmpty()) recommendations.add(new Recommendation(packageName, "HIGH",
                    "El snapshot objetivo contiene avisos OSV que no aparecÃ­an en el base. Revisa el advisory y su versiÃ³n afectada antes de aceptar el cambio.", newAdvisories.stream().sorted().toList()));
                else if (!value.target.versions.isEmpty() && value.target.hasUnverifiedAssessment()) recommendations.add(new Recommendation(packageName, "REVIEW",
                    "La consulta OSV de la versiÃ³n objetivo estÃ¡ incompleta, fallÃ³ o no existe. No concluyas que el cambio elimina el riesgo hasta repetir la evaluaciÃ³n.", List.of()));
                else if (!resolvedAdvisories.isEmpty()) recommendations.add(new Recommendation(packageName, "IMPROVED",
                    "El snapshot objetivo ya no reporta estos avisos OSV para el paquete. Confirma que la evaluaciÃ³n del objetivo estÃ© completa; esto no demuestra ausencia total de vulnerabilidades.", resolvedAdvisories.stream().sorted().toList()));
            }
            recommendations.sort(Comparator.comparing(Recommendation::priority).thenComparing(Recommendation::packageName));
            return new ScanComparison(byId.get(baseId), byId.get(targetId), added, removed, updated, unchanged, deltas, security, recommendations);
        });
    }

    private ScanPoint point(ResultSet row) throws SQLException {
        return new ScanPoint(row.getObject("id", UUID.class), row.getString("display_name"), row.getLong("sequence_number"), row.getString("commit_sha"),
            row.getTimestamp("finished_at") == null ? null : row.getTimestamp("finished_at").toInstant(),
            row.getString("coverage_status"), row.getString("coverage_message"));
    }

    private final class MutablePackage {
        final Side base = new Side(); final Side target = new Side();
        void add(boolean isBase, ResultSet row) throws SQLException {
            Side side = isBase ? base : target;
            side.versions.add(row.getString("version")); side.occurrences++;
            String status = row.getString("assessment_status");
            if (status != null) side.statuses.add(status);
            String advisories = row.getString("advisories");
            if (advisories != null) try {
                JsonNode entries = json.readTree(advisories);
                if (entries.isArray()) for (JsonNode item : entries) {
                    String id = item.path("id").asText("").strip();
                    if (!id.isEmpty()) side.advisories.put(id, reference(item));
                }
            } catch (Exception ignored) { /* malformed provider evidence is not used as a recommendation */ }
        }
    }
    private static final class Side {
        final Set<String> versions = new LinkedHashSet<>();
        final Map<String,String> advisories = new TreeMap<>();
        final Set<String> statuses = new LinkedHashSet<>();
        int occurrences;
        List<String> sortedVersions() { return versions.stream().sorted().toList(); }
        List<String> sortedAdvisories() { return advisories.keySet().stream().sorted().toList(); }
        String assessment() { return statuses.isEmpty() ? "NOT_ASSESSED" : statuses.size() == 1 ? statuses.iterator().next() : "MIXED"; }
        boolean hasUnverifiedAssessment() { return statuses.isEmpty() || statuses.stream().anyMatch(s -> !s.equals("MATCHED") && !s.equals("NO_KNOWN_ADVISORIES")); }
    }
    private static String reference(JsonNode advisory) {
        for (JsonNode item : advisory.path("references")) {
            String url = item.path("url").asText("");
            if (url.startsWith("https://")) return url;
        }
        return "";
    }
    private record PackageKey(String ecosystem, String namespace, String name) {
        String display() { return namespace.isBlank() ? name : namespace + ":" + name; }
    }
}
