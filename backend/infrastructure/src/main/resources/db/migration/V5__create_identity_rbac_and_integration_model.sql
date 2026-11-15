ALTER TABLE organizations ADD COLUMN legal_name TEXT;
ALTER TABLE organizations ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
ALTER TABLE organizations ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN email_normalized TEXT GENERATED ALWAYS AS (lower(btrim(email))) STORED;
ALTER TABLE users ADD CONSTRAINT uq_user_email_normalized UNIQUE (email_normalized);
DROP INDEX uq_users_email;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN avatar_url TEXT;
ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;

CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    provider TEXT NOT NULL CHECK (provider IN ('google', 'github', 'legacy')),
    provider_subject TEXT NOT NULL CHECK (btrim(provider_subject) <> ''),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_subject), UNIQUE (user_id, provider)
);
INSERT INTO user_identities(user_id, provider, provider_subject) SELECT id, 'legacy', subject FROM users;
ALTER TABLE users DROP COLUMN subject;
CREATE INDEX ix_user_identities_user ON user_identities(user_id);
CREATE TABLE user_password_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    password_hash TEXT NOT NULL CHECK (btrim(password_hash) <> ''),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE user_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    purpose TEXT NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'REFRESH_SESSION')),
    token_hash TEXT NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ, CHECK (expires_at > created_at), CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);
CREATE INDEX ix_user_access_tokens_user ON user_access_tokens(user_id, purpose);

CREATE TABLE roles (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE, name TEXT NOT NULL);
INSERT INTO roles(code, name) VALUES ('OWNER', 'Owner'), ('ADMIN', 'Administrator'), ('MEMBER', 'Member'), ('VIEWER', 'Viewer');
CREATE TABLE permissions (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT NOT NULL UNIQUE, description TEXT NOT NULL);
INSERT INTO permissions(code, description) VALUES
 ('organization.manage', 'Manage organization and membership'), ('team.manage', 'Manage teams'),
 ('repository.read', 'Read repositories'), ('repository.manage', 'Manage repository connections'),
 ('scan.read', 'Read scan snapshots'), ('scan.create', 'Request scans and import SBOM'),
 ('document.read', 'Read authorized documents'), ('document.manage', 'Manage documents'),
 ('assistant.query', 'Query authorized evidence'), ('impact.create', 'Request impact analysis'),
 ('change.simulate', 'Request an isolated change test'), ('change.propose', 'Approve a proposed pull request'),
 ('report.read', 'Read reports'), ('report.export', 'Export authorized data'), ('audit.read', 'Read audit events');
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT, PRIMARY KEY (role_id, permission_id)
);
INSERT INTO role_permissions SELECT role.id, permission.id FROM roles role CROSS JOIN permissions permission WHERE role.code IN ('OWNER', 'ADMIN');
INSERT INTO role_permissions SELECT role.id, permission.id FROM roles role CROSS JOIN permissions permission
 WHERE role.code = 'MEMBER' AND permission.code IN ('repository.read','scan.read','scan.create','document.read','document.manage','assistant.query','impact.create','change.simulate','report.read','report.export');
INSERT INTO role_permissions SELECT role.id, permission.id FROM roles role CROSS JOIN permissions permission
 WHERE role.code = 'VIEWER' AND permission.code IN ('repository.read','scan.read','document.read','report.read');

ALTER TABLE memberships RENAME TO organization_memberships;
ALTER TABLE organization_memberships ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE organization_memberships ADD CONSTRAINT uq_membership_id UNIQUE (id);
ALTER TABLE organization_memberships ADD CONSTRAINT uq_membership_tenant_id UNIQUE (organization_id, id);
ALTER TABLE organization_memberships RENAME COLUMN created_at TO joined_at;
ALTER TABLE organization_memberships ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','LEFT'));
ALTER TABLE organization_memberships ADD COLUMN invited_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;
CREATE TABLE membership_roles (
    organization_id UUID NOT NULL, membership_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (organization_id, membership_id, role_id),
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
INSERT INTO membership_roles SELECT membership.organization_id, membership.id, role.id FROM organization_memberships membership JOIN roles role ON role.code = membership.role;
ALTER TABLE organization_memberships DROP COLUMN role;

CREATE TABLE membership_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    email_normalized TEXT NOT NULL CHECK (email_normalized = lower(btrim(email_normalized)) AND email_normalized <> ''),
    token_hash TEXT NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    invited_by_membership_id UUID NOT NULL, accepted_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, id), CHECK (expires_at > created_at),
    FOREIGN KEY (organization_id, invited_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_pending_invitation ON membership_invitations(organization_id, email_normalized) WHERE status = 'PENDING';
CREATE TABLE invitation_roles (
    organization_id UUID NOT NULL, invitation_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT, PRIMARY KEY (organization_id, invitation_id, role_id),
    FOREIGN KEY (organization_id, invitation_id) REFERENCES membership_invitations(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE organization_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name TEXT NOT NULL CHECK (btrim(name) <> ''), UNIQUE (organization_id, id), UNIQUE (organization_id, name)
);
CREATE TABLE team_memberships (
    organization_id UUID NOT NULL, team_id UUID NOT NULL, membership_id UUID NOT NULL,
    PRIMARY KEY (organization_id, team_id, membership_id),
    FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_team_membership_member ON team_memberships(organization_id, membership_id);
CREATE TABLE repository_team_access (
    organization_id UUID NOT NULL, repository_id UUID NOT NULL, team_id UUID NOT NULL,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
    PRIMARY KEY (organization_id, repository_id, team_id, permission_id),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_repository_team_access_team ON repository_team_access(organization_id, team_id);

CREATE TABLE integration_providers (code TEXT PRIMARY KEY CHECK (btrim(code) <> ''), name TEXT NOT NULL);
INSERT INTO integration_providers VALUES ('github','GitHub'), ('demo','Synthetic development fixture');
INSERT INTO integration_providers SELECT DISTINCT provider, provider FROM integrations ON CONFLICT DO NOTHING;
INSERT INTO integration_providers SELECT DISTINCT provider, provider FROM repositories ON CONFLICT DO NOTHING;
ALTER TABLE integrations ADD CONSTRAINT fk_integration_provider FOREIGN KEY (provider) REFERENCES integration_providers(code) ON DELETE RESTRICT;
ALTER TABLE repositories ADD CONSTRAINT fk_repository_provider FOREIGN KEY (provider) REFERENCES integration_providers(code) ON DELETE RESTRICT;
ALTER TABLE integrations ADD COLUMN external_account_login TEXT;
ALTER TABLE integrations ADD COLUMN credential_reference TEXT;
ALTER TABLE integrations ADD COLUMN disconnected_at TIMESTAMPTZ;
ALTER TABLE repositories ADD COLUMN html_url TEXT;
ALTER TABLE repositories ADD COLUMN visibility TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (visibility IN ('PUBLIC','PRIVATE','INTERNAL','UNKNOWN'));
ALTER TABLE repositories ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE repositories ADD COLUMN is_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE repositories ADD COLUMN last_synced_at TIMESTAMPTZ;
CREATE INDEX ix_repositories_name ON repositories(organization_id, full_name);

CREATE TABLE github_installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, integration_id UUID NOT NULL,
    installation_external_id BIGINT NOT NULL UNIQUE CHECK (installation_external_id > 0),
    account_type TEXT NOT NULL CHECK (account_type IN ('USER','ORGANIZATION')),
    UNIQUE (organization_id, id), UNIQUE (organization_id, integration_id),
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE repository_commits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, repository_id UUID NOT NULL,
    commit_sha VARCHAR(64) NOT NULL CHECK (commit_sha ~ '^[a-fA-F0-9]{40}([a-fA-F0-9]{24})?$'),
    authored_at TIMESTAMPTZ, message TEXT, html_url TEXT,
    UNIQUE (organization_id, repository_id, id), UNIQUE (organization_id, repository_id, commit_sha),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT
);
CREATE TABLE repository_branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, repository_id UUID NOT NULL,
    name TEXT NOT NULL CHECK (btrim(name) <> ''), last_commit_id UUID,
    UNIQUE (organization_id, repository_id, name),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, repository_id, last_commit_id) REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT
);
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, integration_id UUID NOT NULL,
    provider TEXT NOT NULL REFERENCES integration_providers(code) ON DELETE RESTRICT,
    delivery_id TEXT NOT NULL, event_type TEXT NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ, status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSED','FAILED','IGNORED')),
    payload_hash TEXT NOT NULL CHECK (payload_hash ~ '^[a-f0-9]{64}$'), sanitized_payload JSONB,
    UNIQUE (organization_id, id), UNIQUE (provider, delivery_id),
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_webhook_pending ON webhook_events(organization_id, received_at) WHERE status = 'PENDING';
CREATE TABLE integration_sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, integration_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMPTZ,
    status TEXT NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')), error_code TEXT,
    FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT,
    CHECK (finished_at IS NULL OR finished_at >= started_at)
);
CREATE TABLE repository_settings (
    organization_id UUID NOT NULL, repository_id UUID NOT NULL,
    scan_on_push BOOLEAN NOT NULL DEFAULT false, max_scan_seconds INTEGER NOT NULL DEFAULT 600 CHECK (max_scan_seconds BETWEEN 1 AND 86400),
    PRIMARY KEY (organization_id, repository_id),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT
);
