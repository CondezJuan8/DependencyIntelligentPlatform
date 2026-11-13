\set ON_ERROR_STOP on
\getenv db_name POSTGRES_DB
\getenv runtime_password APP_DB_PASSWORD
\getenv migrator_password MIGRATION_DB_PASSWORD
-- This script is also run on existing volumes: initdb-only hooks would skip them.
SELECT 'CREATE ROLE dependency_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dependency_migrator') \gexec
SELECT 'CREATE ROLE dependency_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dependency_app') \gexec
SELECT format('ALTER ROLE dependency_migrator PASSWORD %L', :'migrator_password') \gexec
SELECT format('ALTER ROLE dependency_app PASSWORD %L', :'runtime_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO dependency_migrator, dependency_app', :'db_name') \gexec
SELECT format('GRANT CREATE ON DATABASE %I TO dependency_migrator', :'db_name') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO dependency_migrator;
GRANT USAGE ON SCHEMA public TO dependency_app;
-- Transfer only the V1 history from the original scaffold; business tables are never recreated.
SELECT 'ALTER TABLE public.flyway_schema_history OWNER TO dependency_migrator'
WHERE to_regclass('public.flyway_schema_history') IS NOT NULL \gexec
\getenv enable_vector ENABLE_PGVECTOR
\if :{?enable_vector}
\if :enable_vector
CREATE EXTENSION IF NOT EXISTS vector;
\endif
\endif
