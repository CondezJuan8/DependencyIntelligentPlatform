-- The SECURITY DEFINER scan-claim function runs as dependency_migrator.
-- Give it only the queue operations it performs; tenant RLS remains enforced
-- through the migration role's scoped maintenance policy.
GRANT SELECT, UPDATE ON public.background_jobs TO dependency_migrator;
GRANT SELECT, INSERT, UPDATE ON public.background_job_attempts TO dependency_migrator;
