-- pgvector is installed by the pinned PostgreSQL image; vectors remain dimension-flexible.
-- Dimension and revision are measured from the configured Ollama model on first indexing.
CREATE EXTENSION IF NOT EXISTS vector;
COMMENT ON TABLE document_chunk_embeddings IS 'documentation: Chunk/model readiness metadata; the vector itself is stored and dimension-checked in document_chunk_vectors.';

CREATE TABLE document_chunk_vectors (
 organization_id UUID NOT NULL,
 embedding_id UUID NOT NULL,
 embedding_model_id UUID NOT NULL,
 embedding vector NOT NULL,
 PRIMARY KEY (organization_id, embedding_id),
 FOREIGN KEY (organization_id, embedding_id, embedding_model_id)
   REFERENCES document_chunk_embeddings(organization_id, id, embedding_model_id) ON DELETE RESTRICT
);
CREATE INDEX ix_document_chunk_vectors_model ON document_chunk_vectors(embedding_model_id, organization_id);

CREATE OR REPLACE FUNCTION app_private.validate_document_vector_dimension() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE expected_dimension INTEGER;
BEGIN
 SELECT dimension INTO expected_dimension FROM embedding_models WHERE id=NEW.embedding_model_id;
 IF expected_dimension IS NULL OR vector_dims(NEW.embedding) <> expected_dimension THEN
  RAISE EXCEPTION 'Embedding dimension does not match the registered model' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_document_vector_dimension BEFORE INSERT OR UPDATE ON document_chunk_vectors
 FOR EACH ROW EXECUTE FUNCTION app_private.validate_document_vector_dimension();

ALTER TABLE document_chunk_vectors ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunk_vectors FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_chunk_vectors
 USING (organization_id=app_private.current_organization_id()) WITH CHECK (organization_id=app_private.current_organization_id());
CREATE POLICY schema_migration_access ON document_chunk_vectors TO dependency_migrator USING (true) WITH CHECK (true);
GRANT SELECT,INSERT,UPDATE,DELETE ON document_chunk_vectors TO dependency_runtime,dependency_migrator;
GRANT EXECUTE ON FUNCTION app_private.validate_document_vector_dimension() TO dependency_runtime,dependency_migrator;
