-- Incidents table: unique distress telemetry events
CREATE TABLE IF NOT EXISTS incidents (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    packet_id         TEXT    NOT NULL UNIQUE,
    packet_hash       TEXT    NOT NULL UNIQUE,
    target_id_raw     TEXT    NOT NULL,
    region_code       TEXT    NOT NULL,
    province_code     TEXT    NOT NULL,
    municipality_code TEXT    NOT NULL,
    barangay_code     TEXT    NOT NULL,
    zone_code         TEXT    NOT NULL,
    incident_type     TEXT    NOT NULL CHECK(incident_type IN ('CRIME','MEDICAL','ACCIDENT','DISASTER')),
    channel           TEXT    NOT NULL CHECK(channel IN ('ONLINE','BLE_MESH')),
    delivery_path     TEXT    NOT NULL,
    latitude          REAL    NOT NULL,
    longitude         REAL    NOT NULL,
    altitude          REAL    NOT NULL DEFAULT 0,
    gps_accuracy      REAL    NOT NULL DEFAULT 0,
    hop_count         INTEGER NOT NULL DEFAULT 0,
    ttl               INTEGER NOT NULL DEFAULT 10,
    sender_device_id  TEXT    NOT NULL,
    status            TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISPATCHED','RESOLVED')),
    event_timestamp   TEXT    NOT NULL,
    received_at       TEXT    NOT NULL,
    created_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_incidents_region   ON incidents(region_code);
CREATE INDEX IF NOT EXISTS idx_incidents_type     ON incidents(incident_type);
CREATE INDEX IF NOT EXISTS idx_incidents_status   ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_received ON incidents(received_at DESC);

-- Hop events: relay trajectory history
CREATE TABLE IF NOT EXISTS hop_events (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    packet_id        TEXT    NOT NULL REFERENCES incidents(packet_id) ON DELETE CASCADE,
    hop_count        INTEGER NOT NULL,
    sender_device_id TEXT    NOT NULL,
    recorded_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_hop_events_packet ON hop_events(packet_id);

-- Agencies (Target ID Registry)
CREATE TABLE IF NOT EXISTS agencies (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    code           TEXT    NOT NULL UNIQUE,
    name           TEXT    NOT NULL,
    type           TEXT    NOT NULL,
    region_code    TEXT    NOT NULL,
    province_code  TEXT    NOT NULL,
    muni_code      TEXT    NOT NULL,
    barangay_code  TEXT    NOT NULL,
    contact_number TEXT    NOT NULL DEFAULT '',
    latitude       REAL    NOT NULL DEFAULT 0,
    longitude      REAL    NOT NULL DEFAULT 0,
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Responder Units
CREATE TABLE IF NOT EXISTS responder_units (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    agency_id     INTEGER NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    target_id     TEXT    NOT NULL,
    unit_callsign TEXT    NOT NULL UNIQUE,
    device_id     TEXT    NOT NULL DEFAULT '',
    status        TEXT    NOT NULL DEFAULT 'IDLE',
    current_lat   REAL    NOT NULL DEFAULT 0,
    current_lon   REAL    NOT NULL DEFAULT 0,
    last_seen_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Users (Auth)
CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    role          TEXT    NOT NULL CHECK(role IN ('CITIZEN','RESPONDER','DISPATCHER','ADMIN')),
    device_id     TEXT    NOT NULL DEFAULT '',
    agency_id     INTEGER REFERENCES agencies(id) ON DELETE SET NULL,
    created_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
