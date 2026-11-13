-- Optional. An administrator must provision pgvector first; core never requires it.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_extension WHERE extname='vector') THEN
        RAISE EXCEPTION 'Enable pgvector as administrator before activating the vector Flyway location';
    END IF;
END $$;
CREATE TABLE document_chunk_embeddings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    document_chunk_id uuid NOT NULL,
    embedding_model_id uuid NOT NULL REFERENCES embedding_models(id) ON DELETE RESTRICT,
    embedding vector NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id,id),
    UNIQUE (organization_id,document_chunk_id,embedding_model_id),
    FOREIGN KEY (organization_id,document_chunk_id) REFERENCES document_chunks(organization_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_embeddings_model ON document_chunk_embeddings(embedding_model_id);
CREATE FUNCTION app.validate_embedding_dimensions() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected integer;
BEGIN
    SELECT dimensions INTO STRICT expected FROM embedding_models WHERE id=NEW.embedding_model_id;
    IF vector_dims(NEW.embedding)<>expected THEN
        RAISE EXCEPTION 'Embedding dimension does not match model' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER embedding_dimensions BEFORE INSERT OR UPDATE ON document_chunk_embeddings
FOR EACH ROW EXECUTE FUNCTION app.validate_embedding_dimensions();
-- Model dimensions are an identity property: register another model instead of mutating history.
CREATE FUNCTION app.immutable_embedding_model() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.dimensions<>OLD.dimensions OR NEW.model_version_id<>OLD.model_version_id THEN
        RAISE EXCEPTION 'Register a new embedding model instead' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_embedding_model BEFORE UPDATE ON embedding_models
FOR EACH ROW EXECUTE FUNCTION app.immutable_embedding_model();
ALTER TABLE document_chunk_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunk_embeddings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_chunk_embeddings USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());
GRANT SELECT,INSERT,UPDATE,DELETE ON document_chunk_embeddings TO dependency_app;
-- Mixed dimensions use untyped vector + model-aware validation. Once a model is chosen,
-- add a partial expression HNSW index cast to vector(N), filtered to that model_id.
-- Until then exact search must filter embedding_model_id before comparing distances.
