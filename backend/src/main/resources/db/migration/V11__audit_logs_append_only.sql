-- Audit logs are append-only (SECURITY.md "Logs immutable/protected"). The only permitted change is
-- the automatic ON DELETE SET NULL of user_id when an account is purged after its 30-day window.
CREATE OR REPLACE FUNCTION audit_logs_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_logs is append-only';
    END IF;
    IF NEW.user_id IS NULL AND OLD.user_id IS NOT NULL
       AND NEW.id = OLD.id
       AND NEW.entity_type = OLD.entity_type
       AND NEW.entity_id = OLD.entity_id
       AND NEW.action = OLD.action
       AND NEW.old_values IS NOT DISTINCT FROM OLD.old_values
       AND NEW.new_values IS NOT DISTINCT FROM OLD.new_values
       AND NEW.ip_address IS NOT DISTINCT FROM OLD.ip_address
       AND NEW.user_agent IS NOT DISTINCT FROM OLD.user_agent
       AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_logs_no_update_or_delete ON audit_logs;
CREATE TRIGGER audit_logs_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();
