package com.dependencyintelligence.sbom.infrastructure.persistence;

import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.domain.Scan;
import com.dependencyintelligence.sbom.application.dto.ParsedSbom;
import com.dependencyintelligence.sbom.application.exception.ConflictingPackageException;
import com.dependencyintelligence.sbom.application.port.out.SbomImportStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class JdbcSbomImportAdapter implements SbomImportStore {
    private final JdbcTemplate jdbc;
    private final ScanStore scans;

    public JdbcSbomImportAdapter(JdbcTemplate jdbc, ScanStore scans) {
        this.jdbc = jdbc;
        this.scans = scans;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Scan snapshot, ParsedSbom sbom, String sha256) {
        scans.add(snapshot);
        var organizationId = snapshot.organizationId().value();
        jdbc.update("""
            insert into sbom_imports(organization_id, scan_id, format, specification_version, serial_number, sha256)
            values (?, ?, ?, ?, ?, ?)
            """, organizationId, snapshot.id(), sbom.format(), sbom.specificationVersion(), sbom.serialNumber(), sha256);
        for (var occurrence : sbom.components()) {
            var coordinates = occurrence.coordinates();
            // Conditional upsert handles concurrent imports without rewriting catalog coordinates.
            var matches = jdbc.query("""
                insert into components(id, organization_id, purl, ecosystem, namespace, name, version)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (organization_id, purl) do update set id = components.id
                where components.ecosystem = excluded.ecosystem and components.namespace = excluded.namespace
                    and components.name = excluded.name and components.version = excluded.version
                returning id
                """, (rs, row) -> rs.getObject("id", UUID.class), UUID.randomUUID(), organizationId,
                coordinates.purl(), coordinates.ecosystem(), coordinates.namespace(), coordinates.name(), coordinates.version());
            if (matches.isEmpty()) { throw new ConflictingPackageException(); }
            jdbc.update("""
                insert into scan_components(organization_id, scan_id, bom_ref, component_id, dependency_type)
                values (?, ?, ?, ?, 'UNKNOWN')
                """, organizationId, snapshot.id(), occurrence.bomRef(), matches.getFirst());
        }
    }
}
