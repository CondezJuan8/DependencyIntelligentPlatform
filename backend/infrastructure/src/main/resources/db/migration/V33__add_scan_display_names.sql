ALTER TABLE scans ADD COLUMN display_name VARCHAR(100);

ALTER TABLE scans ADD CONSTRAINT ck_scan_display_name_nonblank
    CHECK (display_name IS NULL OR (length(btrim(display_name)) BETWEEN 1 AND 100));
