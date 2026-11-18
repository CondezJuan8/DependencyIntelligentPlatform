-- Single-use decisions linked to a specific administrator and pending owner.
ALTER TABLE user_access_tokens
    ADD COLUMN target_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN admission_decision TEXT;

ALTER TABLE user_access_tokens DROP CONSTRAINT user_access_tokens_purpose_check;
ALTER TABLE user_access_tokens ADD CONSTRAINT user_access_tokens_purpose_check
    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'REFRESH_SESSION', 'PLATFORM_ADMISSION_DECISION'));
ALTER TABLE user_access_tokens ADD CONSTRAINT user_access_tokens_admission_scope_check
    CHECK (
        (purpose = 'PLATFORM_ADMISSION_DECISION' AND target_user_id IS NOT NULL AND admission_decision IS NOT NULL AND admission_decision IN ('APPROVE', 'REJECT'))
        OR (purpose <> 'PLATFORM_ADMISSION_DECISION' AND target_user_id IS NULL AND admission_decision IS NULL)
    );
CREATE INDEX ix_admission_decision_tokens_pending
    ON user_access_tokens(target_user_id, created_at DESC)
    WHERE purpose = 'PLATFORM_ADMISSION_DECISION' AND consumed_at IS NULL;
