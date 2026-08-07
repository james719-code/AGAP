-- 013_rescue_units.sql: Move deployable unit persistence toward the target rescue_units table.

PRAGMA foreign_keys=OFF;

CREATE TABLE IF NOT EXISTS rescue_units (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    agency_id         INTEGER NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    user_id           INTEGER REFERENCES users(id) ON DELETE SET NULL,
    target_id         TEXT    NOT NULL,
    unit_code         TEXT    NOT NULL UNIQUE,
    unit_type         TEXT    NOT NULL DEFAULT 'RESCUE_VEHICLE',
    responder_name    TEXT    NOT NULL DEFAULT '',
    responder_phone   TEXT    NOT NULL DEFAULT '',
    device_id         TEXT    NOT NULL DEFAULT '',
    status            TEXT    NOT NULL DEFAULT 'IDLE',
    current_latitude  REAL    NOT NULL DEFAULT 0,
    current_longitude REAL    NOT NULL DEFAULT 0,
    last_location_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT OR IGNORE INTO rescue_units (
    id, agency_id, user_id, target_id, unit_code, responder_name, responder_phone,
    device_id, status, current_latitude, current_longitude, last_location_at,
    created_at, updated_at
)
SELECT
    id, agency_id, user_id, target_id, unit_callsign, responder_name, responder_phone,
    device_id, status, current_lat, current_lon, last_seen_at, created_at, updated_at
FROM responder_units;

ALTER TABLE incident_dispatches RENAME TO incident_dispatches_legacy_013;

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
FROM incident_dispatches_legacy_013;

DROP TABLE incident_dispatches_legacy_013;

CREATE INDEX IF NOT EXISTS idx_rescue_units_agency ON rescue_units(agency_id);
CREATE INDEX IF NOT EXISTS idx_rescue_units_status ON rescue_units(status);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident ON incident_dispatches(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit ON incident_dispatches(unit_id);

PRAGMA foreign_keys=ON;
