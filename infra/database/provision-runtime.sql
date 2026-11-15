-- Invoke as the migration/admin login. No password is committed or printed by this script.
\set ON_ERROR_STOP on
\getenv runtime_password POSTGRES_APP_PASSWORD
\if :{?runtime_password}
\else
\echo POSTGRES_APP_PASSWORD must be set in the environment
\quit 1
\endif
SELECT length(:'runtime_password') >= 12 AS password_configured \gset
\if :password_configured
\else
\echo POSTGRES_APP_PASSWORD must contain at least 12 characters
\quit 1
\endif
BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dependency_app' AND
     (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication)) THEN
  RAISE EXCEPTION 'Existing dependency_app login has unsafe privileges; review it before provisioning';
 END IF;
END $$;
SELECT format('CREATE ROLE dependency_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', :'runtime_password')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dependency_app') \gexec
SELECT format('ALTER ROLE dependency_app PASSWORD %L', :'runtime_password') \gexec
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dependency_runtime') THEN
  GRANT dependency_runtime TO dependency_app;
 END IF;
END $$;
COMMIT;
