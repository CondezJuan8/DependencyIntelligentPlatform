-- Roles carry capabilities, not credentials. Login/password provisioning is outside migrations.
DO $$
DECLARE role_name TEXT;
BEGIN
 FOREACH role_name IN ARRAY ARRAY['dependency_runtime','dependency_migrator','dependency_catalog_writer','dependency_audit_admin'] LOOP
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
   EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', role_name);
  ELSIF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication OR rolcanlogin)) THEN
   RAISE EXCEPTION 'Existing capability role % has unsafe attributes; review role configuration', role_name;
  END IF;
 END LOOP;
 EXECUTE format('GRANT dependency_migrator TO %I', current_user);
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dependency_app') THEN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dependency_app' AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication)) THEN
   RAISE EXCEPTION 'dependency_app must be an unprivileged runtime login';
  END IF;
  GRANT dependency_runtime TO dependency_app;
 END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS app_private;
CREATE OR REPLACE FUNCTION app_private.current_organization_id() RETURNS UUID
LANGUAGE SQL STABLE SET search_path = pg_catalog AS $$
 SELECT nullif(current_setting('app.organization_id', true), '')::uuid;
$$;
REVOKE ALL ON FUNCTION app_private.current_organization_id() FROM PUBLIC;
GRANT USAGE ON SCHEMA app_private TO dependency_runtime, dependency_migrator, dependency_catalog_writer;
GRANT EXECUTE ON FUNCTION app_private.current_organization_id() TO dependency_runtime, dependency_migrator, dependency_catalog_writer;

ALTER TABLE integrations ADD CONSTRAINT uq_integration_provider_identity UNIQUE (organization_id, id, provider);
ALTER TABLE repositories ADD CONSTRAINT fk_repository_integration_provider FOREIGN KEY (organization_id, integration_id, provider)
 REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT;
ALTER TABLE github_installations ADD COLUMN provider TEXT NOT NULL DEFAULT 'github' CHECK (provider = 'github');
ALTER TABLE github_installations ADD CONSTRAINT fk_github_provider FOREIGN KEY (organization_id, integration_id, provider)
 REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT;
ALTER TABLE webhook_events ADD CONSTRAINT fk_webhook_provider FOREIGN KEY (organization_id, integration_id, provider)
 REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION app_private.validate_impact_validation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE valid_run BOOLEAN;
BEGIN
 IF NEW.classification = 'VALIDATED' THEN
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I.simulation_test_runs run JOIN %I.change_simulations simulation ON simulation.id = run.simulation_id AND simulation.organization_id = run.organization_id WHERE run.organization_id = $1 AND run.id = $2 AND simulation.impact_analysis_id = $3 AND run.finished_at IS NOT NULL AND run.status IN (''COMPLETED'',''FAILED''))', TG_TABLE_SCHEMA, TG_TABLE_SCHEMA)
   INTO valid_run USING NEW.organization_id, NEW.validation_test_run_id, NEW.impact_analysis_id;
  IF NOT valid_run THEN RAISE EXCEPTION 'VALIDATED finding requires a finished test run of the same analysis' USING ERRCODE = '23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER ck_impact_validation AFTER INSERT OR UPDATE ON impact_findings
 DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_validation();

CREATE OR REPLACE FUNCTION app_private.protect_terminal_scan() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status = 'RUNNING') OR
    (OLD.status = 'RUNNING' AND NEW.status IN ('COMPLETED','FAILED'))) THEN
  RAISE EXCEPTION 'Invalid scan lifecycle transition' USING ERRCODE = '23514';
 END IF;
 IF NEW.origin <> OLD.origin THEN RAISE EXCEPTION 'Snapshot origin is immutable' USING ERRCODE = '23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_scan_transition BEFORE UPDATE ON scans FOR EACH ROW EXECUTE FUNCTION app_private.protect_terminal_scan();

CREATE OR REPLACE FUNCTION app_private.reject_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT pg_has_role(current_user, 'dependency_audit_admin', 'member') THEN
  RAISE EXCEPTION 'Audit is append-only; exceptional maintenance requires dependency_audit_admin' USING ERRCODE = '42501';
 END IF;
 IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER audit_append_only BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation();
CREATE TRIGGER audit_change_append_only BEFORE UPDATE OR DELETE ON audit_event_changes FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation();

-- Every tenant table is covered, including tables introduced by this migration chain.
DO $$
DECLARE relation RECORD; tenant_column TEXT; schema_name TEXT := current_schema();
BEGIN
 EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM PUBLIC', schema_name);
 EXECUTE format('GRANT USAGE ON SCHEMA %I TO dependency_runtime, dependency_migrator, dependency_catalog_writer', schema_name);
 FOR relation IN SELECT table_name FROM information_schema.tables WHERE table_schema = schema_name AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history' ORDER BY table_name LOOP
  IF relation.table_name = 'organizations' OR EXISTS (
     SELECT 1 FROM information_schema.columns WHERE table_schema = schema_name AND table_name = relation.table_name AND column_name = 'organization_id') THEN
   tenant_column := CASE WHEN relation.table_name = 'organizations' THEN 'id' ELSE 'organization_id' END;
   EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', schema_name, relation.table_name);
   EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', schema_name, relation.table_name);
   EXECUTE format('CREATE POLICY tenant_isolation ON %I.%I USING (%I = app_private.current_organization_id()) WITH CHECK (%I = app_private.current_organization_id())', schema_name, relation.table_name, tenant_column, tenant_column);
   EXECUTE format('CREATE POLICY schema_migration_access ON %I.%I TO dependency_migrator USING (true) WITH CHECK (true)', schema_name, relation.table_name);
   EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I.%I TO dependency_runtime', schema_name, relation.table_name);
  ELSE
   EXECUTE format('GRANT SELECT ON %I.%I TO dependency_runtime', schema_name, relation.table_name);
  END IF;
 END LOOP;
 -- Identity is global, not a tenant catalogue; only the trusted authentication backend accesses these rows.
 EXECUTE format('GRANT INSERT, UPDATE, DELETE ON %I.users, %I.user_identities, %I.user_password_credentials, %I.user_access_tokens TO dependency_runtime', schema_name, schema_name, schema_name, schema_name);
 EXECUTE format('REVOKE UPDATE, DELETE ON %I.audit_events, %I.audit_event_changes FROM dependency_runtime', schema_name, schema_name);
 -- A separately provisioned advisory worker may maintain public advisories, not roles or provider configuration.
 FOR relation IN SELECT table_name FROM information_schema.tables WHERE table_schema = schema_name AND table_name LIKE 'vulnerability%' LOOP
  EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I.%I TO dependency_catalog_writer', schema_name, relation.table_name);
 END LOOP;
END $$;

-- Index FK lookup paths only when an existing non-partial index does not already cover the prefix.
DO $$
DECLARE fk RECORD; column_list TEXT; index_name TEXT;
BEGIN
 FOR fk IN SELECT constraint_row.*, namespace.nspname AS schema_name, relation.relname AS table_name
 FROM pg_constraint constraint_row JOIN pg_class relation ON relation.oid = constraint_row.conrelid
 JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
 WHERE constraint_row.contype = 'f' AND namespace.nspname = current_schema() ORDER BY relation.relname, constraint_row.conname LOOP
  IF NOT EXISTS (
   SELECT 1 FROM pg_index existing WHERE existing.indrelid = fk.conrelid AND existing.indisvalid AND existing.indpred IS NULL
   AND (SELECT array_agg(key ORDER BY ordinal) FROM unnest(existing.indkey::smallint[]) WITH ORDINALITY AS keys(key, ordinal)
        WHERE ordinal <= cardinality(fk.conkey)) = fk.conkey
  ) THEN
   SELECT string_agg(format('%I', attribute.attname), ', ' ORDER BY key.ordinal) INTO column_list
    FROM unnest(fk.conkey) WITH ORDINALITY AS key(number, ordinal)
    JOIN pg_attribute attribute ON attribute.attrelid = fk.conrelid AND attribute.attnum = key.number;
   index_name := 'ix_fk_' || left(fk.table_name, 37) || '_' || substr(md5(fk.conname), 1, 12);
   EXECUTE format('CREATE INDEX %I ON %I.%I (%s)', index_name, fk.schema_name, fk.table_name, column_list);
  END IF;
 END LOOP;
END $$;
