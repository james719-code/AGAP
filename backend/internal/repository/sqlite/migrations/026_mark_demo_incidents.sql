-- Distinguish shipped demonstration incidents from operational telemetry.
ALTER TABLE incidents ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0;

UPDATE incidents
SET is_demo = 1
WHERE (incident_uuid = 'INC-2026-001' AND packet_hash = 'HASH-SOS-001' AND sender_device_id = 'DEV-CITIZEN-001')
   OR (incident_uuid = 'INC-2026-002' AND packet_hash = 'HASH-SOS-002' AND sender_device_id = 'DEV-CITIZEN-002')
   OR (incident_uuid = 'INC-2026-003' AND packet_hash = 'HASH-SOS-003' AND sender_device_id = 'DEV-CITIZEN-003');

CREATE INDEX IF NOT EXISTS idx_incidents_is_demo ON incidents(is_demo);
