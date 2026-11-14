-- Namespace is kept separately from artifact name. Existing catalog rows remain untouched.
ALTER TABLE components ADD COLUMN namespace VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE scans ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'REPOSITORY_SCAN'
    CHECK (origin IN ('REPOSITORY_SCAN', 'SBOM_IMPORT'));
ALTER TABLE scans ALTER COLUMN git_reference DROP NOT NULL;
ALTER TABLE scans ADD CONSTRAINT ck_scan_origin_reference CHECK (
    (origin = 'REPOSITORY_SCAN' AND git_reference IS NOT NULL) OR
    (origin = 'SBOM_IMPORT' AND git_reference IS NULL AND commit_sha IS NULL
        AND scanner_name IS NULL AND scanner_version IS NULL AND status = 'COMPLETED')
);

-- Provenance of bytes imported, not an assertion that an original file was retained.
CREATE TABLE sbom_imports (
    organization_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    format VARCHAR(40) NOT NULL CHECK (btrim(format) <> ''),
    specification_version VARCHAR(20) NOT NULL CHECK (btrim(specification_version) <> ''),
    serial_number VARCHAR(64),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (organization_id, scan_id),
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id)
);
