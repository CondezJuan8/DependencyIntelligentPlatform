REVOKE UPDATE, DELETE ON scan_vulnerability_assessments FROM dependency_runtime;

COMMENT ON TABLE scan_vulnerability_assessments IS
    'vulnerability: Append-only per-scan-component OSV assessment with tenant-scoped canonical JSON evidence.';
