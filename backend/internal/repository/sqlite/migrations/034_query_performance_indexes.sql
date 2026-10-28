-- 034_query_performance_indexes.sql: Add performance indexes for hot query paths

CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident_status ON incident_dispatches(incident_id, status);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit_status ON incident_dispatches(unit_id, status);
CREATE INDEX IF NOT EXISTS idx_incident_reports_reporter_created ON incident_reports(reporter_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_reports_uuid ON incident_reports(report_uuid);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_addresses_barangay ON addresses(barangay_id);
