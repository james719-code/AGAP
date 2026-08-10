-- 027_dispatch_history_reassignment.sql
-- Allow a responder unit to be assigned to the same incident again after a
-- previous assignment was COMPLETED/CANCELLED. The old table-level
-- UNIQUE(incident_id, unit_id) constraint incorrectly blocked that valid
-- lifecycle forever. Active double-booking is still prevented by the partial
-- unique index on unit_id.

PRAGMA foreign_keys=OFF;

DROP INDEX IF EXISTS idx_incident_dispatches_one_active_per_unit;
DROP INDEX IF EXISTS idx_incident_dispatches_incident;
DROP INDEX IF EXISTS idx_incident_dispatches_unit;

ALTER TABLE incident_dispatches RENAME TO incident_dispatches_legacy_027;

CREATE TABLE incident_dispatches (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id   INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    unit_id       INTEGER NOT NULL REFERENCES rescue_units(id) ON DELETE CASCADE,
    dispatched_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    status        TEXT NOT NULL DEFAULT 'ASSIGNED'
        CHECK(status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE','COMPLETED','CANCELLED')),
    dispatched_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    accepted_at   TEXT,
    departed_at   TEXT,
    arrived_at    TEXT,
    completed_at  TEXT
);

INSERT INTO incident_dispatches (
    id, incident_id, unit_id, dispatched_by, status, dispatched_at,
    accepted_at, departed_at, arrived_at, completed_at
)
SELECT
    id, incident_id, unit_id, dispatched_by, status, dispatched_at,
    accepted_at, departed_at, arrived_at, completed_at
FROM incident_dispatches_legacy_027;

DROP TABLE incident_dispatches_legacy_027;

CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident
    ON incident_dispatches(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit
    ON incident_dispatches(unit_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_incident_dispatches_one_active_per_unit
    ON incident_dispatches(unit_id)
    WHERE status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE');

PRAGMA foreign_keys=ON;
