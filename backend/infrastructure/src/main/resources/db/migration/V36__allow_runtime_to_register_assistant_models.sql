-- Nexo records the provider/model revision with each generated answer.
-- This shared metadata table was created before runtime privileges were restricted
-- and therefore retained SELECT-only access from V10.
GRANT SELECT, INSERT, UPDATE ON assistant_model_versions TO dependency_runtime;
