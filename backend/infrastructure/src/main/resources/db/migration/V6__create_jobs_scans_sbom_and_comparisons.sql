CREATE TABLE job_types (code TEXT PRIMARY KEY, description TEXT NOT NULL);
INSERT INTO job_types VALUES ('SCAN','Analyze repository'), ('DOCUMENT_PROCESS','Process document'), ('EMBED','Embed chunks'),
 ('REPORT','Generate report'), ('NOTIFY','Deliver notification'), ('IMPACT','Analyze change impact'), ('SIMULATE','Run isolated change test'), ('SYNC','Synchronize integration');
CREATE TABLE job_statuses (code TEXT PRIMARY KEY);
INSERT INTO job_statuses VALUES ('PENDING'),('RUNNING'),('COMPLETED'),('FAILED'),('CANCELLED'),('RETRYING');
CREATE TABLE scanner_tools (code TEXT PRIMARY KEY CHECK (btrim(code) <> ''), name TEXT NOT NULL);
INSERT INTO scanner_tools VALUES ('cdxgen','cdxgen'), ('osv-scanner','OSV-Scanner');
INSERT INTO scanner_tools SELECT DISTINCT scanner_name, scanner_name FROM scans WHERE scanner_name IS NOT NULL ON CONFLICT DO NOTHING;
ALTER TABLE scans ADD CONSTRAINT fk_scan_tool FOREIGN KEY (scanner_name) REFERENCES scanner_tools(code) ON DELETE RESTRICT;

CREATE TABLE background_jobs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 job_type TEXT NOT NULL REFERENCES job_types(code) ON DELETE RESTRICT,
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 payload JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(payload) = 'object'), deduplication_key TEXT,
 available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, locked_at TIMESTAMPTZ, locked_by TEXT,
 max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 100), last_error_code TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), CHECK ((locked_at IS NULL) = (locked_by IS NULL))
);
CREATE UNIQUE INDEX uq_job_deduplication ON background_jobs(organization_id, job_type, deduplication_key) WHERE deduplication_key IS NOT NULL;
CREATE INDEX ix_job_queue ON background_jobs(organization_id, available_at) WHERE status IN ('PENDING','RETRYING');
CREATE TABLE background_job_attempts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, job_id UUID NOT NULL,
 attempt_number INTEGER NOT NULL CHECK (attempt_number > 0), worker TEXT NOT NULL,
 started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMPTZ,
 status TEXT NOT NULL REFERENCES job_statuses(code) ON DELETE RESTRICT, error_code TEXT, error_message TEXT,
 UNIQUE (organization_id, id), UNIQUE (organization_id, job_id, attempt_number), CHECK (finished_at IS NULL OR finished_at >= started_at),
 FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE background_job_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, job_id UUID NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, event_type TEXT NOT NULL, sanitized_metadata JSONB NOT NULL DEFAULT '{}',
 FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_job_events ON background_job_events(organization_id, job_id, occurred_at);
CREATE TABLE scan_profiles (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 name TEXT NOT NULL CHECK (btrim(name) <> ''), max_duration_seconds INTEGER NOT NULL CHECK (max_duration_seconds > 0),
 UNIQUE (organization_id, id), UNIQUE (organization_id, name)
);
CREATE TABLE scan_profile_ecosystems (
 organization_id UUID NOT NULL, scan_profile_id UUID NOT NULL, package_ecosystem_id UUID NOT NULL REFERENCES package_ecosystems(id) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, scan_profile_id, package_ecosystem_id),
 FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT
);
ALTER TABLE scans ADD CONSTRAINT uq_scan_repository_id UNIQUE (organization_id, repository_id, id);
ALTER TABLE scans ADD COLUMN scan_profile_id UUID;
ALTER TABLE scans ADD COLUMN background_job_id UUID;
ALTER TABLE scans ADD COLUMN requested_by_membership_id UUID;
ALTER TABLE scans ADD COLUMN trigger_type TEXT NOT NULL DEFAULT 'MANUAL' CHECK (trigger_type IN ('MANUAL','WEBHOOK','SCHEDULED','IMPORT'));
UPDATE scans SET trigger_type = 'IMPORT' WHERE origin = 'SBOM_IMPORT';
ALTER TABLE scans ADD CONSTRAINT fk_scan_profile FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT;
ALTER TABLE scans ADD CONSTRAINT fk_scan_job FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT;
ALTER TABLE scans ADD CONSTRAINT fk_scan_requester FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT;
-- Valid observed commit strings become repository-scoped commit identities. Invalid legacy values stop migration instead of being discarded.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM scans WHERE commit_sha IS NOT NULL AND commit_sha !~ '^[a-fA-F0-9]{40}([a-fA-F0-9]{24})?$') THEN
  RAISE EXCEPTION 'Invalid legacy scan commit SHA; correct the source data before migrating (no values were discarded)';
 END IF;
END $$;
INSERT INTO repository_commits(organization_id, repository_id, commit_sha)
 SELECT DISTINCT organization_id, repository_id, commit_sha FROM scans WHERE commit_sha IS NOT NULL ON CONFLICT DO NOTHING;
ALTER TABLE scans ADD COLUMN repository_commit_id UUID;
UPDATE scans scan SET repository_commit_id = commit.id FROM repository_commits commit
 WHERE commit.organization_id = scan.organization_id AND commit.repository_id = scan.repository_id AND commit.commit_sha = scan.commit_sha;
ALTER TABLE scans DROP CONSTRAINT ck_scan_origin_reference;
ALTER TABLE scans DROP COLUMN commit_sha;
ALTER TABLE scans ADD CONSTRAINT fk_scan_commit FOREIGN KEY (organization_id, repository_id, repository_commit_id)
 REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT;
ALTER TABLE scans ADD CONSTRAINT ck_scan_origin_reference CHECK (
 (origin = 'REPOSITORY_SCAN' AND git_reference IS NOT NULL) OR
 (origin = 'SBOM_IMPORT' AND git_reference IS NULL AND repository_commit_id IS NULL AND scanner_name IS NULL AND scanner_version IS NULL AND status = 'COMPLETED' AND trigger_type = 'IMPORT')
);
CREATE TABLE scan_stages (code TEXT PRIMARY KEY, sequence_number INTEGER NOT NULL UNIQUE CHECK (sequence_number > 0));
INSERT INTO scan_stages VALUES ('FETCH',1),('GENERATE_SBOM',2),('PARSE',3),('NORMALIZE',4),('VULNERABILITY_LOOKUP',5),('FINALIZE',6);
CREATE TABLE scan_stage_executions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, scan_id UUID NOT NULL,
 stage_code TEXT NOT NULL REFERENCES scan_stages(code) ON DELETE RESTRICT,
 attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0), status TEXT NOT NULL REFERENCES job_statuses(code) ON DELETE RESTRICT,
 started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, error_code TEXT,
 UNIQUE (organization_id, id), UNIQUE (organization_id, scan_id, stage_code, attempt_number),
 CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
 FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE scan_logs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, scan_id UUID NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, level TEXT NOT NULL CHECK (level IN ('DEBUG','INFO','WARN','ERROR')),
 message TEXT NOT NULL, sanitized_metadata JSONB NOT NULL DEFAULT '{}',
 FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_scan_logs_history ON scan_logs(organization_id, scan_id, occurred_at);
ALTER TABLE scan_artifacts ADD CONSTRAINT uq_scan_artifact_id UNIQUE (organization_id, id);
ALTER TABLE scan_artifacts ADD CONSTRAINT uq_scan_artifact_snapshot UNIQUE (organization_id, scan_id, id);
ALTER TABLE scan_artifacts ADD COLUMN size_bytes BIGINT CHECK (size_bytes >= 0);
ALTER TABLE scan_artifacts ADD COLUMN media_type TEXT;
ALTER TABLE scan_artifacts ADD COLUMN expires_at TIMESTAMPTZ;
ALTER TABLE sbom_imports RENAME TO sbom_documents;
ALTER TABLE sbom_documents ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE sbom_documents ADD CONSTRAINT uq_sbom_document_id UNIQUE (organization_id, id);
ALTER TABLE sbom_documents ADD COLUMN artifact_id UUID;
ALTER TABLE sbom_documents ADD COLUMN generated_at TIMESTAMPTZ;
ALTER TABLE sbom_documents ADD COLUMN document_version INTEGER NOT NULL DEFAULT 1 CHECK (document_version > 0);
ALTER TABLE sbom_documents ADD CONSTRAINT fk_sbom_artifact FOREIGN KEY (organization_id, scan_id, artifact_id)
 REFERENCES scan_artifacts(organization_id, scan_id, id) ON DELETE RESTRICT;
CREATE TABLE sbom_external_references (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, sbom_document_id UUID NOT NULL,
 reference_type TEXT NOT NULL, url TEXT NOT NULL, UNIQUE (organization_id, sbom_document_id, reference_type, url),
 FOREIGN KEY (organization_id, sbom_document_id) REFERENCES sbom_documents(organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE scan_comparisons (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, repository_id UUID NOT NULL,
 base_scan_id UUID NOT NULL, target_scan_id UUID NOT NULL, rules_version TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), UNIQUE (organization_id, base_scan_id, target_scan_id, rules_version),
 CHECK (base_scan_id <> target_scan_id),
 FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id, target_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT
);
ALTER TABLE component_versions ADD CONSTRAINT uq_component_version_package UNIQUE (organization_id, component_id, id);
CREATE TABLE scan_component_changes (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, comparison_id UUID NOT NULL,
 component_id UUID NOT NULL, before_version_id UUID, after_version_id UUID,
 change_type TEXT NOT NULL CHECK (change_type IN ('ADDED','REMOVED','UPDATED','UNCHANGED')),
 UNIQUE (organization_id, comparison_id, component_id),
 CHECK ((change_type = 'ADDED' AND before_version_id IS NULL AND after_version_id IS NOT NULL)
     OR (change_type = 'REMOVED' AND before_version_id IS NOT NULL AND after_version_id IS NULL)
     OR (change_type = 'UPDATED' AND before_version_id IS NOT NULL AND after_version_id IS NOT NULL AND before_version_id <> after_version_id)
     OR (change_type = 'UNCHANGED' AND before_version_id IS NOT NULL AND after_version_id IS NOT NULL AND before_version_id = after_version_id)),
 FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_id, before_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_id, after_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT
);
CREATE TABLE license_catalog (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, url TEXT);
CREATE TABLE component_hashes (
 organization_id UUID NOT NULL, component_version_id UUID NOT NULL,
 algorithm TEXT NOT NULL CHECK (algorithm IN ('MD5','SHA-1','SHA-256','SHA-512')),
 digest TEXT NOT NULL CHECK (digest ~ '^[a-f0-9]+$'), PRIMARY KEY (organization_id, component_version_id, algorithm, digest),
 CHECK (length(digest) = CASE algorithm WHEN 'MD5' THEN 32 WHEN 'SHA-1' THEN 40 WHEN 'SHA-256' THEN 64 WHEN 'SHA-512' THEN 128 END),
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE component_licenses (
 organization_id UUID NOT NULL, component_version_id UUID NOT NULL,
 license_id UUID NOT NULL REFERENCES license_catalog(id) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, component_version_id, license_id),
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE scan_component_licenses (
 organization_id UUID NOT NULL, scan_component_id UUID NOT NULL,
 license_id UUID NOT NULL REFERENCES license_catalog(id) ON DELETE RESTRICT,
 PRIMARY KEY (organization_id, scan_component_id, license_id),
 FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE component_external_references (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, component_version_id UUID NOT NULL,
 reference_type TEXT NOT NULL, url TEXT NOT NULL, UNIQUE (organization_id, component_version_id, reference_type, url),
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
