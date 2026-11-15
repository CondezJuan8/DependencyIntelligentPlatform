-- Preserve applied V1-V3. Catalogues are global; private package identities remain tenant-scoped.
CREATE TABLE package_ecosystems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    name TEXT NOT NULL CHECK (btrim(name) <> '')
);
INSERT INTO package_ecosystems(code, name) VALUES ('maven', 'Maven'), ('npm', 'npm'), ('pypi', 'PyPI'), ('nuget', 'NuGet'), ('golang', 'Go');
INSERT INTO package_ecosystems(code, name)
SELECT DISTINCT lower(ecosystem), ecosystem FROM components ON CONFLICT (code) DO NOTHING;

ALTER TABLE components RENAME TO legacy_components_v3;
CREATE TABLE components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    package_ecosystem_id UUID NOT NULL REFERENCES package_ecosystems(id) ON DELETE RESTRICT,
    namespace TEXT,
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    description TEXT,
    UNIQUE (organization_id, id),
    CONSTRAINT uq_component_identity UNIQUE NULLS NOT DISTINCT (organization_id, package_ecosystem_id, namespace, name),
    CHECK (namespace IS NULL OR btrim(namespace) <> '')
);

CREATE TEMPORARY TABLE component_transition ON COMMIT DROP AS
SELECT legacy.id AS old_id, legacy.organization_id, legacy.version, legacy.purl,
       first_value(legacy.id) OVER (
           PARTITION BY legacy.organization_id, lower(legacy.ecosystem), nullif(legacy.namespace, ''), legacy.name
           ORDER BY legacy.id) AS package_id,
       first_value(legacy.id) OVER (
           PARTITION BY legacy.organization_id, lower(legacy.ecosystem), nullif(legacy.namespace, ''), legacy.name, legacy.version
           ORDER BY legacy.id) AS version_id
FROM legacy_components_v3 legacy;

INSERT INTO components(id, organization_id, package_ecosystem_id, namespace, name)
SELECT DISTINCT transition.package_id, legacy.organization_id, ecosystem.id, nullif(legacy.namespace, ''), legacy.name
FROM legacy_components_v3 legacy JOIN component_transition transition ON transition.old_id = legacy.id
JOIN package_ecosystems ecosystem ON ecosystem.code = lower(legacy.ecosystem);

CREATE TABLE component_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    component_id UUID NOT NULL,
    version TEXT NOT NULL CHECK (btrim(version) <> ''),
    published_at TIMESTAMPTZ,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, component_id, version),
    FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT
);
INSERT INTO component_versions(id, organization_id, component_id, version)
SELECT DISTINCT version_id, organization_id, package_id, version FROM component_transition;

CREATE TABLE component_identifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    component_version_id UUID NOT NULL,
    identifier_type TEXT NOT NULL CHECK (identifier_type IN ('PURL', 'CPE', 'SWID')),
    value TEXT NOT NULL CHECK (btrim(value) <> ''),
    UNIQUE (organization_id, identifier_type, value),
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
INSERT INTO component_identifiers(organization_id, component_version_id, identifier_type, value)
SELECT organization_id, version_id, 'PURL', purl FROM component_transition;

ALTER TABLE scan_components ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE scan_components ADD CONSTRAINT uq_scan_component_id UNIQUE (organization_id, scan_id, id);
ALTER TABLE scan_components ADD CONSTRAINT uq_scan_component_tenant_id UNIQUE (organization_id, id);
ALTER TABLE scan_components ADD COLUMN component_version_id UUID;
UPDATE scan_components occurrence SET component_version_id = transition.version_id
FROM component_transition transition WHERE occurrence.component_id = transition.old_id;
ALTER TABLE scan_components ALTER COLUMN component_version_id SET NOT NULL;
ALTER TABLE scan_components ADD CONSTRAINT fk_scan_component_version FOREIGN KEY (organization_id, component_version_id)
    REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT;
ALTER TABLE scan_components DROP COLUMN component_id;
CREATE INDEX ix_scan_component_versions ON scan_components(organization_id, component_version_id);
DROP TABLE legacy_components_v3;

CREATE TABLE dependency_scopes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE
);
INSERT INTO dependency_scopes(code) VALUES ('runtime'), ('development'), ('test'), ('optional'), ('provided'), ('unknown');
CREATE TABLE dependency_relationship_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE
);
INSERT INTO dependency_relationship_types(code) VALUES ('depends_on'), ('contains'), ('provides'), ('optional_dependency');
ALTER TABLE scan_components ADD COLUMN dependency_scope_id UUID REFERENCES dependency_scopes(id) ON DELETE RESTRICT;
ALTER TABLE scan_components ADD COLUMN is_root BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE dependency_relationships RENAME TO dependency_edges;
ALTER TABLE dependency_edges ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE dependency_edges ADD COLUMN source_scan_component_id UUID;
ALTER TABLE dependency_edges ADD COLUMN target_scan_component_id UUID;
ALTER TABLE dependency_edges ADD COLUMN relationship_type_id UUID REFERENCES dependency_relationship_types(id) ON DELETE RESTRICT;
UPDATE dependency_edges edge SET source_scan_component_id = occurrence.id
FROM scan_components occurrence WHERE occurrence.organization_id = edge.organization_id AND occurrence.scan_id = edge.scan_id AND occurrence.bom_ref = edge.parent_ref;
UPDATE dependency_edges edge SET target_scan_component_id = occurrence.id
FROM scan_components occurrence WHERE occurrence.organization_id = edge.organization_id AND occurrence.scan_id = edge.scan_id AND occurrence.bom_ref = edge.child_ref;
UPDATE dependency_edges SET relationship_type_id = (SELECT id FROM dependency_relationship_types WHERE code = 'depends_on');
ALTER TABLE dependency_edges ALTER COLUMN source_scan_component_id SET NOT NULL;
ALTER TABLE dependency_edges ALTER COLUMN target_scan_component_id SET NOT NULL;
ALTER TABLE dependency_edges ALTER COLUMN relationship_type_id SET NOT NULL;
ALTER TABLE dependency_edges DROP CONSTRAINT dependency_relationships_pkey;
ALTER TABLE dependency_edges DROP COLUMN parent_ref;
ALTER TABLE dependency_edges DROP COLUMN child_ref;
ALTER TABLE dependency_edges ADD PRIMARY KEY (id);
ALTER TABLE dependency_edges ADD CONSTRAINT uq_dependency_edge UNIQUE (organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id);
ALTER TABLE dependency_edges ADD CONSTRAINT ck_dependency_edge_not_self CHECK (source_scan_component_id <> target_scan_component_id);
ALTER TABLE dependency_edges ADD CONSTRAINT fk_edge_source FOREIGN KEY (organization_id, scan_id, source_scan_component_id)
    REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT;
ALTER TABLE dependency_edges ADD CONSTRAINT fk_edge_target FOREIGN KEY (organization_id, scan_id, target_scan_component_id)
    REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT;
CREATE INDEX ix_dependency_edge_target ON dependency_edges(organization_id, scan_id, target_scan_component_id);
CREATE INDEX ix_components_lookup ON components(organization_id, package_ecosystem_id, name);

COMMENT ON TABLE components IS 'component: Tenant-private logical package identity; versions and occurrences are independent relations.';
COMMENT ON TABLE component_versions IS 'component: Exact textual version; no universal SemVer assumptions.';
COMMENT ON TABLE component_identifiers IS 'component: Alternate version identifiers, including multiple PURLs for the same package version.';
COMMENT ON TABLE scan_components IS 'scan: Snapshot occurrence. bom_ref is local to the SBOM; classification requires evidence.';
COMMENT ON TABLE dependency_edges IS 'dependencygraph: Directed observed relation between occurrences of the same tenant and snapshot.';
