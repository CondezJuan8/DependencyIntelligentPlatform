-- Immutable after application. See docs/database/README.md.

CREATE TABLE documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    name text NOT NULL,
    document_type_id uuid NOT NULL,
    origin text NOT NULL,
    status text NOT NULL DEFAULT 'UPLOADED' CHECK (status IN ('UPLOADED','PROCESSING','INDEXED','FAILED','DELETED')),
    uploaded_by_membership_id uuid NOT NULL,
    retention_policy_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (document_type_id) REFERENCES document_types (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, uploaded_by_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, retention_policy_id) REFERENCES data_retention_policies (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_documents_fk1 ON documents (document_type_id);

CREATE INDEX ix_documents_fk2 ON documents (organization_id, uploaded_by_membership_id);

CREATE INDEX ix_documents_fk3 ON documents (organization_id, retention_policy_id);

CREATE TABLE document_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    version_number integer NOT NULL CHECK (version_number>0),
    sha256 text NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    size_bytes bigint NOT NULL CHECK (size_bytes>=0),
    validated_mime_type text NOT NULL,
    extractor_version text,
    temporary_storage_reference text,
    temporary_expires_at timestamptz,
    original_deleted_at timestamptz,
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_id,version_number),
    CHECK ((temporary_storage_reference IS NULL OR temporary_expires_at IS NOT NULL)),
    CHECK ((original_deleted_at IS NULL OR temporary_storage_reference IS NULL)),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT
);

CREATE TABLE document_repositories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_id,repository_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_repositories_fk2 ON document_repositories (organization_id, repository_id);

CREATE TABLE document_components (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    component_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_id,component_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_id) REFERENCES components (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_components_fk2 ON document_components (organization_id, component_id);

CREATE TABLE document_component_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    component_version_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_id,component_version_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_component_versions_fk2 ON document_component_versions (organization_id, component_version_id);

CREATE TABLE document_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_version_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal>=0),
    content text NOT NULL,
    page_start integer CHECK (page_start>0),
    page_end integer,
    section_path text,
    token_count integer CHECK (token_count>=0),
    content_hash text NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,document_version_id,ordinal),
    CHECK (page_end IS NULL OR (page_start IS NOT NULL AND page_end>=page_start)),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_chunks_search ON document_chunks USING gin (to_tsvector('simple',content));

CREATE TABLE document_processing_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    document_version_id uuid NOT NULL,
    job_id uuid NOT NULL,
    tool text NOT NULL,
    tool_version text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    error_code text,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_processing_runs_fk1 ON document_processing_runs (organization_id, document_version_id);

CREATE INDEX ix_document_processing_runs_fk2 ON document_processing_runs (organization_id, job_id);

CREATE TABLE document_processing_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    processing_run_id uuid NOT NULL,
    storage_reference text NOT NULL,
    expires_at timestamptz NOT NULL,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, processing_run_id) REFERENCES document_processing_runs (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_document_processing_artifacts_fk1 ON document_processing_artifacts (organization_id, processing_run_id);

CREATE TABLE assistant_model_providers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

CREATE TABLE assistant_model_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id uuid NOT NULL,
    model_name text NOT NULL,
    model_version text NOT NULL,
    configuration jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE NULLS NOT DISTINCT (provider_id,model_name,model_version),
    FOREIGN KEY (provider_id) REFERENCES assistant_model_providers (id) ON DELETE RESTRICT
);

CREATE TABLE embedding_models (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id uuid NOT NULL,
    dimensions integer NOT NULL CHECK (dimensions>0),
    UNIQUE NULLS NOT DISTINCT (model_version_id),
    FOREIGN KEY (model_version_id) REFERENCES assistant_model_versions (id) ON DELETE RESTRICT
);

CREATE TABLE assistant_prompt_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    version text NOT NULL,
    content text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (name,version)
);

CREATE TABLE assistant_conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    owner_membership_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    title text,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, owner_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_assistant_conversations_fk1 ON assistant_conversations (organization_id, owner_membership_id);

CREATE TABLE assistant_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal>=0),
    speaker text NOT NULL CHECK (speaker IN ('USER','ASSISTANT','SYSTEM')),
    content text NOT NULL,
    model_version_id uuid,
    prompt_version_id uuid,
    input_tokens integer CHECK (input_tokens>=0),
    output_tokens integer CHECK (output_tokens>=0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,conversation_id,ordinal),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (model_version_id) REFERENCES assistant_model_versions (id) ON DELETE RESTRICT,
    FOREIGN KEY (prompt_version_id) REFERENCES assistant_prompt_versions (id) ON DELETE RESTRICT
);

CREATE INDEX ix_assistant_messages_fk2 ON assistant_messages (model_version_id);

CREATE INDEX ix_assistant_messages_fk3 ON assistant_messages (prompt_version_id);

CREATE TABLE assistant_message_evidences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    message_id uuid NOT NULL,
    document_chunk_id uuid,
    scan_component_id uuid,
    finding_id uuid,
    citation_ordinal integer NOT NULL CHECK (citation_ordinal>0),
    relevance numeric(6,5) CHECK (relevance BETWEEN 0 AND 1),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,message_id,citation_ordinal),
    CHECK (num_nonnulls(document_chunk_id,scan_component_id,finding_id)=1),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, message_id) REFERENCES assistant_messages (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_assistant_message_evidences_fk2 ON assistant_message_evidences (organization_id, document_chunk_id);

CREATE INDEX ix_assistant_message_evidences_fk3 ON assistant_message_evidences (organization_id, scan_component_id);

CREATE INDEX ix_assistant_message_evidences_fk4 ON assistant_message_evidences (organization_id, finding_id);

CREATE TABLE deletion_request_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    deletion_request_id uuid NOT NULL,
    document_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,deletion_request_id,document_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, deletion_request_id) REFERENCES data_deletion_requests (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, document_id) REFERENCES documents (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_deletion_request_documents_fk2 ON deletion_request_documents (organization_id, document_id);
