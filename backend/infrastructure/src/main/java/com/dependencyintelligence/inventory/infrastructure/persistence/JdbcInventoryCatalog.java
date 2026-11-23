package com.dependencyintelligence.inventory.infrastructure.persistence;

import static com.dependencyintelligence.inventory.application.dto.InventoryCatalog.*;

import com.dependencyintelligence.inventory.application.port.out.InventoryCatalogReader;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import com.dependencyintelligence.vulnerability.infrastructure.configuration.OsvProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcInventoryCatalog implements InventoryCatalogReader {
    private static final String SCANS = """
        SELECT DISTINCT ON (s.repository_id, CASE WHEN s.scanner_name IN ('maven-manifest','cdxgen-maven') THEN 0 ELSE 1 END)
               s.id, s.repository_id, r.full_name, r.provider, s.finished_at, s.git_reference, s.source_sha256
        FROM scans s
        JOIN repositories r ON r.organization_id=s.organization_id AND r.id=s.repository_id AND r.is_enabled
        WHERE s.organization_id=:organization AND s.repository_id IN (:repositories) AND s.status='COMPLETED'
          AND s.scanner_name IN ('maven-manifest','cdxgen-maven','npm-lockfile','cdxgen-npm')
        ORDER BY s.repository_id, CASE WHEN s.scanner_name IN ('maven-manifest','cdxgen-maven') THEN 0 ELSE 1 END,
                 s.finished_at DESC, s.id DESC
        """;
    private static final String COMPONENTS = """
        SELECT o.id, o.scan_id, upper(e.code) AS ecosystem, coalesce(c.namespace,'') AS namespace, c.name,
               v.version, d.code AS scope, o.dependency_type,
               coalesce(o.source_manifest_path, split_part(o.bom_ref,'#',1)) AS manifest_path
        FROM scan_components o
        JOIN component_versions v ON v.organization_id=o.organization_id AND v.id=o.component_version_id
        JOIN components c ON c.organization_id=v.organization_id AND c.id=v.component_id
        JOIN package_ecosystems e ON e.id=c.package_ecosystem_id
        LEFT JOIN dependency_scopes d ON d.id=o.dependency_scope_id
        WHERE o.organization_id=:organization AND o.scan_id IN (:scans)
        """;
    private static final String EVIDENCE = """
        SELECT a.scan_component_id, a.assessment_status, a.checked_at, a.usable,
               jsonb_array_length(a.advisories) AS advisory_count,
               ARRAY(SELECT DISTINCT coalesce(upper(item ->> 'severity'),'UNKNOWN')
                     FROM jsonb_array_elements(a.advisories) items(item)) AS severities
        FROM (
            SELECT DISTINCT ON (scan_component_id, assessment_status IN ('MATCHED','NO_KNOWN_ADVISORIES','INCOMPLETE'))
                   scan_component_id, assessment_status, checked_at, advisories,
                   assessment_status IN ('MATCHED','NO_KNOWN_ADVISORIES','INCOMPLETE') AS usable
            FROM scan_vulnerability_assessments
            WHERE organization_id=:organization AND scan_id IN (:scans) AND source='OSV' %s
            ORDER BY scan_component_id, usable, checked_at DESC, id DESC
        ) a
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final Clock clock;
    private final OsvProperties osv;

    public JdbcInventoryCatalog(NamedParameterJdbcTemplate jdbc, TenantTransactions tenants, Clock clock, OsvProperties osv) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
        this.osv = osv;
    }

    @Override public CatalogPage packages(UUID organizationId, List<UUID> repositories, Filters filters, Page page) {
        var grouped = new HashMap<PackageKey, PackageMatches>();
        for (var row : read(organizationId, repositories, filters, null)) {
            grouped.computeIfAbsent(row.key(), ignored -> new PackageMatches()).add(row.occurrence());
        }
        Comparator<PackageSummary> order = Comparator.comparing((PackageSummary item) -> item.key().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(item -> item.key().name()).thenComparing(item -> item.key().ecosystem())
            .thenComparing(item -> item.key().namespace());
        if (filters.riskFirst()) order = Comparator.comparingInt((PackageSummary item) -> rank(item.severity())).reversed().thenComparing(order);
        var sorted = grouped.entrySet().stream().map(entry -> entry.getValue().summary(entry.getKey())).sorted(order).toList();
        return new CatalogPage(page(sorted, page), sorted.size(), sorted.stream().mapToLong(PackageSummary::versions).sum());
    }

    @Override public OccurrencePage occurrences(UUID organizationId, List<UUID> repositories, Filters filters, PackageKey key, Page page) {
        var sorted = read(organizationId, repositories, filters, key).stream().map(CatalogRow::occurrence)
            .sorted(Comparator.comparing(Occurrence::version)
                .thenComparing(Occurrence::repositoryName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Occurrence::repositoryId).thenComparing(Occurrence::manifestPath).thenComparing(Occurrence::id))
            .toList();
        return new OccurrencePage(page(sorted, page), sorted.size());
    }

    private List<CatalogRow> read(UUID organizationId, List<UUID> repositories, Filters filters, PackageKey key) {
        return tenants.execute(organizationId, () -> {
            // The stage queries use the organization-prefixed indexes and keep RLS on the runtime connection.
            jdbc.getJdbcTemplate().execute("SET LOCAL jit = off");
            jdbc.getJdbcTemplate().execute("SET LOCAL join_collapse_limit = 1");
            jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = '10s'");
            var parameters = new MapSqlParameterSource("organization", organizationId).addValue("repositories", repositories);
            var snapshots = new HashMap<UUID, Snapshot>();
            for (var scan : jdbc.query(SCANS, parameters, (row, index) -> new Snapshot(row.getObject("id", UUID.class),
                    row.getObject("repository_id", UUID.class), row.getString("full_name"), row.getString("provider"),
                    time(row, "finished_at"), row.getString("git_reference"), row.getString("source_sha256")))) {
                snapshots.put(scan.id(), scan);
            }
            if (snapshots.isEmpty()) return List.of();
            parameters.addValue("scans", snapshots.keySet());
            String sql = componentQuery(filters, key, parameters);
            String queryText = filters.query().toLowerCase(Locale.ROOT);
            var components = jdbc.query(sql, parameters, (row, index) -> new ComponentRow(row.getObject("id", UUID.class),
                row.getObject("scan_id", UUID.class), new PackageKey(row.getString("ecosystem"), row.getString("namespace"), row.getString("name")),
                row.getString("version"), row.getString("scope"), row.getString("dependency_type"), row.getString("manifest_path")))
                .stream().filter(item -> matchesQuery(queryText, item, snapshots.get(item.scanId()))).toList();
            if (components.isEmpty()) return List.of();
            var assessments = new HashMap<UUID, Evidence>();
            var attempts = new HashMap<UUID, Evidence>();
            String evidenceSql = EVIDENCE.formatted(components.size() <= 500 ? "AND scan_component_id IN (:occurrences)" : "");
            if (components.size() <= 500) parameters.addValue("occurrences", components.stream().map(ComponentRow::id).toList());
            jdbc.query(evidenceSql, parameters, row -> {
                UUID occurrence = row.getObject("scan_component_id", UUID.class);
                var result = new Evidence(row.getString("assessment_status"), time(row, "checked_at"),
                    row.getInt("advisory_count"), severities(row));
                (row.getBoolean("usable") ? assessments : attempts).put(occurrence, result);
            });
            Instant validSince = clock.instant().minus(osv.freshness());
            List<CatalogRow> matching = new ArrayList<>();
            for (var component : components) {
                Snapshot source = snapshots.get(component.scanId());
                Evidence evidence = assessments.get(component.id());
                Evidence attempt = latest(evidence, attempts.get(component.id()));
                String status = status(evidence, attempt, validSince);
                if (!matchesSecurity(filters, evidence, status)) continue;
                String severity = evidence == null ? null : evidence.severities().stream().max(Comparator.comparingInt(JdbcInventoryCatalog::rank)).orElse(null);
                matching.add(new CatalogRow(component.key(), new Occurrence(component.id(), source.repositoryId(),
                    source.repositoryName(), source.provider(), component.version(), component.manifestPath(), component.scope(),
                    component.kind(), source.id(), source.analyzedAt(), source.commit(), source.hash(), status,
                    attempt == null ? null : attempt.checkedAt(), evidence == null ? 0 : evidence.advisoryCount(), severity)));
            }
            return matching;
        });
    }

    private static String componentQuery(Filters filters, PackageKey key, MapSqlParameterSource parameters) {
        var sql = new StringBuilder(COMPONENTS);
        filter(sql, parameters, "upper(e.code)", "ecosystem", filters.ecosystem());
        filter(sql, parameters, "v.version", "version", filters.version());
        filter(sql, parameters, "o.dependency_type", "kind", filters.kind());
        filter(sql, parameters, "d.code", "scope", filters.scope());
        if (key != null) {
            filter(sql, parameters, "upper(e.code)", "packageEcosystem", key.ecosystem());
            filter(sql, parameters, "c.name", "name", key.name());
            sql.append(" AND coalesce(c.namespace,'')=:namespace");
            parameters.addValue("namespace", key.namespace());
        }
        return sql.toString();
    }

    private static void filter(StringBuilder sql, MapSqlParameterSource parameters, String column, String parameter, String value) {
        if (value.isEmpty()) return;
        sql.append(" AND ").append(column).append("=:").append(parameter);
        parameters.addValue(parameter, value);
    }

    private static boolean matchesQuery(String query, ComponentRow item, Snapshot source) {
        if (query.isEmpty()) return true;
        String coordinates = item.key().ecosystem().equals("MAVEN") ? item.key().namespace() + ":" + item.key().name() : item.key().name();
        return String.join(" ", item.key().name(), coordinates, item.version(), item.manifestPath(), source.repositoryName())
            .toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean matchesSecurity(Filters filters, Evidence evidence, String status) {
        if (!filters.severity().isEmpty() && (evidence == null || !evidence.severities().contains(filters.severity()))) return false;
        return switch (filters.security()) {
            case "VULNERABLE" -> evidence != null && evidence.advisoryCount() > 0;
            case "NO_KNOWN" -> status.equals("NO_KNOWN_ADVISORIES");
            case "REVIEW" -> needsReview(status);
            default -> true;
        };
    }

    private static boolean needsReview(String status) {
        return !status.equals("MATCHED") && !status.equals("NO_KNOWN_ADVISORIES");
    }

    private static Evidence latest(Evidence evidence, Evidence attempt) {
        if (evidence == null) return attempt;
        if (attempt == null || evidence.checkedAt().isAfter(attempt.checkedAt())) return evidence;
        return attempt;
    }

    private static String status(Evidence evidence, Evidence attempt, Instant validSince) {
        if (attempt == null) return "NOT_ASSESSED";
        if (evidence != null && (attempt.status().equals("UNAVAILABLE") || evidence.checkedAt().isBefore(validSince))) return "STALE";
        return attempt.status();
    }

    private static Set<String> severities(ResultSet row) throws SQLException {
        var array = row.getArray("severities");
        try {
            Set<String> result = new HashSet<>();
            for (String severity : (String[]) array.getArray()) result.add(rank(severity) > 0 ? severity : "UNKNOWN");
            return result;
        } finally {
            array.free();
        }
    }

    private static int rank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            case "UNKNOWN" -> 0;
            default -> -1;
        };
    }

    private static Instant time(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <T> List<T> page(List<T> rows, Page page) {
        int from = Math.min(page.offset(), rows.size());
        return rows.subList(from, from + Math.min(page.size(), rows.size() - from));
    }

    private record Snapshot(UUID id, UUID repositoryId, String repositoryName, String provider, Instant analyzedAt, String commit, String hash) {}
    private record ComponentRow(UUID id, UUID scanId, PackageKey key, String version, String scope, String kind, String manifestPath) {}
    private record Evidence(String status, Instant checkedAt, int advisoryCount, Set<String> severities) {}
    private record CatalogRow(PackageKey key, Occurrence occurrence) {}

    private static final class PackageMatches {
        private final Set<String> versions = new HashSet<>();
        private final Set<UUID> repositories = new HashSet<>();
        private int occurrences;
        private int withAdvisories;
        private int pending;
        private String severity;

        void add(Occurrence item) {
            versions.add(item.version());
            repositories.add(item.repositoryId());
            occurrences++;
            if (item.advisoryCount() > 0) withAdvisories++;
            if (needsReview(item.assessment())) pending++;
            if (rank(item.severity()) > rank(severity)) severity = item.severity();
        }

        PackageSummary summary(PackageKey key) {
            return new PackageSummary(key, versions.size(), repositories.size(), occurrences, withAdvisories, severity, pending);
        }
    }
}
