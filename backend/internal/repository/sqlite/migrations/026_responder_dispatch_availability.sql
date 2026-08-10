-- 026_responder_dispatch_availability.sql
-- Keep responder availability consistent with active dispatch assignments and
-- prevent the same rescue unit from being assigned to multiple active incidents.

-- Normalize legacy/blank statuses so clients never receive an empty status chip.
UPDATE rescue_units
SET status = 'IDLE'
WHERE status IS NULL OR UPPER(TRIM(status)) IN ('', 'NONE', 'NULL');

-- Any unit with an active dispatch is operationally DISPATCHED unless an admin
-- explicitly marked the unit OFFLINE.
UPDATE rescue_units
SET status = 'DISPATCHED',
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE status <> 'OFFLINE'
  AND EXISTS (
      SELECT 1
      FROM incident_dispatches d
      WHERE d.unit_id = rescue_units.id
        AND d.status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE')
  );

-- Older Android builds wrote dispatch lifecycle values such as EN_ROUTE and
-- ON_SCENE into rescue_units.status. Once active assignments above have been
-- reconciled to DISPATCHED, every remaining unknown unit status is idle.
UPDATE rescue_units
SET status = 'IDLE',
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE UPPER(TRIM(status)) NOT IN ('IDLE','DISPATCHED','OFFLINE')
  AND NOT EXISTS (
      SELECT 1
      FROM incident_dispatches d
      WHERE d.unit_id = rescue_units.id
        AND d.status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE')
  );

-- Repair stale DISPATCHED flags left behind by older builds after a dispatch
-- completed/cancelled.
UPDATE rescue_units
SET status = 'IDLE',
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE status = 'DISPATCHED'
  AND NOT EXISTS (
      SELECT 1
      FROM incident_dispatches d
      WHERE d.unit_id = rescue_units.id
        AND d.status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE')
  );

-- Race-condition guard: even if two admins dispatch at the same instant,
-- SQLite permits only one active assignment for a rescue unit.
CREATE UNIQUE INDEX IF NOT EXISTS idx_incident_dispatches_one_active_per_unit
ON incident_dispatches(unit_id)
WHERE status IN ('ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE');
