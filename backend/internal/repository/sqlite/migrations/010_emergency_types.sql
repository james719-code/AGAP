-- 010_emergency_types.sql: Move emergency classification toward the target schema.

CREATE TABLE IF NOT EXISTS emergency_types (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    code               TEXT    NOT NULL UNIQUE,
    name               TEXT    NOT NULL,
    description        TEXT,
    default_priority   INTEGER NOT NULL DEFAULT 3,
    target_agency_type TEXT,
    is_active          INTEGER NOT NULL DEFAULT 1,
    created_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT OR IGNORE INTO emergency_types
    (id, code, name, description, default_priority, target_agency_type)
VALUES
    (1, 'MEDICAL', 'Medical Emergency', 'Urgent medical assistance required, cardiac, injury, or respiratory distress.', 1, 'MEDICAL'),
    (2, 'ACCIDENT', 'Traffic / Vehicular Accident', 'Collision, road accident, crash requiring rescue and ambulance.', 2, 'RESCUE'),
    (3, 'FIRE', 'Fire Emergency', 'Structure fire, wildfire, or chemical fire outbreak.', 1, 'FIRE'),
    (4, 'DISASTER', 'Natural Disaster', 'Typhoon, flooding, landslide, volcanic eruption, or earthquake emergency.', 1, 'DRRMO'),
    (5, 'CRIME', 'Crime / Security Distress', 'Active assault, robbery, security threat, or civil disturbance.', 1, 'POLICE');

ALTER TABLE incidents ADD COLUMN emergency_type_id INTEGER REFERENCES emergency_types(id) ON DELETE SET NULL;
ALTER TABLE predefined_messages ADD COLUMN emergency_type_id INTEGER REFERENCES emergency_types(id) ON DELETE SET NULL;

UPDATE incidents
SET emergency_type_id = (
    SELECT id FROM emergency_types WHERE emergency_types.code = incidents.incident_type
)
WHERE emergency_type_id IS NULL;

UPDATE predefined_messages
SET emergency_type_id = accident_type_id
WHERE emergency_type_id IS NULL
  AND accident_type_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM emergency_types WHERE emergency_types.id = predefined_messages.accident_type_id);

CREATE INDEX IF NOT EXISTS idx_incidents_emergency_type ON incidents(emergency_type_id);
CREATE INDEX IF NOT EXISTS idx_predefined_messages_emergency_type ON predefined_messages(emergency_type_id);
