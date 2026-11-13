-- Immutable after application. See docs/database/README.md.

CREATE TABLE notification_rules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    name text NOT NULL,
    event_type text NOT NULL,
    is_enabled boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,name),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TABLE notification_subscriptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    rule_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,rule_id,membership_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_notification_subscriptions_fk2 ON notification_subscriptions (organization_id, membership_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    rule_id uuid NOT NULL,
    deduplication_key text NOT NULL,
    title text NOT NULL,
    summary text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,deduplication_key),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_notifications_fk1 ON notifications (organization_id, rule_id);

CREATE TABLE notification_findings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    notification_id uuid NOT NULL,
    finding_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,notification_id,finding_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, notification_id) REFERENCES notifications (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_notification_findings_fk2 ON notification_findings (organization_id, finding_id);

CREATE TABLE notification_scans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    notification_id uuid NOT NULL,
    scan_id uuid NOT NULL,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,notification_id,scan_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, notification_id) REFERENCES notifications (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, scan_id) REFERENCES scans (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_notification_scans_fk2 ON notification_scans (organization_id, scan_id);

CREATE TABLE notification_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    notification_id uuid NOT NULL,
    channel_id uuid NOT NULL,
    recipient_membership_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,notification_id,channel_id,recipient_membership_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, notification_id) REFERENCES notifications (organization_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (channel_id) REFERENCES notification_channels (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, recipient_membership_id) REFERENCES organization_memberships (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_notification_deliveries_fk2 ON notification_deliveries (channel_id);

CREATE INDEX ix_notification_deliveries_fk3 ON notification_deliveries (organization_id, recipient_membership_id);

CREATE TABLE notification_delivery_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    attempt_number integer NOT NULL CHECK (attempt_number>0),
    error_code text,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','RETRYING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE NULLS NOT DISTINCT (organization_id,delivery_id,attempt_number),
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    FOREIGN KEY (organization_id, delivery_id) REFERENCES notification_deliveries (organization_id, id) ON DELETE RESTRICT
);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON documents TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON documents TO dependency_app;

ALTER TABLE document_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_versions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_versions TO dependency_app;

ALTER TABLE document_repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_repositories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_repositories TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_repositories TO dependency_app;

ALTER TABLE document_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_components FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_components TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_components TO dependency_app;

ALTER TABLE document_component_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_component_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_component_versions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_component_versions TO dependency_app;

ALTER TABLE document_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunks FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_chunks TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_chunks TO dependency_app;

ALTER TABLE document_processing_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_processing_runs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_processing_runs TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_processing_runs TO dependency_app;

ALTER TABLE document_processing_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_processing_artifacts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_processing_artifacts TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_processing_artifacts TO dependency_app;

GRANT SELECT ON assistant_model_providers TO dependency_app;

GRANT SELECT ON assistant_model_versions TO dependency_app;

GRANT SELECT ON embedding_models TO dependency_app;

GRANT SELECT ON assistant_prompt_versions TO dependency_app;

ALTER TABLE assistant_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistant_conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assistant_conversations TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON assistant_conversations TO dependency_app;

ALTER TABLE assistant_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistant_messages FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assistant_messages TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON assistant_messages TO dependency_app;

ALTER TABLE assistant_message_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistant_message_evidences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assistant_message_evidences TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON assistant_message_evidences TO dependency_app;

ALTER TABLE deletion_request_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE deletion_request_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON deletion_request_documents TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON deletion_request_documents TO dependency_app;

ALTER TABLE impact_analyses ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_analyses FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_analyses TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_analyses TO dependency_app;

ALTER TABLE impact_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_findings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_findings TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_findings TO dependency_app;

ALTER TABLE impact_finding_repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_finding_repositories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_finding_repositories TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_finding_repositories TO dependency_app;

ALTER TABLE impact_finding_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_finding_components FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_finding_components TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_finding_components TO dependency_app;

ALTER TABLE impact_finding_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_finding_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_finding_versions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_finding_versions TO dependency_app;

ALTER TABLE impact_finding_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_finding_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_finding_documents TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_finding_documents TO dependency_app;

ALTER TABLE impact_finding_vulnerabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_finding_vulnerabilities FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_finding_vulnerabilities TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_finding_vulnerabilities TO dependency_app;

ALTER TABLE impact_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_evidences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_evidences TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_evidences TO dependency_app;

ALTER TABLE impact_recommendations ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_recommendations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_recommendations TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_recommendations TO dependency_app;

ALTER TABLE impact_recommendation_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_recommendation_evidences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_recommendation_evidences TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_recommendation_evidences TO dependency_app;

ALTER TABLE impact_test_suggestions ENABLE ROW LEVEL SECURITY;
ALTER TABLE impact_test_suggestions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON impact_test_suggestions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON impact_test_suggestions TO dependency_app;

ALTER TABLE version_change_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE version_change_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON version_change_records TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON version_change_records TO dependency_app;

ALTER TABLE change_record_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE change_record_evidences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON change_record_evidences TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON change_record_evidences TO dependency_app;

ALTER TABLE document_review_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_review_flags FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_review_flags TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON document_review_flags TO dependency_app;

ALTER TABLE report_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_schedules TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON report_schedules TO dependency_app;

ALTER TABLE report_schedule_recipients ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_schedule_recipients FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_schedule_recipients TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON report_schedule_recipients TO dependency_app;

ALTER TABLE notification_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_rules TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_rules TO dependency_app;

ALTER TABLE notification_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_subscriptions TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_subscriptions TO dependency_app;

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notifications TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notifications TO dependency_app;

ALTER TABLE notification_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_findings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_findings TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_findings TO dependency_app;

ALTER TABLE notification_scans ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_scans FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_scans TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_scans TO dependency_app;

ALTER TABLE notification_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_deliveries TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_deliveries TO dependency_app;

ALTER TABLE notification_delivery_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_delivery_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_delivery_attempts TO PUBLIC USING (organization_id=app.current_organization_id()) WITH CHECK (organization_id=app.current_organization_id());

GRANT SELECT,INSERT,UPDATE,DELETE ON notification_delivery_attempts TO dependency_app;
