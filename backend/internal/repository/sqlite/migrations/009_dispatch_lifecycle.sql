-- 009_dispatch_lifecycle.sql: Add responder acceptance and dispatch lifecycle timestamps.

PRAGMA foreign_keys=OFF;

ALTER TABLE incident_dispatches RENAME TO incident_dispatches_legacy_009;

CREATE TABLE incident_dispatches (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id  INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    unit_id      INTEGER NOT NULL REFERENCES responder_units(id) ON DELETE CASCADE,
    dispatched_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    status       TEXT    NOT NULL DEFAULT 'ASSIGNED'
        CHECK(status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE','COMPLETED','CANCELLED')),
    dispatched_at TEXT   NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
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
    id, incident_id, unit_id, dispatched_by,
    CASE status
        WHEN 'CLEARED' THEN 'COMPLETED'
        ELSE status
    END,
    dispatched_at,
    CASE WHEN status IN ('EN_ROUTE','ON_SCENE','CLEARED') THEN dispatched_at ELSE NULL END,
    CASE WHEN status IN ('EN_ROUTE','ON_SCENE','CLEARED') THEN dispatched_at ELSE NULL END,
    arrived_at,
    cleared_at
FROM incident_dispatches_legacy_009;

DROP TABLE incident_dispatches_legacy_009;

CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident ON incident_dispatches(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit ON incident_dispatches(unit_id);

PRAGMA foreign_keys=ON;
