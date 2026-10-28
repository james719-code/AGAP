-- Timeline reads should visit only the selected incidents and return events in
-- chronological order, including deterministic ordering for equal timestamps.
CREATE INDEX IF NOT EXISTS idx_incident_status_history_incident_created
    ON incident_status_history(incident_id, created_at, id);
