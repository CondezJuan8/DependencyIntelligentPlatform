-- Core metadata does not install pgvector, select a model, or contact an AI provider.
CREATE TABLE document_types (code TEXT PRIMARY KEY, description TEXT NOT NULL);
INSERT INTO document_types VALUES ('PDF','PDF'),('DOCX','Word document'),('MARKDOWN','Markdown'),('TXT','Plain text'),('README','Repository readme'),('OPENAPI','API specification'),('ADR','Architecture decision'),('CHANGELOG','Release notes');
CREATE TABLE documents (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
 name TEXT NOT NULL CHECK (btrim(name) <> ''), document_type TEXT NOT NULL REFERENCES document_types(code) ON DELETE RESTRICT,
 origin TEXT NOT NULL CHECK (origin IN ('UPLOAD','REPOSITORY','INTEGRATION')), description TEXT,
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED','DELETION_PENDING')),
 uploaded_by_membership_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, uploaded_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_versions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, document_id UUID NOT NULL,
 version_number INTEGER NOT NULL CHECK (version_number > 0),
 sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'), size_bytes BIGINT NOT NULL CHECK (size_bytes > 0), mime_type TEXT NOT NULL,
 original_filename TEXT NOT NULL, storage_backend TEXT NOT NULL CHECK (storage_backend IN ('LOCAL','OBJECT')),
 original_status TEXT NOT NULL DEFAULT 'RETAINED' CHECK (original_status IN ('RETAINED','DELETED')),
 storage_key TEXT, temporary_storage_key TEXT, temporary_expires_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ,
 indexing_status TEXT NOT NULL DEFAULT 'PENDING' REFERENCES job_statuses(code) ON DELETE RESTRICT,
 uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), UNIQUE (organization_id, document_id, version_number),
 CHECK ((original_status = 'RETAINED' AND storage_key IS NOT NULL AND deleted_at IS NULL) OR
        (original_status = 'DELETED' AND storage_key IS NULL AND temporary_storage_key IS NULL AND deleted_at IS NOT NULL)),
 CHECK (storage_key IS NULL OR (storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$' AND storage_key !~ '(^|/)\.\.(/|$)')),
 CHECK ((temporary_storage_key IS NULL) = (temporary_expires_at IS NULL)),
 CHECK (temporary_storage_key IS NULL OR (temporary_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$' AND temporary_storage_key !~ '(^|/)\.\.(/|$)')),
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_repositories (
 organization_id UUID NOT NULL, document_id UUID NOT NULL, repository_id UUID NOT NULL,
 PRIMARY KEY (organization_id, document_id, repository_id),
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_document_repository_reverse ON document_repositories(organization_id, repository_id);
CREATE TABLE document_components (
 organization_id UUID NOT NULL, document_id UUID NOT NULL, component_id UUID NOT NULL,
 PRIMARY KEY (organization_id, document_id, component_id),
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_component_versions (
 organization_id UUID NOT NULL, document_id UUID NOT NULL, component_version_id UUID NOT NULL,
 PRIMARY KEY (organization_id, document_id, component_version_id),
 FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_chunks (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, document_version_id UUID NOT NULL,
 ordinal INTEGER NOT NULL CHECK (ordinal > 0), content TEXT NOT NULL CHECK (btrim(content) <> ''),
 page_start INTEGER, page_end INTEGER, section_path TEXT, token_count INTEGER CHECK (token_count >= 0),
 content_hash TEXT NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
 UNIQUE (organization_id, id), UNIQUE (organization_id, document_version_id, ordinal),
 CHECK ((page_start IS NULL AND page_end IS NULL) OR (page_start IS NOT NULL AND page_end IS NOT NULL AND page_start > 0 AND page_end >= page_start)),
 FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_document_chunks_text ON document_chunks USING gin(to_tsvector('simple', content));
CREATE TABLE document_processing_runs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, document_version_id UUID NOT NULL, background_job_id UUID,
 stage TEXT NOT NULL CHECK (stage IN ('EXTRACT','OCR','CHUNK','EMBED','INDEX')), tool_name TEXT NOT NULL, tool_version TEXT,
 status TEXT NOT NULL REFERENCES job_statuses(code) ON DELETE RESTRICT,
 started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMPTZ, error_code TEXT,
 UNIQUE (organization_id, id), CHECK (finished_at IS NULL OR finished_at >= started_at),
 FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE document_processing_artifacts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, processing_run_id UUID NOT NULL,
 artifact_type TEXT NOT NULL, storage_key TEXT NOT NULL, sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
 expires_at TIMESTAMPTZ NOT NULL, deleted_at TIMESTAMPTZ,
 FOREIGN KEY (organization_id, processing_run_id) REFERENCES document_processing_runs(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE assistant_model_providers (code TEXT PRIMARY KEY, name TEXT NOT NULL);
INSERT INTO assistant_model_providers VALUES ('gemini','Gemini'),('ollama','Ollama');
CREATE TABLE assistant_model_versions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), provider TEXT NOT NULL REFERENCES assistant_model_providers(code) ON DELETE RESTRICT,
 model_name TEXT NOT NULL, revision TEXT NOT NULL, UNIQUE (provider, model_name, revision)
);
CREATE TABLE embedding_models (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), provider TEXT NOT NULL REFERENCES assistant_model_providers(code) ON DELETE RESTRICT,
 model_name TEXT NOT NULL, revision TEXT NOT NULL, dimension INTEGER NOT NULL CHECK (dimension > 0),
 UNIQUE (provider, model_name, revision)
);
CREATE TABLE document_chunk_embeddings (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, document_chunk_id UUID NOT NULL,
 embedding_model_id UUID NOT NULL REFERENCES embedding_models(id) ON DELETE RESTRICT,
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','FAILED','READY')),
 vector_reference TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), UNIQUE (organization_id, id, embedding_model_id),
 UNIQUE (organization_id, document_chunk_id, embedding_model_id),
 CHECK ((status = 'READY') = (vector_reference IS NOT NULL)),
 FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE assistant_prompt_versions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), purpose TEXT NOT NULL, version TEXT NOT NULL,
 instructions TEXT NOT NULL, UNIQUE (purpose, version)
);
CREATE TABLE assistant_conversations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, created_by_membership_id UUID NOT NULL,
 title TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (organization_id, id),
 FOREIGN KEY (organization_id, created_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE assistant_messages (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, conversation_id UUID NOT NULL,
 ordinal INTEGER NOT NULL CHECK (ordinal > 0), role TEXT NOT NULL CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
 content TEXT NOT NULL, model_version_id UUID REFERENCES assistant_model_versions(id) ON DELETE RESTRICT,
 prompt_version_id UUID REFERENCES assistant_prompt_versions(id) ON DELETE RESTRICT,
 input_tokens INTEGER CHECK (input_tokens >= 0), output_tokens INTEGER CHECK (output_tokens >= 0),
 status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('PENDING','COMPLETED','FAILED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (organization_id, id), UNIQUE (organization_id, conversation_id, ordinal),
 FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE assistant_message_evidences (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, message_id UUID NOT NULL,
 ordinal INTEGER NOT NULL CHECK (ordinal > 0), document_chunk_id UUID, scan_component_id UUID, finding_id UUID,
 citation_label TEXT NOT NULL, CHECK (num_nonnulls(document_chunk_id, scan_component_id, finding_id) = 1),
 UNIQUE (organization_id, message_id, ordinal),
 FOREIGN KEY (organization_id, message_id) REFERENCES assistant_messages(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT,
 FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT
);
COMMENT ON TABLE document_chunk_embeddings IS 'documentation: Model/chunk metadata; physical vectors require the optional pgvector migration and a verified model/dimension. No model is seeded.';
