-- Local stable state machines. RETRYING returns a failed job to the queue.
CREATE FUNCTION app.validate_process_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status <> OLD.status AND NOT (
        (OLD.status='PENDING' AND NEW.status IN ('RUNNING','CANCELLED')) OR
        (OLD.status='RUNNING' AND NEW.status IN ('COMPLETED','FAILED','CANCELLED')) OR
        (OLD.status='FAILED' AND NEW.status='RETRYING') OR
        (OLD.status='RETRYING' AND NEW.status IN ('RUNNING','CANCELLED'))
    ) THEN
        RAISE EXCEPTION 'Invalid process transition: % -> %', OLD.status, NEW.status USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['background_jobs','background_job_attempts','integration_sync_runs',
        'scans','scan_stage_executions','report_jobs','data_deletion_requests'] LOOP
        EXECUTE format('CREATE TRIGGER process_transition BEFORE UPDATE OF status ON %I FOR EACH ROW EXECUTE FUNCTION app.validate_process_transition()',t);
        EXECUTE format('ALTER TABLE %I ADD CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at>=started_at))',t);
    END LOOP;
END $$;
-- The provider range used as evidence must belong to the finding's advisory.
CREATE FUNCTION app.validate_finding_range() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.matched_range_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM scan_vulnerability_findings f
        JOIN vulnerability_affected_packages p ON p.vulnerability_id=f.vulnerability_id
        JOIN vulnerability_affected_ranges r ON r.affected_package_id=p.id
        WHERE f.organization_id=NEW.organization_id AND f.id=NEW.finding_id AND r.id=NEW.matched_range_id
    ) THEN RAISE EXCEPTION 'Evidence range belongs to another advisory' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER finding_range_consistency BEFORE INSERT OR UPDATE ON finding_evidence
FOR EACH ROW EXECUTE FUNCTION app.validate_finding_range();
CREATE INDEX ix_repository_team_role ON repository_team_access(role_id);
