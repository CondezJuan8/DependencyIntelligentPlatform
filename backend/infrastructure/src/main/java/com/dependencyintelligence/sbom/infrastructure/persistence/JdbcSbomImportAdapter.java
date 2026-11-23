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
import java.util.Objects;

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
            insert into sbom_documents(organization_id, scan_id, format, specification_version, serial_number, sha256)
            values (?, ?, ?, ?, ?, ?)
            """, organizationId, snapshot.id(), sbom.format(), sbom.specificationVersion(), sbom.serialNumber(), sha256);
        for (var occurrence : sbom.components()) {
            var coordinates = occurrence.coordinates();
            var existing = jdbc.query("""
                select version.id, ecosystem.code, package.namespace, package.name, version.version
                from component_identifiers identifier
                join component_versions version on version.organization_id = identifier.organization_id and version.id = identifier.component_version_id
                join components package on package.organization_id = version.organization_id and package.id = version.component_id
                join package_ecosystems ecosystem on ecosystem.id = package.package_ecosystem_id
                where identifier.organization_id = ? and identifier.identifier_type = 'PURL' and identifier.value = ?
                """, (rs, row) -> new ExistingVersion(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("namespace"), rs.getString("name"), rs.getString("version")), organizationId, coordinates.purl());
            UUID versionId;
            if (!existing.isEmpty()) {
                var version = existing.getFirst();
                if (!version.ecosystem().equalsIgnoreCase(coordinates.ecosystem())
                        || !Objects.equals(version.namespace(), coordinates.namespace())
                        || !version.name().equals(coordinates.name()) || !version.version().equals(coordinates.version())) {
                    throw new ConflictingPackageException();
                }
                versionId = version.id();
            } else {
                var ecosystemId = jdbc.queryForObject("select id from package_ecosystems where code = lower(?)", UUID.class, coordinates.ecosystem());
                var packageId = jdbc.queryForObject("""
                    insert into components(organization_id, package_ecosystem_id, namespace, name) values (?, ?, ?, ?)
                    on conflict on constraint uq_component_identity do update set id = components.id returning id
                    """, UUID.class, organizationId, ecosystemId, coordinates.namespace(), coordinates.name());
                versionId = jdbc.queryForObject("""
                    insert into component_versions(organization_id, component_id, version) values (?, ?, ?)
                    on conflict (organization_id, component_id, version) do update set id = component_versions.id returning id
                    """, UUID.class, organizationId, packageId, coordinates.version());
                var identifiers = jdbc.query("""
                    insert into component_identifiers(organization_id, component_version_id, identifier_type, value) values (?, ?, 'PURL', ?)
                    on conflict (organization_id, identifier_type, value) do update set id = component_identifiers.id
                    where component_identifiers.component_version_id = excluded.component_version_id returning id
                    """, (rs, row) -> rs.getObject("id", UUID.class), organizationId, versionId, coordinates.purl());
                if (identifiers.isEmpty()) { throw new ConflictingPackageException(); }
            }
            jdbc.update("""
                insert into scan_components(organization_id, scan_id, bom_ref, component_version_id, dependency_type, dependency_scope_id)
                values (?, ?, ?, ?, ?, (select id from dependency_scopes where code=?))
                """, organizationId, snapshot.id(), occurrence.bomRef(), versionId, occurrence.dependencyType(), occurrence.scope());
        }
    }

    private record ExistingVersion(UUID id, String ecosystem, String namespace, String name, String version) { }
}
