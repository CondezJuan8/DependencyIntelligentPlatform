-- Tenant-owned relations use composite foreign keys, including snapshot identity.
CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    slug VARCHAR(100) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    subject VARCHAR(255) NOT NULL UNIQUE CHECK (btrim(subject) <> ''),
    email VARCHAR(320) NOT NULL CHECK (btrim(email) <> ''),
    display_name VARCHAR(200) NOT NULL CHECK (btrim(display_name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE memberships (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX ix_memberships_user ON memberships(user_id);

CREATE TABLE integrations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    provider VARCHAR(40) NOT NULL CHECK (btrim(provider) <> '' AND provider = lower(provider)),
    external_id VARCHAR(255) NOT NULL CHECK (btrim(external_id) <> ''),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, provider, external_id)
);

CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    integration_id UUID,
    provider VARCHAR(40) NOT NULL CHECK (btrim(provider) <> '' AND provider = lower(provider)),
    external_id VARCHAR(255) NOT NULL CHECK (btrim(external_id) <> ''),
    full_name VARCHAR(512) NOT NULL CHECK (btrim(full_name) <> ''),
    default_branch VARCHAR(255) NOT NULL CHECK (btrim(default_branch) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (organization_id, id),
    CONSTRAINT uq_repository_provider_identity UNIQUE (organization_id, provider, external_id),
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id)
);
CREATE INDEX ix_repositories_integration ON repositories(organization_id, integration_id);

CREATE TABLE scans (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    git_reference VARCHAR(255) NOT NULL CHECK (btrim(git_reference) <> ''),
    commit_sha VARCHAR(64),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    scanner_name VARCHAR(100),
    scanner_version VARCHAR(100),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_reason TEXT,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id),
    CHECK (started_at IS NULL OR started_at >= requested_at),
    CHECK (finished_at IS NULL OR finished_at >= started_at),
    CHECK (
        (status = 'PENDING' AND started_at IS NULL AND finished_at IS NULL AND failure_reason IS NULL) OR
        (status = 'RUNNING' AND started_at IS NOT NULL AND finished_at IS NULL AND failure_reason IS NULL) OR
        (status = 'COMPLETED' AND started_at IS NOT NULL AND finished_at IS NOT NULL AND failure_reason IS NULL) OR
        (status = 'FAILED' AND started_at IS NOT NULL AND finished_at IS NOT NULL AND failure_reason IS NOT NULL AND btrim(failure_reason) <> '')
    )
);
CREATE INDEX ix_scans_history ON scans(organization_id, repository_id, requested_at DESC);
CREATE INDEX ix_scans_pending ON scans(requested_at) WHERE status = 'PENDING';

CREATE TABLE scan_artifacts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    format VARCHAR(40) NOT NULL CHECK (btrim(format) <> ''),
    storage_key TEXT NOT NULL CHECK (btrim(storage_key) <> ''),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id)
);
CREATE INDEX ix_artifacts_scan ON scan_artifacts(organization_id, scan_id);

-- Tenant-local catalog: private package names are not shared across organizations.
CREATE TABLE components (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    purl TEXT NOT NULL CHECK (purl LIKE 'pkg:%'),
    ecosystem VARCHAR(100) NOT NULL CHECK (btrim(ecosystem) <> ''),
    name VARCHAR(512) NOT NULL CHECK (btrim(name) <> ''),
    version VARCHAR(255) NOT NULL CHECK (btrim(version) <> ''),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, purl)
);
CREATE INDEX ix_components_lookup ON components(organization_id, ecosystem, name);

CREATE TABLE scan_components (
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    bom_ref VARCHAR(512) NOT NULL CHECK (btrim(bom_ref) <> ''),
    component_id UUID NOT NULL,
    dependency_type VARCHAR(20) NOT NULL CHECK (dependency_type IN ('DIRECT', 'TRANSITIVE', 'UNKNOWN')),
    PRIMARY KEY (organization_id, scan_id, bom_ref),
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id),
    FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id)
);
CREATE INDEX ix_scan_components_component ON scan_components(organization_id, component_id);

CREATE TABLE dependency_relationships (
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    parent_ref VARCHAR(512) NOT NULL,
    child_ref VARCHAR(512) NOT NULL,
    PRIMARY KEY (organization_id, scan_id, parent_ref, child_ref),
    FOREIGN KEY (organization_id, scan_id, parent_ref) REFERENCES scan_components(organization_id, scan_id, bom_ref),
    FOREIGN KEY (organization_id, scan_id, child_ref) REFERENCES scan_components(organization_id, scan_id, bom_ref),
    CHECK (parent_ref <> child_ref)
);
CREATE INDEX ix_relationships_child ON dependency_relationships(organization_id, scan_id, child_ref);

-- Public advisory catalog. Tenant findings reference this source explicitly.
CREATE TABLE vulnerabilities (
    id UUID PRIMARY KEY,
    source VARCHAR(40) NOT NULL CHECK (btrim(source) <> ''),
    external_id VARCHAR(255) NOT NULL CHECK (btrim(external_id) <> ''),
    summary TEXT,
    severity VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN')),
    source_url TEXT,
    published_at TIMESTAMPTZ,
    modified_at TIMESTAMPTZ,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (source, external_id)
);

CREATE TABLE vulnerability_findings (
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    bom_ref VARCHAR(512) NOT NULL,
    vulnerability_id UUID NOT NULL REFERENCES vulnerabilities(id),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fixed_versions JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(fixed_versions) = 'array'),
    PRIMARY KEY (organization_id, scan_id, bom_ref, vulnerability_id),
    FOREIGN KEY (organization_id, scan_id, bom_ref) REFERENCES scan_components(organization_id, scan_id, bom_ref)
);
CREATE INDEX ix_findings_vulnerability ON vulnerability_findings(vulnerability_id);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL CHECK (btrim(action) <> ''),
    resource_type VARCHAR(100) NOT NULL CHECK (btrim(resource_type) <> ''),
    resource_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_audit_history ON audit_events(organization_id, occurred_at DESC);
