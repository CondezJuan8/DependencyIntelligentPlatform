DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['document_processing_runs','impact_analyses','notification_deliveries','notification_delivery_attempts'] LOOP
        EXECUTE format('CREATE TRIGGER process_transition BEFORE UPDATE OF status ON %I FOR EACH ROW EXECUTE FUNCTION app.validate_process_transition()',t);
        EXECUTE format('ALTER TABLE %I ADD CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at>=started_at))',t);
    END LOOP;
END $$;
