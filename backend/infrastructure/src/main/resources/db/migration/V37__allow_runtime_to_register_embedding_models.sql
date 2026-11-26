-- Document indexing records the Ollama model and its dimension before storing vectors.
-- The shared catalog was created before runtime privileges were restricted and kept SELECT-only.
GRANT SELECT, INSERT, UPDATE ON embedding_models TO dependency_runtime;
