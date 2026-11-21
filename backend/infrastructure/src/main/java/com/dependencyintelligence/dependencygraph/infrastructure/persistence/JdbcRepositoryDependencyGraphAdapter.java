package com.dependencyintelligence.dependencygraph.infrastructure.persistence;

import com.dependencyintelligence.dependencygraph.application.dto.RepositoryDependencyGraph;
import com.dependencyintelligence.dependencygraph.application.port.out.RepositoryDependencyGraphReader;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class JdbcRepositoryDependencyGraphAdapter implements RepositoryDependencyGraphReader {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public JdbcRepositoryDependencyGraphAdapter(JdbcTemplate jdbc, TenantTransactions tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Override public RepositoryDependencyGraph read(UUID organizationId, UUID repositoryId) {
        return tenants.execute(organizationId, () -> readWithinTenant(organizationId, repositoryId));
    }

    private RepositoryDependencyGraph readWithinTenant(UUID organizationId, UUID repositoryId) {
        List<String> names = jdbc.query("""
            select full_name from repositories where organization_id=? and id=? and is_enabled
            """, (row, index) -> row.getString(1), organizationId, repositoryId);
        if (names.isEmpty()) throw new IllegalArgumentException("Repositorio no disponible.");

        List<SourceRow> scans = jdbc.query("""
            select id,scanner_name,source_sha256,git_reference,finished_at,unresolved_count
            from scans
            where organization_id=? and repository_id=? and status='COMPLETED'
              and scanner_name in ('maven-manifest','npm-lockfile','cdxgen-maven','cdxgen-npm')
              and id=(select candidate.id from scans candidate
                     where candidate.organization_id=scans.organization_id
                       and candidate.repository_id=scans.repository_id
                       and ((candidate.scanner_name in ('maven-manifest','cdxgen-maven') and scans.scanner_name in ('maven-manifest','cdxgen-maven'))
                         or (candidate.scanner_name in ('npm-lockfile','cdxgen-npm') and scans.scanner_name in ('npm-lockfile','cdxgen-npm')))
                       and candidate.status='COMPLETED'
                     order by candidate.finished_at desc limit 1)
            order by scanner_name
            """, (row, index) -> new SourceRow(row.getObject(1, UUID.class), ecosystem(row.getString(2)),
                row.getString(3), row.getString(4), row.getTimestamp(5).toInstant(), row.getInt(6)), organizationId, repositoryId);

        List<RepositoryDependencyGraph.Source> sources = scans.stream().map(source ->
            new RepositoryDependencyGraph.Source(source.id(), source.ecosystem(), source.sha256(), source.commitSha(),
                source.analyzedAt(), source.unresolvedCount())).toList();
        if (scans.isEmpty()) return new RepositoryDependencyGraph(repositoryId, names.getFirst(), List.of(), List.of(), List.of(), 0);

        List<UUID> scanIds = scans.stream().map(SourceRow::id).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(scanIds.size(), "?"));
        Object[] scanArguments = new Object[scanIds.size() + 1];
        scanArguments[0] = organizationId;
        for (int index = 0; index < scanIds.size(); index++) scanArguments[index + 1] = scanIds.get(index);
        List<NodeRow> packageRows = jdbc.query("""
            select sc.id,sc.scan_id,pe.code,c.namespace,c.name,cv.version,ds.code,sc.dependency_type,sc.bom_ref
            from scan_components sc
            join component_versions cv on cv.organization_id=sc.organization_id and cv.id=sc.component_version_id
            join components c on c.organization_id=cv.organization_id and c.id=cv.component_id
            join package_ecosystems pe on pe.id=c.package_ecosystem_id
            join dependency_scopes ds on ds.id=sc.dependency_scope_id
            where sc.organization_id=? and sc.scan_id in (""" + placeholders + ") order by pe.code,c.name,cv.version,sc.bom_ref",
            (row, index) -> new NodeRow(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3),
                row.getString(4), row.getString(5), row.getString(6), row.getString(7), row.getString(8), row.getString(9)),
            scanArguments);

        List<RepositoryDependencyGraph.Node> nodes = new ArrayList<>();
        List<RepositoryDependencyGraph.Edge> edges = new ArrayList<>();
        Map<UUID, NodeRow> nodeById = new HashMap<>();
        for (NodeRow row : packageRows) {
            String label = row.namespace() == null || row.namespace().isBlank() ? row.name()
                : row.namespace() + ":" + row.name();
            label += "@" + row.version();
            nodes.add(new RepositoryDependencyGraph.Node(row.id().toString(), label, row.version(), row.ecosystem(),
                row.scope(), row.dependencyType(), manifestPath(row.bomRef()), false));
            nodeById.put(row.id(), row);
        }

        for (SourceRow scan : scans) {
            List<NodeRow> ecosystemRows = packageRows.stream().filter(row -> row.scanId().equals(scan.id())).toList();
            if (ecosystemRows.isEmpty()) continue;
            String rootId = "project:" + scan.id();
            nodes.add(new RepositoryDependencyGraph.Node(rootId, names.getFirst() + " · " + scan.ecosystem(), "",
                scan.ecosystem(), "", "PROJECT", "", true));
            for (NodeRow row : ecosystemRows) {
                if ("DIRECT".equals(row.dependencyType())) {
                    edges.add(new RepositoryDependencyGraph.Edge("declares:" + scan.id() + ":" + row.id(), rootId,
                        row.id().toString(), "declares", scan.ecosystem()));
                }
            }
        }

        List<EdgeRow> edgeRows = jdbc.query("""
            select edge.id,edge.scan_id,edge.source_scan_component_id,edge.target_scan_component_id,kind.code
            from dependency_edges edge join dependency_relationship_types kind on kind.id=edge.relationship_type_id
            where edge.organization_id=? and edge.scan_id in (""" + placeholders + ") order by edge.id",
            (row, index) -> new EdgeRow(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class), row.getObject(4, UUID.class), row.getString(5)), scanArguments);
        for (EdgeRow edge : edgeRows) {
            NodeRow source = nodeById.get(edge.sourceId());
            NodeRow target = nodeById.get(edge.targetId());
            if (source == null || target == null) continue;
            edges.add(new RepositoryDependencyGraph.Edge(edge.id().toString(), edge.sourceId().toString(),
                edge.targetId().toString(), edge.relationship(), source.ecosystem()));
        }

        return new RepositoryDependencyGraph(repositoryId, names.getFirst(), sources, nodes, edges,
            scans.stream().mapToInt(SourceRow::unresolvedCount).sum());
    }

    private static String ecosystem(String scanner) { return scanner.endsWith("npm") || "npm-lockfile".equals(scanner) ? "npm" : "maven"; }
    private static String manifestPath(String bomRef) {
        int marker = bomRef.indexOf('#');
        return marker < 0 ? bomRef : bomRef.substring(0, marker);
    }

    private record SourceRow(UUID id, String ecosystem, String sha256, String commitSha,
                             java.time.Instant analyzedAt, int unresolvedCount) { }
    private record NodeRow(UUID id, UUID scanId, String ecosystem, String namespace, String name, String version,
                           String scope, String dependencyType, String bomRef) { }
    private record EdgeRow(UUID id, UUID scanId, UUID sourceId, UUID targetId, String relationship) { }
}
