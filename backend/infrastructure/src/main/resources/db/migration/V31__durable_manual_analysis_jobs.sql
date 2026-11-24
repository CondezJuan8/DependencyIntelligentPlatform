CREATE UNIQUE INDEX uq_active_scan_job_repository
    ON background_jobs (organization_id, (payload ->> 'repositoryId'))
    WHERE job_type = 'SCAN' AND status IN ('PENDING', 'RUNNING', 'RETRYING');

CREATE UNIQUE INDEX uq_scan_job_scanner
    ON scans (organization_id, background_job_id, scanner_name)
    WHERE background_job_id IS NOT NULL AND scanner_name IS NOT NULL;

CREATE OR REPLACE FUNCTION app_private.claim_next_scan_analysis_job(worker_name TEXT)
RETURNS TABLE (organization_id UUID, job_id UUID, repository_id UUID, attempt_number INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, app_private
AS $$
DECLARE
    candidate RECORD;
    next_attempt INTEGER;
BEGIN
    IF worker_name IS NULL OR btrim(worker_name) = '' OR length(worker_name) > 128 THEN
        RAISE EXCEPTION 'Worker name is required';
    END IF;

    UPDATE public.background_job_attempts attempt
       SET status = 'FAILED', finished_at = clock_timestamp(), error_code = 'WORKER_LEASE_EXPIRED',
           error_message = 'Worker lease expired before the analysis job finished'
      FROM public.background_jobs job
     WHERE attempt.organization_id = job.organization_id AND attempt.job_id = job.id
       AND attempt.status = 'RUNNING' AND job.job_type = 'SCAN' AND job.status = 'RUNNING'
       AND job.locked_at < clock_timestamp() - INTERVAL '30 minutes';

    UPDATE public.background_jobs job
       SET status = CASE
               WHEN (SELECT count(*) FROM public.background_job_attempts attempt
                      WHERE attempt.organization_id = job.organization_id AND attempt.job_id = job.id) >= job.max_attempts
               THEN 'FAILED' ELSE 'RETRYING' END,
           last_error_code = 'WORKER_LEASE_EXPIRED', locked_at = NULL, locked_by = NULL,
           available_at = clock_timestamp()
     WHERE job.job_type = 'SCAN' AND job.status = 'RUNNING'
       AND job.locked_at < clock_timestamp() - INTERVAL '30 minutes';

    SELECT job.organization_id, job.id, job.payload
      INTO candidate
      FROM public.background_jobs job
     WHERE job.job_type = 'SCAN' AND job.status IN ('PENDING', 'RETRYING')
       AND job.available_at <= clock_timestamp()
       AND (SELECT count(*) FROM public.background_job_attempts attempt
             WHERE attempt.organization_id = job.organization_id AND attempt.job_id = job.id) < job.max_attempts
     ORDER BY job.available_at, job.created_at, job.id
     LIMIT 1
     FOR UPDATE SKIP LOCKED;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*)::INTEGER + 1 INTO next_attempt
      FROM public.background_job_attempts attempt
     WHERE attempt.organization_id = candidate.organization_id AND attempt.job_id = candidate.id;

    UPDATE public.background_jobs job
       SET status = 'RUNNING', locked_at = clock_timestamp(), locked_by = worker_name
     WHERE job.organization_id = candidate.organization_id AND job.id = candidate.id;

    INSERT INTO public.background_job_attempts
        (organization_id, job_id, attempt_number, worker, status)
    VALUES (candidate.organization_id, candidate.id, next_attempt, worker_name, 'RUNNING');

    RETURN QUERY SELECT candidate.organization_id, candidate.id,
                        (candidate.payload ->> 'repositoryId')::UUID, next_attempt;
END;
$$;

ALTER FUNCTION app_private.claim_next_scan_analysis_job(TEXT) OWNER TO dependency_migrator;
REVOKE ALL ON FUNCTION app_private.claim_next_scan_analysis_job(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_private.claim_next_scan_analysis_job(TEXT) TO dependency_runtime;
