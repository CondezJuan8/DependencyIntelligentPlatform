ALTER TABLE scans
    ADD COLUMN coverage_status TEXT NOT NULL DEFAULT 'COMPLETE'
        CHECK (coverage_status IN ('COMPLETE', 'PARTIAL')),
    ADD COLUMN coverage_message TEXT;

INSERT INTO scanner_tools(code, name)
VALUES ('cdxgen-maven', 'CycloneDX Generator 13.8.0 · Maven'),
       ('cdxgen-npm', 'CycloneDX Generator 13.8.0 · npm')
ON CONFLICT (code) DO NOTHING;

UPDATE scans
SET coverage_status = 'PARTIAL',
    coverage_message = 'El análisis histórico Maven solo podía leer declaraciones directas; no garantizaba dependencias transitivas.'
WHERE scanner_name = 'maven-manifest' AND status = 'COMPLETED';
