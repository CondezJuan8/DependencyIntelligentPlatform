-- OPTIONAL: not in the default Flyway location. Configure a verified embeddingDimensions placeholder first.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE document_chunk_vectors (
 organization_id UUID NOT NULL, embedding_id UUID NOT NULL, embedding_model_id UUID NOT NULL,
 embedding vector(${embeddingDimensions}) NOT NULL,
 PRIMARY KEY (organization_id, embedding_id),
 FOREIGN KEY (organization_id, embedding_id, embedding_model_id)
 REFERENCES document_chunk_embeddings(organization_id, id, embedding_model_id) ON DELETE RESTRICT
);
CREATE FUNCTION app_private.validate_vector_dimension() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM embedding_models WHERE id = NEW.embedding_model_id AND dimension = ${embeddingDimensions}) THEN
  RAISE EXCEPTION 'Embedding model dimension differs from configured vector column' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ck_vector_model_dimension BEFORE INSERT OR UPDATE ON document_chunk_vectors
 FOR EACH ROW EXECUTE FUNCTION app_private.validate_vector_dimension();
ALTER TABLE document_chunk_vectors ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunk_vectors FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_chunk_vectors
 USING (organization_id = app_private.current_organization_id()) WITH CHECK (organization_id = app_private.current_organization_id());
CREATE POLICY schema_migration_access ON document_chunk_vectors TO dependency_migrator USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON document_chunk_vectors TO dependency_runtime;
-- No ANN index is chosen before representative queries and volume are measured.
