-- 018_retire_incident_packet_id.sql: Retire incidents.packet_id after mesh_packets owns transport packet identity.

PRAGMA foreign_keys=OFF;

INSERT OR IGNORE INTO mesh_packets (
    packet_uuid, packet_type, incident_id, origin_device_identifier,
    payload_hash, created_at, server_received_at
)
SELECT
    packet_id,
    'SOS',
    id,
    sender_device_id,
    packet_hash,
    created_at,
    received_at
FROM incidents
WHERE packet_id IS NOT NULL AND packet_id != '';

ALTER TABLE incidents RENAME TO incidents_legacy_018;

CREATE TABLE incidents (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_uuid      TEXT    NOT NULL UNIQUE,
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
    emergency_type_id  INTEGER REFERENCES emergency_types(id) ON DELETE SET NULL,
    address_id         INTEGER REFERENCES addresses(id) ON DELETE SET NULL,
    false_positive     INTEGER NOT NULL DEFAULT 0,
    dispatched_unit_id INTEGER REFERENCES rescue_units(id) ON DELETE SET NULL,
    dispatched_at      TEXT,
    resolved_at        TEXT,
    description        TEXT NOT NULL DEFAULT '',
    dispatcher_notes   TEXT NOT NULL DEFAULT ''
);

INSERT INTO incidents (
    id, incident_uuid, packet_hash, target_id_raw, region_code, province_code,
    municipality_code, barangay_code, zone_code, incident_type, channel,
    delivery_path, latitude, longitude, altitude, gps_accuracy, hop_count,
    ttl, sender_device_id, status, event_timestamp, received_at, created_at,
    updated_at, citizen_id, responder_id, emergency_type_id, address_id,
    false_positive, dispatched_unit_id, dispatched_at, resolved_at,
    description, dispatcher_notes
)
SELECT
    id, incident_uuid, packet_hash, target_id_raw, region_code, province_code,
    municipality_code, barangay_code, zone_code, incident_type, channel,
    delivery_path, latitude, longitude, altitude, gps_accuracy, hop_count,
    ttl, sender_device_id, status, event_timestamp, received_at, created_at,
    updated_at, citizen_id, responder_id, emergency_type_id, address_id,
    false_positive, dispatched_unit_id, dispatched_at, resolved_at,
    description, dispatcher_notes
FROM incidents_legacy_018;

ALTER TABLE incident_dispatches RENAME TO incident_dispatches_legacy_018;

CREATE TABLE incident_dispatches (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id   INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    unit_id       INTEGER NOT NULL REFERENCES rescue_units(id) ON DELETE CASCADE,
    dispatched_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    status        TEXT    NOT NULL DEFAULT 'ASSIGNED'
        CHECK(status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE','COMPLETED','CANCELLED')),
    dispatched_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    accepted_at   TEXT,
    departed_at   TEXT,
    arrived_at    TEXT,
    completed_at  TEXT,
    UNIQUE(incident_id, unit_id)
);

INSERT INTO incident_dispatches (
    id, incident_id, unit_id, dispatched_by, status, dispatched_at,
    accepted_at, departed_at, arrived_at, completed_at
)
SELECT
    id, incident_id, unit_id, dispatched_by, status, dispatched_at,
    accepted_at, departed_at, arrived_at, completed_at
FROM incident_dispatches_legacy_018;

DROP TABLE incident_dispatches_legacy_018;

ALTER TABLE notifications RENAME TO notifications_legacy_018;

CREATE TABLE notifications (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    incident_id INTEGER REFERENCES incidents(id) ON DELETE SET NULL,
    type        TEXT    NOT NULL,
    payload     TEXT    NOT NULL DEFAULT '',
    is_read     INTEGER NOT NULL DEFAULT 0,
    sent_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    read_at     TEXT
);

INSERT INTO notifications (id, user_id, incident_id, type, payload, is_read, sent_at, read_at)
SELECT id, user_id, incident_id, type, payload, is_read, sent_at, read_at
FROM notifications_legacy_018;

DROP TABLE notifications_legacy_018;

ALTER TABLE incident_status_history RENAME TO incident_status_history_legacy_018;

CREATE TABLE incident_status_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    status      TEXT    NOT NULL CHECK(status IN ('CREATED','QUEUED','RECEIVED','ACKNOWLEDGED','DISPATCHED','EN_ROUTE','ON_SCENE','RESOLVED','CANCELLED','FALSE_ALARM')),
    changed_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
    latitude    REAL,
    longitude   REAL,
    notes       TEXT,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT INTO incident_status_history (id, incident_id, status, changed_by, latitude, longitude, notes, created_at)
SELECT id, incident_id, status, changed_by, latitude, longitude, notes, created_at
FROM incident_status_history_legacy_018;

DROP TABLE incident_status_history_legacy_018;

ALTER TABLE mesh_delivery_events RENAME TO mesh_delivery_events_legacy_018;
ALTER TABLE mesh_packets RENAME TO mesh_packets_legacy_018;

CREATE TABLE mesh_packets (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    packet_uuid              TEXT    NOT NULL UNIQUE,
    packet_type              TEXT    NOT NULL CHECK(packet_type IN ('SOS','ACK','STATUS')),
    incident_id              INTEGER REFERENCES incidents(id) ON DELETE SET NULL,
    origin_user_id           INTEGER REFERENCES users(id) ON DELETE SET NULL,
    origin_device_id         INTEGER REFERENCES user_devices(id) ON DELETE SET NULL,
    origin_device_identifier TEXT    NOT NULL DEFAULT '',
    target_agency_id         INTEGER REFERENCES agencies(id) ON DELETE SET NULL,
    payload_hash             TEXT    NOT NULL DEFAULT '',
    created_at               TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    expires_at               TEXT,
    server_received_at       TEXT
);

INSERT INTO mesh_packets (
    id, packet_uuid, packet_type, incident_id, origin_user_id, origin_device_id,
    origin_device_identifier, target_agency_id, payload_hash, created_at,
    expires_at, server_received_at
)
SELECT
    id, packet_uuid, packet_type, incident_id, origin_user_id, origin_device_id,
    origin_device_identifier, target_agency_id, payload_hash, created_at,
    expires_at, server_received_at
FROM mesh_packets_legacy_018;

DROP TABLE mesh_packets_legacy_018;

CREATE TABLE mesh_delivery_events (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    packet_id         INTEGER NOT NULL REFERENCES mesh_packets(id) ON DELETE CASCADE,
    device_id         INTEGER REFERENCES user_devices(id) ON DELETE SET NULL,
    device_identifier TEXT    NOT NULL DEFAULT '',
    event_type        TEXT    NOT NULL CHECK(event_type IN ('CREATED','RECEIVED','RELAYED','TARGET_REACHED','UPLINKED','DUPLICATE_DROPPED')),
    latitude          REAL,
    longitude         REAL,
    recorded_at       TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    synced_at         TEXT
);

INSERT INTO mesh_delivery_events (
    id, packet_id, device_id, device_identifier, event_type,
    latitude, longitude, recorded_at, synced_at
)
SELECT
    id, packet_id, device_id, device_identifier, event_type,
    latitude, longitude, recorded_at, synced_at
FROM mesh_delivery_events_legacy_018;

DROP TABLE mesh_delivery_events_legacy_018;

DROP TABLE incidents_legacy_018;

CREATE UNIQUE INDEX IF NOT EXISTS idx_incidents_incident_uuid ON incidents(incident_uuid);
CREATE INDEX IF NOT EXISTS idx_incidents_region ON incidents(region_code);
CREATE INDEX IF NOT EXISTS idx_incidents_type ON incidents(incident_type);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_received ON incidents(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_emergency_type ON incidents(emergency_type_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident ON incident_dispatches(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit ON incident_dispatches(unit_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_mesh_packets_incident ON mesh_packets(incident_id);
CREATE INDEX IF NOT EXISTS idx_mesh_packets_hash ON mesh_packets(payload_hash);
CREATE INDEX IF NOT EXISTS idx_mesh_packets_origin_device_identifier ON mesh_packets(origin_device_identifier);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_packet ON mesh_delivery_events(packet_id);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_type ON mesh_delivery_events(event_type);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_device_identifier ON mesh_delivery_events(device_identifier);

PRAGMA foreign_keys=ON;
