-- Immutable after application. See docs/database/README.md.

CREATE TABLE impact_analyses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    base_scan_id uuid NOT NULL,
    current_scan_component_id uuid,
    target_component_version_id uuid,
    operation text NOT NULL CHECK (operation IN ('ADD','UPDATE','REMOVE','REPLACE')),
    requested_by_membership_id uuid NOT NULL,
    method_version text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (organization_id, id),
    CHECK ((operation='ADD' AND current_scan_component_id IS NULL AND target_component_version_id IS NOT NULL) OR (operation='REMOVE' AND current_scan_component_id IS NOT NULL AND target_component_version_id IS NULL) OR (operation IN ('UPDATE','REPLACE') AND current_scan_component_id IS NOT NULL AND target_component_version_id IS NOT NULL)),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans (organization_id, repository_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, base_scan_id, current_scan_component_id) REFERENCES scan_components (organization_id, scan_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, target_component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_analyses_fk1 ON impact_analyses (organization_id, repository_id, base_scan_id);

CREATE INDEX ix_impact_analyses_fk2 ON impact_analyses (organization_id, base_scan_id, current_scan_component_id);

CREATE INDEX ix_impact_analyses_fk3 ON impact_analyses (organization_id, target_component_version_id);

CREATE INDEX ix_impact_analyses_fk4 ON impact_analyses (organization_id, requested_by_membership_id);

CREATE TABLE impact_findings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_analysis_id uuid NOT NULL,
    impact_type text NOT NULL,
    classification text NOT NULL CHECK (classification IN ('KNOWN','POTENTIAL','VALIDATED')),
    severity_level_id uuid,
    confidence numeric(6,5) CHECK (confidence BETWEEN 0 AND 1),
    summary text NOT NULL,
    review_status text NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING','ACCEPTED','REJECTED')),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (severity_level_id) REFERENCES severity_levels (id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_findings_fk1 ON impact_findings (organization_id, impact_analysis_id);

CREATE INDEX ix_impact_findings_fk2 ON impact_findings (severity_level_id);

CREATE TABLE impact_finding_repositories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,impact_finding_id,repository_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_finding_repositories_fk2 ON impact_finding_repositories (organization_id, repository_id);

CREATE TABLE impact_finding_components (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    component_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,impact_finding_id,component_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_finding_components_fk2 ON impact_finding_components (organization_id, component_id);

CREATE TABLE impact_finding_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,impact_finding_id,component_version_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_finding_versions_fk2 ON impact_finding_versions (organization_id, component_version_id);

CREATE TABLE impact_finding_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    document_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,impact_finding_id,document_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_finding_documents_fk2 ON impact_finding_documents (organization_id, document_id);

CREATE TABLE impact_finding_vulnerabilities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    vulnerability_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,impact_finding_id,vulnerability_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_finding_vulnerabilities_fk2 ON impact_finding_vulnerabilities (vulnerability_id);

CREATE TABLE impact_evidences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    evidence_type text NOT NULL,
    source_reference text NOT NULL,
    excerpt_redacted text,
    page_number integer CHECK (page_number>0),
    observed_at timestamptz NOT NULL,
    sha256 text CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_evidences_fk1 ON impact_evidences (organization_id, impact_finding_id);

CREATE TABLE impact_recommendations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    action text NOT NULL,
    priority integer NOT NULL CHECK (priority>=0),
    explanation text NOT NULL,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_recommendations_fk1 ON impact_recommendations (organization_id, impact_finding_id);

CREATE TABLE impact_recommendation_evidences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    recommendation_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,recommendation_id,evidence_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, recommendation_id) REFERENCES impact_recommendations (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_recommendation_evidences_fk2 ON impact_recommendation_evidences (organization_id, evidence_id);

CREATE TABLE impact_test_suggestions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    category text NOT NULL,
    description text NOT NULL,
    priority integer NOT NULL CHECK (priority>=0),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_impact_test_suggestions_fk1 ON impact_test_suggestions (organization_id, impact_finding_id);

CREATE TABLE version_change_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    component_id uuid NOT NULL,
    from_version_id uuid NOT NULL,
    to_version_id uuid NOT NULL,
    source_url text NOT NULL,
    retrieved_at timestamptz NOT NULL,
    UNIQUE (organization_id, id),
    CHECK (from_version_id<>to_version_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id, from_version_id) REFERENCES component_versions (organization_id, component_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id, to_version_id) REFERENCES component_versions (organization_id, component_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_version_change_records_fk1 ON version_change_records (organization_id, component_id);

CREATE INDEX ix_version_change_records_fk2 ON version_change_records (organization_id, component_id, from_version_id);

CREATE INDEX ix_version_change_records_fk3 ON version_change_records (organization_id, component_id, to_version_id);

CREATE TABLE change_record_evidences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    change_record_id uuid NOT NULL,
    excerpt_redacted text NOT NULL,
    source_reference text NOT NULL,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, change_record_id) REFERENCES version_change_records (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_change_record_evidences_fk1 ON change_record_evidences (organization_id, change_record_id);

CREATE TABLE document_review_flags (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    impact_finding_id uuid NOT NULL,
    reason text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','REVIEWED','DISMISSED')),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_id,impact_finding_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_review_flags_fk2 ON document_review_flags (organization_id, impact_finding_id);

CREATE TABLE report_schedules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    definition_id uuid NOT NULL,
    format_id uuid NOT NULL,
    cron_expression text NOT NULL,
    time_zone text NOT NULL,
    is_enabled boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (definition_id) REFERENCES report_definitions (id) ON DELETE RESTRICT,
    FOREIGN KEY (format_id) REFERENCES report_formats (id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_schedules_fk1 ON report_schedules (definition_id);

CREATE INDEX ix_report_schedules_fk2 ON report_schedules (format_id);

CREATE TABLE report_schedule_recipients (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    schedule_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,schedule_id,membership_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, schedule_id) REFERENCES report_schedules (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_report_schedule_recipients_fk2 ON report_schedule_recipients (organization_id, membership_id);
