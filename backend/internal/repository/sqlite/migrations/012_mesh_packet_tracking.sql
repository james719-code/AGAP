-- 012_mesh_packet_tracking.sql: Add target BLE mesh packet and delivery event tracking.

CREATE TABLE IF NOT EXISTS mesh_packets (
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

CREATE INDEX IF NOT EXISTS idx_mesh_packets_incident ON mesh_packets(incident_id);
CREATE INDEX IF NOT EXISTS idx_mesh_packets_hash ON mesh_packets(payload_hash);
CREATE INDEX IF NOT EXISTS idx_mesh_packets_origin_device_identifier ON mesh_packets(origin_device_identifier);

CREATE TABLE IF NOT EXISTS mesh_delivery_events (
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

CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_packet ON mesh_delivery_events(packet_id);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_type ON mesh_delivery_events(event_type);
CREATE INDEX IF NOT EXISTS idx_mesh_delivery_events_device_identifier ON mesh_delivery_events(device_identifier);
