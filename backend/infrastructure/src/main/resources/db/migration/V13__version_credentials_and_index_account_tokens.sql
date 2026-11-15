ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0 CHECK (credential_version >= 0);
CREATE INDEX ix_account_token_requests ON user_access_tokens(user_id, purpose, created_at DESC);
