-- Cover both ownership paths used by citizen history and synchronization.
CREATE INDEX IF NOT EXISTS idx_incidents_citizen ON incidents(citizen_id);
CREATE INDEX IF NOT EXISTS idx_incident_reports_reporter_incident
    ON incident_reports(reporter_user_id, incident_id);
