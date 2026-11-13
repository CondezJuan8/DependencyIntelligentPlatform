-- Immutable after application. See docs/database/README.md.

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email_normalized text NOT NULL,
    email_verified_at timestamptz,
    password_hash text,
    display_name text NOT NULL,
    avatar_url text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (email_normalized),
    CHECK (email_normalized = lower(btrim(email_normalized)) AND position('@' in email_normalized)>1)
);

CREATE TABLE organizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    slug text NOT NULL,
    legal_name text,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (slug),
    CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE user_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider text NOT NULL,
    provider_subject text NOT NULL,
    user_id uuid NOT NULL,
    UNIQUE NULLS NOT DISTINCT (provider,provider_subject),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_identities_fk0 ON user_identities (user_id);

CREATE TABLE roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO roles (code,name) VALUES ('OWNER','OWNER') ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code,name) VALUES ('ADMIN','ADMIN') ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code,name) VALUES ('MEMBER','MEMBER') ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code,name) VALUES ('VIEWER','VIEWER') ON CONFLICT (code) DO NOTHING;

CREATE TABLE permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO permissions (code,name) VALUES ('organization.manage','organization.manage') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('repository.read','repository.read') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('repository.manage','repository.manage') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('scan.create','scan.create') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('scan.read','scan.read') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('document.read','document.read') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('document.manage','document.manage') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('report.export','report.export') ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code,name) VALUES ('audit.read','audit.read') ON CONFLICT (code) DO NOTHING;

CREATE TABLE role_permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    UNIQUE NULLS NOT DISTINCT (role_id,permission_id),
    FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE RESTRICT
);

CREATE INDEX ix_role_permissions_fk1 ON role_permissions (permission_id);

CREATE TABLE organization_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED','ACTIVE','SUSPENDED','LEFT')),
    joined_at timestamptz NOT NULL DEFAULT now(),
    invited_by_user_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,user_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    FOREIGN KEY (invited_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_organization_memberships_fk1 ON organization_memberships (user_id);

CREATE INDEX ix_organization_memberships_fk2 ON organization_memberships (invited_by_user_id);

CREATE TABLE membership_roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,membership_id,role_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE INDEX ix_membership_roles_fk2 ON membership_roles (role_id);

CREATE TABLE membership_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    email_normalized text NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    inviter_membership_id uuid NOT NULL,
    accepted_membership_id uuid,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (token_hash),
    CHECK (email_normalized=lower(btrim(email_normalized))),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, inviter_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, accepted_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_membership_invitations_fk1 ON membership_invitations (organization_id, inviter_membership_id);

CREATE INDEX ix_membership_invitations_fk2 ON membership_invitations (organization_id, accepted_membership_id);

CREATE TABLE organization_teams (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    name text NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,name),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TABLE team_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    team_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,team_id,membership_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_team_memberships_fk2 ON team_memberships (organization_id, membership_id);

CREATE TABLE integration_providers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO integration_providers (code,name) VALUES ('github','github') ON CONFLICT (code) DO NOTHING;

CREATE TABLE package_ecosystems (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO package_ecosystems (code,name) VALUES ('Maven','Maven') ON CONFLICT (code) DO NOTHING;

INSERT INTO package_ecosystems (code,name) VALUES ('npm','npm') ON CONFLICT (code) DO NOTHING;

INSERT INTO package_ecosystems (code,name) VALUES ('PyPI','PyPI') ON CONFLICT (code) DO NOTHING;

INSERT INTO package_ecosystems (code,name) VALUES ('NuGet','NuGet') ON CONFLICT (code) DO NOTHING;

INSERT INTO package_ecosystems (code,name) VALUES ('Go','Go') ON CONFLICT (code) DO NOTHING;

CREATE TABLE dependency_scopes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO dependency_scopes (code,name) VALUES ('runtime','runtime') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_scopes (code,name) VALUES ('development','development') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_scopes (code,name) VALUES ('test','test') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_scopes (code,name) VALUES ('optional','optional') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_scopes (code,name) VALUES ('provided','provided') ON CONFLICT (code) DO NOTHING;

CREATE TABLE dependency_relationship_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO dependency_relationship_types (code,name) VALUES ('depends_on','depends_on') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_relationship_types (code,name) VALUES ('contains','contains') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_relationship_types (code,name) VALUES ('provides','provides') ON CONFLICT (code) DO NOTHING;

INSERT INTO dependency_relationship_types (code,name) VALUES ('optional_dependency','optional_dependency') ON CONFLICT (code) DO NOTHING;

CREATE TABLE vulnerability_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO vulnerability_sources (code,name) VALUES ('OSV','OSV') ON CONFLICT (code) DO NOTHING;

CREATE TABLE scanner_tools (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO scanner_tools (code,name) VALUES ('cdxgen','cdxgen') ON CONFLICT (code) DO NOTHING;

CREATE TABLE license_catalog (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO license_catalog (code,name) VALUES ('MIT','MIT') ON CONFLICT (code) DO NOTHING;

INSERT INTO license_catalog (code,name) VALUES ('Apache-2.0','Apache-2.0') ON CONFLICT (code) DO NOTHING;

INSERT INTO license_catalog (code,name) VALUES ('BSD-3-Clause','BSD-3-Clause') ON CONFLICT (code) DO NOTHING;

CREATE TABLE job_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO job_types (code,name) VALUES ('SCAN','SCAN') ON CONFLICT (code) DO NOTHING;

INSERT INTO job_types (code,name) VALUES ('VULNERABILITY_LOOKUP','VULNERABILITY_LOOKUP') ON CONFLICT (code) DO NOTHING;

INSERT INTO job_types (code,name) VALUES ('DOCUMENT_INDEX','DOCUMENT_INDEX') ON CONFLICT (code) DO NOTHING;

INSERT INTO job_types (code,name) VALUES ('REPORT','REPORT') ON CONFLICT (code) DO NOTHING;

INSERT INTO job_types (code,name) VALUES ('NOTIFICATION','NOTIFICATION') ON CONFLICT (code) DO NOTHING;

INSERT INTO job_types (code,name) VALUES ('DELETION','DELETION') ON CONFLICT (code) DO NOTHING;

CREATE TABLE scan_stages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO scan_stages (code,name) VALUES ('FETCH','FETCH') ON CONFLICT (code) DO NOTHING;

INSERT INTO scan_stages (code,name) VALUES ('SBOM_GENERATION','SBOM_GENERATION') ON CONFLICT (code) DO NOTHING;

INSERT INTO scan_stages (code,name) VALUES ('PARSE','PARSE') ON CONFLICT (code) DO NOTHING;

INSERT INTO scan_stages (code,name) VALUES ('NORMALIZATION','NORMALIZATION') ON CONFLICT (code) DO NOTHING;

INSERT INTO scan_stages (code,name) VALUES ('VULNERABILITY_LOOKUP','VULNERABILITY_LOOKUP') ON CONFLICT (code) DO NOTHING;

CREATE TABLE report_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO report_types (code,name) VALUES ('EXECUTIVE','EXECUTIVE') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('DEPENDENCY','DEPENDENCY') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('VULNERABILITY','VULNERABILITY') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('IMPACT','IMPACT') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('REPOSITORY','REPOSITORY') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('CHANGE','CHANGE') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_types (code,name) VALUES ('SBOM','SBOM') ON CONFLICT (code) DO NOTHING;

CREATE TABLE report_formats (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO report_formats (code,name) VALUES ('PDF','PDF') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_formats (code,name) VALUES ('CSV','CSV') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_formats (code,name) VALUES ('JSON','JSON') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_formats (code,name) VALUES ('CYCLONEDX','CYCLONEDX') ON CONFLICT (code) DO NOTHING;

INSERT INTO report_formats (code,name) VALUES ('SPDX','SPDX') ON CONFLICT (code) DO NOTHING;

CREATE TABLE document_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO document_types (code,name) VALUES ('PDF','PDF') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('DOCX','DOCX') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('MARKDOWN','MARKDOWN') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('TXT','TXT') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('README','README') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('OPENAPI','OPENAPI') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('ADR','ADR') ON CONFLICT (code) DO NOTHING;

INSERT INTO document_types (code,name) VALUES ('CHANGELOG','CHANGELOG') ON CONFLICT (code) DO NOTHING;

CREATE TABLE notification_channels (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    name text NOT NULL,
    UNIQUE NULLS NOT DISTINCT (code)
);

INSERT INTO notification_channels (code,name) VALUES ('IN_APP','IN_APP') ON CONFLICT (code) DO NOTHING;

INSERT INTO notification_channels (code,name) VALUES ('EMAIL','EMAIL') ON CONFLICT (code) DO NOTHING;

INSERT INTO notification_channels (code,name) VALUES ('WEBHOOK','WEBHOOK') ON CONFLICT (code) DO NOTHING;

CREATE TABLE severity_levels (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL,
    rank smallint NOT NULL CHECK (rank BETWEEN 0 AND 4),
    UNIQUE NULLS NOT DISTINCT (code),
    UNIQUE NULLS NOT DISTINCT (rank)
);

INSERT INTO severity_levels (code,rank) VALUES ('UNKNOWN',0),('LOW',1),('MEDIUM',2),('HIGH',3),('CRITICAL',4) ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('OWNER','ADMIN') OR (r.code='MEMBER' AND p.code IN ('repository.read','scan.create','scan.read','document.read','report.export'))
OR (r.code='VIEWER' AND p.code IN ('repository.read','scan.read','document.read')) ON CONFLICT (role_id,permission_id) DO NOTHING;
