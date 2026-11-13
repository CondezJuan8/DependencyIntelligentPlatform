-- Immutable after application. See docs/database/README.md.

CREATE TABLE vulnerabilities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_source_id uuid NOT NULL,
    external_id text NOT NULL,
    summary text,
    description text,
    published_at timestamptz,
    modified_at timestamptz,
    withdrawn_at timestamptz,
    UNIQUE NULLS NOT DISTINCT (vulnerability_source_id,external_id),
    FOREIGN KEY (vulnerability_source_id) REFERENCES vulnerability_sources (id) ON DELETE RESTRICT
);

CREATE TABLE vulnerability_aliases (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_id uuid NOT NULL,
    alias_type text NOT NULL,
    value text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,alias_type,value),
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT
);

CREATE TABLE vulnerability_references (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_id uuid NOT NULL,
    reference_type text NOT NULL,
    url text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,url),
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT
);

CREATE TABLE vulnerability_severities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_id uuid NOT NULL,
    source_id uuid NOT NULL,
    severity_level_id uuid NOT NULL,
    method text NOT NULL,
    score numeric(5,2) CHECK (score BETWEEN 0 AND 10),
    vector text,
    cvss_version text,
    assessed_at timestamptz NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,source_id,method,vector),
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,id),
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT,
    FOREIGN KEY (source_id) REFERENCES vulnerability_sources (id) ON DELETE RESTRICT,
    FOREIGN KEY (severity_level_id) REFERENCES severity_levels (id) ON DELETE RESTRICT
);

CREATE INDEX ix_vulnerability_severities_fk1 ON vulnerability_severities (source_id);

CREATE INDEX ix_vulnerability_severities_fk2 ON vulnerability_severities (severity_level_id);

CREATE TABLE vulnerability_affected_packages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_id uuid NOT NULL,
    package_ecosystem_id uuid NOT NULL,
    namespace text NOT NULL DEFAULT '',
    normalized_name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,package_ecosystem_id,namespace,normalized_name),
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT,
    FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems (id) ON DELETE RESTRICT
);

CREATE INDEX ix_vulnerability_affected_packages_fk1 ON vulnerability_affected_packages (package_ecosystem_id);

CREATE TABLE vulnerability_affected_ranges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    affected_package_id uuid NOT NULL,
    range_type text NOT NULL CHECK (range_type IN ('SEMVER','ECOSYSTEM','GIT')),
    repository_url text,
    FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages (id) ON DELETE RESTRICT
);

CREATE INDEX ix_vulnerability_affected_ranges_fk0 ON vulnerability_affected_ranges (affected_package_id);

CREATE TABLE vulnerability_range_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    affected_range_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal>=0),
    event_type text NOT NULL CHECK (event_type IN ('INTRODUCED','FIXED','LAST_AFFECTED','LIMIT')),
    version text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (affected_range_id,ordinal),
    FOREIGN KEY (affected_range_id) REFERENCES vulnerability_affected_ranges (id) ON DELETE RESTRICT
);

CREATE TABLE vulnerability_fixed_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    affected_package_id uuid NOT NULL,
    version text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (affected_package_id,version),
    FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages (id) ON DELETE RESTRICT
);

CREATE TABLE vulnerability_source_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vulnerability_id uuid NOT NULL,
    source_id uuid NOT NULL,
    payload jsonb NOT NULL,
    sha256 text NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    fetched_at timestamptz NOT NULL,
    parser_version text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vulnerability_id,source_id,sha256),
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT,
    FOREIGN KEY (source_id) REFERENCES vulnerability_sources (id) ON DELETE RESTRICT
);

CREATE INDEX ix_vulnerability_source_records_fk1 ON vulnerability_source_records (source_id);

CREATE TABLE scan_vulnerability_findings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    scan_component_id uuid NOT NULL,
    vulnerability_id uuid NOT NULL,
    source_record_id uuid NOT NULL,
    severity_assessment_id uuid,
    match_status text NOT NULL CHECK (match_status IN ('MATCHED','UNCERTAIN','REJECTED')),
    resolution_status text NOT NULL DEFAULT 'OPEN' CHECK (resolution_status IN ('OPEN','DISMISSED','FIXED')),
    detected_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,scan_component_id,vulnerability_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT,
    FOREIGN KEY (vulnerability_id, severity_assessment_id) REFERENCES vulnerability_severities (vulnerability_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (source_record_id) REFERENCES vulnerability_source_records (id) ON DELETE RESTRICT
);

CREATE INDEX ix_scan_vulnerability_findings_fk2 ON scan_vulnerability_findings (vulnerability_id);

CREATE INDEX ix_scan_vulnerability_findings_fk3 ON scan_vulnerability_findings (vulnerability_id, severity_assessment_id);

CREATE INDEX ix_scan_vulnerability_findings_fk4 ON scan_vulnerability_findings (source_record_id);

ALTER TABLE vulnerability_source_records ADD UNIQUE (vulnerability_id,id);

ALTER TABLE scan_vulnerability_findings ADD FOREIGN KEY (vulnerability_id,source_record_id) REFERENCES vulnerability_source_records(vulnerability_id,id) ON DELETE RESTRICT;

CREATE TABLE finding_evidence (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    finding_id uuid NOT NULL,
    matched_range_id uuid,
    observed_version text NOT NULL,
    package_identifier text NOT NULL,
    method_version text NOT NULL,
    explanation text NOT NULL,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (matched_range_id) REFERENCES vulnerability_affected_ranges (id) ON DELETE RESTRICT
);

CREATE INDEX ix_finding_evidence_fk1 ON finding_evidence (organization_id, finding_id);

CREATE INDEX ix_finding_evidence_fk2 ON finding_evidence (matched_range_id);
