-- 030_optimize_database_performance.sql: Add performance indexes for high-throughput query paths

CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_packet_type ON mesh_delivery_events(packet_id, event_type);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_recorded ON mesh_delivery_events(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_status_timestamp ON incidents(status, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_rescue_units_agency_status ON rescue_units(agency_id, status);
