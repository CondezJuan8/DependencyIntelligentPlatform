-- A local upload has no Git reference. Preserve its archive digest instead of inventing a commit.
ALTER TABLE scans DROP CONSTRAINT scans_origin_check;
ALTER TABLE scans ADD CONSTRAINT scans_origin_check
  CHECK (origin IN ('REPOSITORY_SCAN', 'LOCAL_UPLOAD_SCAN', 'SBOM_IMPORT'));

ALTER TABLE scans ADD COLUMN source_sha256 VARCHAR(64)
  CHECK (source_sha256 ~ '^[a-f0-9]{64}$');
ALTER TABLE scans ADD COLUMN manifest_count INTEGER CHECK (manifest_count >= 0);
ALTER TABLE scans ADD COLUMN unresolved_count INTEGER CHECK (unresolved_count >= 0);

ALTER TABLE scans DROP CONSTRAINT ck_scan_origin_reference;
ALTER TABLE scans ADD CONSTRAINT ck_scan_origin_reference CHECK (
  (origin = 'REPOSITORY_SCAN' AND git_reference IS NOT NULL)
  OR (origin = 'LOCAL_UPLOAD_SCAN' AND git_reference IS NULL
      AND repository_commit_id IS NULL AND source_sha256 IS NOT NULL)
  OR (origin = 'SBOM_IMPORT' AND git_reference IS NULL AND repository_commit_id IS NULL
      AND scanner_name IS NULL AND scanner_version IS NULL AND status = 'COMPLETED'
      AND trigger_type = 'IMPORT')
);

INSERT INTO scanner_tools(code, name) VALUES ('maven-manifest', 'Maven POM manifest reader')
  ON CONFLICT (code) DO NOTHING;
