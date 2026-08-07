-- 011_incident_uuid.sql: Add the target incident identifier while preserving packet_id compatibility.

ALTER TABLE incidents ADD COLUMN incident_uuid TEXT;

UPDATE incidents
SET incident_uuid = packet_id
WHERE incident_uuid IS NULL OR incident_uuid = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_incidents_incident_uuid ON incidents(incident_uuid);
