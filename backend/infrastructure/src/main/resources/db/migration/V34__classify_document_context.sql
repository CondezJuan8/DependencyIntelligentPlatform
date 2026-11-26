ALTER TABLE documents
  ADD COLUMN purpose TEXT NOT NULL DEFAULT 'GENERAL'
    CHECK (purpose IN ('GENERAL', 'MASTER_CONTEXT', 'VULNERABILITY_REFERENCE')),
  ADD COLUMN context_repository_id UUID;

ALTER TABLE documents
  ADD CONSTRAINT fk_document_context_repository
    FOREIGN KEY (organization_id, context_repository_id)
    REFERENCES repositories(organization_id, id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_document_master_context_scope
    CHECK ((purpose = 'MASTER_CONTEXT' AND context_repository_id IS NOT NULL)
        OR (purpose <> 'MASTER_CONTEXT' AND context_repository_id IS NULL));

CREATE UNIQUE INDEX ux_active_master_context_per_repository
  ON documents(organization_id, context_repository_id)
  WHERE purpose = 'MASTER_CONTEXT' AND status = 'ACTIVE';
