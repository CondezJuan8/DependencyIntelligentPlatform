-- Complete legacy FK deletion semantics explicitly without changing historical data.
DO $$
DECLARE fk RECORD;
BEGIN
 FOR fk IN SELECT constraint_row.conname, relation.relname, pg_get_constraintdef(constraint_row.oid) AS definition
 FROM pg_constraint constraint_row JOIN pg_class relation ON relation.oid=constraint_row.conrelid
 JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
 WHERE constraint_row.contype='f' AND constraint_row.confdeltype='a' AND namespace.nspname=current_schema() LOOP
  EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk.relname, fk.conname);
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s ON DELETE RESTRICT', fk.relname, fk.conname, fk.definition);
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION app_private.validate_impact_evidence_link() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE parent_table TEXT; parent_id UUID; valid_link BOOLEAN;
BEGIN
 IF TG_TABLE_NAME='impact_finding_evidences' THEN
  parent_table := 'impact_findings'; parent_id := NEW.finding_id;
 ELSE
  parent_table := 'impact_recommendations'; parent_id := NEW.recommendation_id;
 END IF;
 EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I.%I parent JOIN %I.impact_evidences evidence ON evidence.organization_id=parent.organization_id AND evidence.impact_analysis_id=parent.impact_analysis_id WHERE parent.organization_id=$1 AND parent.id=$2 AND evidence.id=$3)', TG_TABLE_SCHEMA, parent_table, TG_TABLE_SCHEMA)
 INTO valid_link USING NEW.organization_id, parent_id, NEW.evidence_id;
 IF NOT valid_link THEN RAISE EXCEPTION 'Impact evidence and its parent must belong to the same analysis' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER ck_finding_evidence_analysis AFTER INSERT OR UPDATE ON impact_finding_evidences
 DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_evidence_link();
CREATE CONSTRAINT TRIGGER ck_recommendation_evidence_analysis AFTER INSERT OR UPDATE ON impact_recommendation_evidences
 DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_evidence_link();

CREATE OR REPLACE FUNCTION app_private.protect_finished_test() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.simulation_id <> OLD.simulation_id OR NEW.organization_id <> OLD.organization_id
    OR (OLD.finished_at IS NOT NULL AND NEW IS DISTINCT FROM OLD) THEN
  RAISE EXCEPTION 'Executed test evidence is immutable; record a new attempt instead' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_finished_test_immutable BEFORE UPDATE ON simulation_test_runs FOR EACH ROW EXECUTE FUNCTION app_private.protect_finished_test();
CREATE OR REPLACE FUNCTION app_private.protect_simulation_context() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.organization_id,NEW.repository_id,NEW.impact_analysis_id,NEW.base_commit_id,NEW.environment_reference,NEW.configuration_version)
    IS DISTINCT FROM ROW(OLD.organization_id,OLD.repository_id,OLD.impact_analysis_id,OLD.base_commit_id,OLD.environment_reference,OLD.configuration_version) THEN
  RAISE EXCEPTION 'Simulation provenance is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_simulation_context BEFORE UPDATE ON change_simulations FOR EACH ROW EXECUTE FUNCTION app_private.protect_simulation_context();

CREATE OR REPLACE FUNCTION app_private.enforce_attempt_limit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE maximum INTEGER;
BEGIN
 EXECUTE format('SELECT max_attempts FROM %I.background_jobs WHERE organization_id=$1 AND id=$2 FOR UPDATE', TG_TABLE_SCHEMA)
 INTO maximum USING NEW.organization_id, NEW.job_id;
 IF maximum IS NULL OR NEW.attempt_number > maximum THEN
  RAISE EXCEPTION 'Job attempt exceeds configured maximum or job is not accessible' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_job_attempt_limit BEFORE INSERT OR UPDATE ON background_job_attempts FOR EACH ROW EXECUTE FUNCTION app_private.enforce_attempt_limit();

CREATE OR REPLACE FUNCTION app_private.protect_terminal_scan() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status = 'RUNNING') OR
    (OLD.status = 'RUNNING' AND NEW.status IN ('COMPLETED','FAILED'))) THEN
  RAISE EXCEPTION 'Invalid scan lifecycle transition' USING ERRCODE = '23514';
 END IF;
 IF NEW.origin <> OLD.origin OR NEW.organization_id <> OLD.organization_id OR NEW.repository_id <> OLD.repository_id
    OR (OLD.status IN ('COMPLETED','FAILED') AND NEW.repository_commit_id IS DISTINCT FROM OLD.repository_commit_id) THEN
  RAISE EXCEPTION 'Snapshot provenance is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
