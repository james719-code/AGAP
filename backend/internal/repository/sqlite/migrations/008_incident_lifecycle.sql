-- 008_incident_lifecycle.sql: Align incidents with the AGAP target lifecycle.

PRAGMA foreign_keys=OFF;

ALTER TABLE incidents RENAME TO incidents_legacy_008;

CREATE TABLE incidents (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    packet_id          TEXT    NOT NULL UNIQUE,
    packet_hash        TEXT    NOT NULL UNIQUE,
    target_id_raw      TEXT    NOT NULL,
    region_code        TEXT    NOT NULL,
    province_code      TEXT    NOT NULL,
    municipality_code  TEXT    NOT NULL,
    barangay_code      TEXT    NOT NULL,
    zone_code          TEXT    NOT NULL,
    incident_type      TEXT    NOT NULL CHECK(incident_type IN ('CRIME','MEDICAL','ACCIDENT','DISASTER','FIRE')),
    channel            TEXT    NOT NULL CHECK(channel IN ('ONLINE','BLE_MESH')),
    delivery_path      TEXT    NOT NULL,
    latitude           REAL    NOT NULL,
    longitude          REAL    NOT NULL,
    altitude           REAL    NOT NULL DEFAULT 0,
    gps_accuracy       REAL    NOT NULL DEFAULT 0,
    hop_count          INTEGER NOT NULL DEFAULT 0,
    ttl                INTEGER NOT NULL DEFAULT 10,
    sender_device_id   TEXT    NOT NULL,
    status             TEXT    NOT NULL DEFAULT 'RECEIVED'
        CHECK(status IN ('CREATED','QUEUED','RECEIVED','ACKNOWLEDGED','DISPATCHED','EN_ROUTE','ON_SCENE','RESOLVED','CANCELLED','FALSE_ALARM')),
    event_timestamp    TEXT    NOT NULL,
    received_at        TEXT    NOT NULL,
    created_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    citizen_id         INTEGER REFERENCES users(id) ON DELETE SET NULL,
    responder_id       INTEGER REFERENCES users(id) ON DELETE SET NULL,
    accident_type_id   INTEGER REFERENCES accident_types(id) ON DELETE SET NULL,
    address_id         INTEGER REFERENCES addresses(id) ON DELETE SET NULL,
    false_positive     INTEGER NOT NULL DEFAULT 0,
    dispatched_unit_id INTEGER REFERENCES responder_units(id) ON DELETE SET NULL,
    dispatched_at      TEXT,
    resolved_at        TEXT,
    description        TEXT NOT NULL DEFAULT '',
    dispatcher_notes   TEXT NOT NULL DEFAULT ''
);

INSERT INTO incidents (
    id, packet_id, packet_hash, target_id_raw, region_code, province_code,
    municipality_code, barangay_code, zone_code, incident_type, channel,
    delivery_path, latitude, longitude, altitude, gps_accuracy, hop_count,
    ttl, sender_device_id, status, event_timestamp, received_at, created_at,
    updated_at, citizen_id, responder_id, accident_type_id, address_id,
    false_positive, dispatched_unit_id, dispatched_at, resolved_at,
    description, dispatcher_notes
)
SELECT
    id, packet_id, packet_hash, target_id_raw, region_code, province_code,
    municipality_code, barangay_code, zone_code, incident_type, channel,
    delivery_path, latitude, longitude, altitude, gps_accuracy, hop_count,
    ttl, sender_device_id,
    CASE status
        WHEN 'ACTIVE' THEN 'RECEIVED'
        WHEN 'DISPATCHED' THEN 'DISPATCHED'
        WHEN 'RESOLVED' THEN 'RESOLVED'
        ELSE status
    END,
    event_timestamp, received_at, created_at, updated_at, citizen_id,
    responder_id, accident_type_id, address_id, false_positive,
    dispatched_unit_id, dispatched_at, resolved_at, description,
    dispatcher_notes
FROM incidents_legacy_008;

DROP TABLE incidents_legacy_008;

CREATE INDEX IF NOT EXISTS idx_incidents_region   ON incidents(region_code);
CREATE INDEX IF NOT EXISTS idx_incidents_type     ON incidents(incident_type);
CREATE INDEX IF NOT EXISTS idx_incidents_status   ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_received ON incidents(received_at DESC);

CREATE TABLE IF NOT EXISTS incident_status_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    status      TEXT    NOT NULL CHECK(status IN ('CREATED','QUEUED','RECEIVED','ACKNOWLEDGED','DISPATCHED','EN_ROUTE','ON_SCENE','RESOLVED','CANCELLED','FALSE_ALARM')),
    changed_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
    latitude    REAL,
    longitude   REAL,
    notes       TEXT,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT INTO incident_status_history (incident_id, status, notes, created_at)
SELECT id, status, 'Migrated current status', updated_at FROM incidents;

PRAGMA foreign_keys=ON;
