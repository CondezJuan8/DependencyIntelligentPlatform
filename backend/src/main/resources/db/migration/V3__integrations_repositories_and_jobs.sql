-- Immutable after application. See docs/database/README.md.

CREATE TABLE integrations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    external_account_id text NOT NULL,
    external_account_login text NOT NULL,
    status text NOT NULL DEFAULT 'CONNECTED' CHECK (status IN ('CONNECTED','SUSPENDED','DISCONNECTED')),
    credential_reference text,
    connected_at timestamptz NOT NULL DEFAULT now(),
    disconnected_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,provider_id,external_account_id),
    UNIQUE NULLS NOT DISTINCT (organization_id,id,provider_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (provider_id) REFERENCES integration_providers (id) ON DELETE RESTRICT
);

CREATE INDEX ix_integrations_fk1 ON integrations (provider_id);

CREATE TABLE github_installations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    installation_id bigint NOT NULL CHECK (installation_id>0),
    app_id bigint NOT NULL CHECK (app_id>0),
    suspended_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,integration_id),
    UNIQUE NULLS NOT DISTINCT (installation_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE repositories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    external_repository_id text NOT NULL,
    owner_login text NOT NULL,
    name text NOT NULL,
    html_url text,
    default_branch text NOT NULL DEFAULT 'main',
    visibility text NOT NULL CHECK (visibility IN ('PUBLIC','PRIVATE','INTERNAL')),
    is_archived boolean NOT NULL DEFAULT false,
    is_enabled boolean NOT NULL DEFAULT true,
    last_synced_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,integration_id,external_repository_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_repositories_name ON repositories (organization_id,owner_login,name);

CREATE TABLE repository_commits (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    commit_sha text NOT NULL,
    committed_at timestamptz,
    message text,
    html_url text,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,commit_sha),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,id),
    CHECK (commit_sha ~ '^([a-f0-9]{40}|[a-f0-9]{64})$'),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE repository_branches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    name text NOT NULL,
    last_commit_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,name),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, last_commit_id) REFERENCES repository_commits (organization_id, repository_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_repository_branches_fk2 ON repository_branches (organization_id, repository_id, last_commit_id);

CREATE TABLE repository_settings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    manual_scans_enabled boolean NOT NULL DEFAULT true,
    push_scans_enabled boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE repository_team_access (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    team_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,repository_id,team_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_repository_team_access_fk2 ON repository_team_access (organization_id, team_id);

ALTER TABLE repository_team_access ADD COLUMN role_id uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT;

CREATE TABLE webhook_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    delivery_id text NOT NULL,
    event_type text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    payload_hash text NOT NULL,
    status text NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','PROCESSED','FAILED','IGNORED')),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (provider_id,delivery_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, integration_id, provider_id) REFERENCES integrations (organization_id, id, provider_id) ON DELETE RESTRICT
);

CREATE INDEX ix_webhook_events_fk1 ON webhook_events (organization_id, integration_id, provider_id);

CREATE TABLE integration_sync_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    error_code text,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_integration_sync_runs_fk1 ON integration_sync_runs (organization_id, integration_id);

CREATE TABLE background_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    job_type_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    deduplication_key text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    locked_by text,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
    max_attempts integer NOT NULL DEFAULT 3 CHECK (max_attempts>0),
    last_error_code text,
    last_error_message text,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,deduplication_key),
    CHECK (attempt_count<=max_attempts),
    CHECK (jsonb_typeof(payload)='object'),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (job_type_id) REFERENCES job_types (id) ON DELETE RESTRICT
);

CREATE INDEX ix_background_jobs_fk1 ON background_jobs (job_type_id);

CREATE INDEX ix_jobs_pending ON background_jobs (organization_id,available_at,id) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE background_job_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    job_id uuid NOT NULL,
    attempt_number integer NOT NULL CHECK (attempt_number>0),
    worker text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    error_code text,
    error_message text,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,job_id,attempt_number),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE background_job_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    job_id uuid NOT NULL,
    event_type text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    details text,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_background_job_events_fk1 ON background_job_events (organization_id, job_id);
