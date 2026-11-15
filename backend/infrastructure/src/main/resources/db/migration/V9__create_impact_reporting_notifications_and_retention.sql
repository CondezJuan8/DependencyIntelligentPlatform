ALTER TABLE scans ADD CONSTRAINT uq_scan_commit_context UNIQUE (organization_id, repository_id, id, repository_commit_id);
CREATE TABLE impact_analyses (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, repository_id UUID NOT NULL,
 base_scan_id UUID NOT NULL, base_commit_id UUID, component_id UUID NOT NULL,
 current_version_id UUID, target_version_id UUID, replacement_version_id UUID,
 operation TEXT NOT NULL CHECK (operation IN ('ADD','UPDATE','REMOVE','REPLACE')),
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 method_version TEXT NOT NULL, requested_by_membership_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), UNIQUE (organization_id, repository_id, id, base_commit_id),
 CHECK ((operation = 'ADD' AND current_version_id IS NULL AND target_version_id IS NOT NULL AND replacement_version_id IS NULL)
     OR (operation = 'UPDATE' AND current_version_id IS NOT NULL AND target_version_id IS NOT NULL AND current_version_id <> target_version_id AND replacement_version_id IS NULL)
     OR (operation = 'REMOVE' AND current_version_id IS NOT NULL AND target_version_id IS NULL AND replacement_version_id IS NULL)
     OR (operation = 'REPLACE' AND current_version_id IS NOT NULL AND target_version_id IS NULL AND replacement_version_id IS NOT NULL AND replacement_version_id <> current_version_id)),
 FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id, base_scan_id, base_commit_id) REFERENCES scans(organization_id, repository_id, id, repository_commit_id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_id, current_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_id, target_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, replacement_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE change_simulations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, repository_id UUID NOT NULL, impact_analysis_id UUID NOT NULL,
 base_commit_id UUID NOT NULL, background_job_id UUID, requested_by_membership_id UUID,
 environment_reference TEXT NOT NULL, configuration_version TEXT NOT NULL, timeout_seconds INTEGER NOT NULL CHECK (timeout_seconds > 0),
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, repository_id, impact_analysis_id, base_commit_id) REFERENCES impact_analyses(organization_id, repository_id, id, base_commit_id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE simulation_test_runs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, simulation_id UUID NOT NULL,
 tool_name TEXT NOT NULL, tool_version TEXT, command_summary TEXT NOT NULL,
 status TEXT NOT NULL REFERENCES job_statuses(code) ON DELETE RESTRICT, exit_code INTEGER,
 started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, sanitized_summary TEXT,
 UNIQUE (organization_id, id), CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
 FOREIGN KEY (organization_id, simulation_id) REFERENCES change_simulations(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE change_pull_request_proposals (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, simulation_id UUID NOT NULL,
 title TEXT NOT NULL, description TEXT NOT NULL, proposed_patch_storage_key TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','PUBLISHED','REJECTED')),
 approved_by_membership_id UUID, approved_at TIMESTAMPTZ, published_at TIMESTAMPTZ, external_url TEXT, external_number BIGINT,
 UNIQUE (organization_id, id), CHECK (external_number IS NULL OR external_number > 0),
 CHECK (status NOT IN ('APPROVED','PUBLISHED') OR (approved_by_membership_id IS NOT NULL AND approved_at IS NOT NULL)),
 CHECK (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND external_url IS NOT NULL AND external_number IS NOT NULL)),
 FOREIGN KEY (organization_id, simulation_id) REFERENCES change_simulations(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, approved_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_findings (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, impact_analysis_id UUID NOT NULL,
 classification TEXT NOT NULL CHECK (classification IN ('KNOWN','POTENTIAL','VALIDATED')),
 summary TEXT NOT NULL, severity_code TEXT REFERENCES severity_levels(code) ON DELETE RESTRICT,
 review_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING','ACCEPTED','REJECTED')),
 validation_test_run_id UUID, UNIQUE (organization_id, id),
 CHECK ((classification = 'VALIDATED') = (validation_test_run_id IS NOT NULL)),
 FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, validation_test_run_id) REFERENCES simulation_test_runs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_finding_repositories (
 organization_id UUID NOT NULL, finding_id UUID NOT NULL, repository_id UUID NOT NULL,
 PRIMARY KEY (organization_id, finding_id, repository_id),
 FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_finding_component_versions (
 organization_id UUID NOT NULL, finding_id UUID NOT NULL, component_version_id UUID NOT NULL,
 PRIMARY KEY (organization_id, finding_id, component_version_id),
 FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_finding_documents (
 organization_id UUID NOT NULL, finding_id UUID NOT NULL, document_id UUID NOT NULL,
 PRIMARY KEY (organization_id, finding_id, document_id),
 FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_finding_vulnerabilities (
 organization_id UUID NOT NULL, finding_id UUID NOT NULL, vulnerability_id UUID NOT NULL REFERENCES vulnerabilities(id) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, finding_id, vulnerability_id),
 FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_evidences (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, impact_analysis_id UUID NOT NULL,
 evidence_type TEXT NOT NULL, source_url TEXT, excerpt TEXT NOT NULL, page_number INTEGER CHECK (page_number > 0),
 observed_at TIMESTAMPTZ NOT NULL, content_hash TEXT NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
 UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_finding_evidences (
 organization_id UUID NOT NULL, finding_id UUID NOT NULL, evidence_id UUID NOT NULL,
 PRIMARY KEY (organization_id, finding_id, evidence_id),
 FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_recommendations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, impact_analysis_id UUID NOT NULL,
 action TEXT NOT NULL, priority INTEGER NOT NULL CHECK (priority > 0), explanation TEXT NOT NULL,
 UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_recommendation_evidences (
 organization_id UUID NOT NULL, recommendation_id UUID NOT NULL, evidence_id UUID NOT NULL,
 PRIMARY KEY (organization_id, recommendation_id, evidence_id),
 FOREIGN KEY (organization_id, recommendation_id) REFERENCES impact_recommendations(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE impact_test_suggestions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, impact_analysis_id UUID NOT NULL,
 category TEXT NOT NULL, priority INTEGER NOT NULL CHECK (priority > 0), description TEXT NOT NULL,
 FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE version_change_records (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, component_version_id UUID NOT NULL,
 source_url TEXT NOT NULL, published_at TIMESTAMPTZ, fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 content TEXT NOT NULL, UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE change_record_evidences (
 organization_id UUID NOT NULL, change_record_id UUID NOT NULL, evidence_id UUID NOT NULL,
 PRIMARY KEY (organization_id, change_record_id, evidence_id),
 FOREIGN KEY (organization_id, change_record_id) REFERENCES version_change_records(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_review_flags (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, document_id UUID NOT NULL, impact_finding_id UUID NOT NULL,
 reason TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','REVIEWED','DISMISSED')),
 UNIQUE (organization_id, document_id, impact_finding_id),
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE report_types (code TEXT PRIMARY KEY);
INSERT INTO report_types VALUES ('EXECUTIVE'),('DEPENDENCY'),('VULNERABILITY'),('IMPACT'),('REPOSITORY'),('CHANGE'),('SBOM');
CREATE TABLE report_formats (code TEXT PRIMARY KEY);
INSERT INTO report_formats VALUES ('PDF'),('CSV'),('JSON'),('CYCLONEDX'),('SPDX');
CREATE TABLE report_definitions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE,
 report_type TEXT NOT NULL REFERENCES report_types(code) ON DELETE RESTRICT, name TEXT NOT NULL
);
INSERT INTO report_definitions(code, report_type, name) SELECT code, code, code FROM report_types;
CREATE TABLE report_jobs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, definition_id UUID NOT NULL REFERENCES report_definitions(id) ON DELETE RESTRICT,
 format TEXT NOT NULL REFERENCES report_formats(code) ON DELETE RESTRICT, requested_by_membership_id UUID, background_job_id UUID,
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 execution_parameters JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(execution_parameters) = 'object'),
 requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE report_repositories (
 organization_id UUID NOT NULL, report_job_id UUID NOT NULL, repository_id UUID NOT NULL,
 PRIMARY KEY (organization_id, report_job_id, repository_id),
 FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE report_scans (
 organization_id UUID NOT NULL, report_job_id UUID NOT NULL, scan_id UUID NOT NULL,
 PRIMARY KEY (organization_id, report_job_id, scan_id),
 FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE report_artifacts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, report_job_id UUID NOT NULL,
 storage_key TEXT NOT NULL, sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'), size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
 media_type TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ,
 UNIQUE (organization_id, id), FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE report_schedules (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 definition_id UUID NOT NULL REFERENCES report_definitions(id) ON DELETE RESTRICT, format TEXT NOT NULL REFERENCES report_formats(code) ON DELETE RESTRICT,
 cron_expression TEXT NOT NULL, time_zone TEXT NOT NULL, is_enabled BOOLEAN NOT NULL DEFAULT false, UNIQUE (organization_id, id)
);
CREATE TABLE report_schedule_recipients (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, schedule_id UUID NOT NULL,
 membership_id UUID, external_email TEXT, CHECK (num_nonnulls(membership_id, external_email) = 1),
 UNIQUE NULLS NOT DISTINCT (organization_id, schedule_id, membership_id, external_email),
 FOREIGN KEY (organization_id, schedule_id) REFERENCES report_schedules(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE notification_channels (code TEXT PRIMARY KEY);
INSERT INTO notification_channels VALUES ('IN_APP'),('EMAIL'),('WEBHOOK');
CREATE TABLE notification_rules (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 event_type TEXT NOT NULL CHECK (event_type IN ('NEW_VULNERABILITY','SEVERITY_CHANGED','COMPONENT_ADDED','COMPONENT_REMOVED','COMPONENT_UPDATED','SCAN_FAILED')),
 name TEXT NOT NULL, is_enabled BOOLEAN NOT NULL DEFAULT true, UNIQUE (organization_id, id), UNIQUE (organization_id, name)
);
CREATE TABLE notification_subscriptions (
 organization_id UUID NOT NULL, rule_id UUID NOT NULL, membership_id UUID NOT NULL,
 channel TEXT NOT NULL REFERENCES notification_channels(code) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, rule_id, membership_id, channel),
 FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE notifications (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 rule_id UUID, event_type TEXT NOT NULL, deduplication_key TEXT NOT NULL,
 title TEXT NOT NULL, summary TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 scan_id UUID, finding_id UUID, comparison_id UUID,
 UNIQUE (organization_id, id), UNIQUE (organization_id, event_type, deduplication_key),
 FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE notification_deliveries (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, notification_id UUID NOT NULL, membership_id UUID NOT NULL,
 channel TEXT NOT NULL REFERENCES notification_channels(code) ON DELETE RESTRICT,
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DELIVERED','FAILED','READ')),
 delivered_at TIMESTAMPTZ, read_at TIMESTAMPTZ, UNIQUE (organization_id, id), UNIQUE (organization_id, notification_id, membership_id, channel),
 CHECK (read_at IS NULL OR (channel = 'IN_APP' AND delivered_at IS NOT NULL AND read_at >= delivered_at)),
 FOREIGN KEY (organization_id, notification_id) REFERENCES notifications(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE notification_delivery_attempts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, delivery_id UUID NOT NULL,
 attempt_number INTEGER NOT NULL CHECK (attempt_number > 0), attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 status TEXT NOT NULL CHECK (status IN ('DELIVERED','FAILED')), error_code TEXT, provider_reference TEXT,
 UNIQUE (organization_id, delivery_id, attempt_number),
 FOREIGN KEY (organization_id, delivery_id) REFERENCES notification_deliveries(organization_id, id) ON DELETE RESTRICT
);

ALTER TABLE audit_events ADD CONSTRAINT uq_audit_event_id UNIQUE (organization_id, id);
ALTER TABLE audit_events ADD COLUMN result TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (result IN ('SUCCESS','FAILURE','UNKNOWN'));
ALTER TABLE audit_events ADD COLUMN correlation_id UUID;
CREATE TABLE audit_event_changes (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, audit_event_id UUID NOT NULL,
 attribute TEXT NOT NULL CHECK (lower(attribute) !~ '(password|token|secret|private_key)'),
 before_value JSONB, after_value JSONB,
 FOREIGN KEY (organization_id, audit_event_id) REFERENCES audit_events(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE data_retention_policies (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 resource_type TEXT NOT NULL CHECK (resource_type IN ('DOCUMENT_ORIGINAL','DOCUMENT_TEMP','SBOM','SCAN_LOG','AUDIT','ADVISORY_PAYLOAD','ASSISTANT_MESSAGE','REPORT')),
 action TEXT NOT NULL CHECK (action IN ('KEEP','DELETE','ANONYMIZE','ARCHIVE')), retention_days INTEGER,
 is_enabled BOOLEAN NOT NULL DEFAULT true, UNIQUE (organization_id, id), UNIQUE (organization_id, resource_type),
 CHECK ((action = 'KEEP' AND retention_days IS NULL) OR (action <> 'KEEP' AND retention_days IS NOT NULL AND retention_days > 0))
);
ALTER TABLE documents ADD COLUMN retention_policy_id UUID;
ALTER TABLE documents ADD CONSTRAINT fk_document_retention FOREIGN KEY (organization_id, retention_policy_id) REFERENCES data_retention_policies(organization_id, id) ON DELETE RESTRICT;
CREATE TABLE data_deletion_requests (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, requested_by_membership_id UUID,
 document_id UUID, integration_id UUID, scan_id UUID, report_job_id UUID, conversation_id UUID,
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMPTZ,
 CHECK (num_nonnulls(document_id, integration_id, scan_id, report_job_id, conversation_id) = 1),
 FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations(organization_id, id) ON DELETE RESTRICT
);
