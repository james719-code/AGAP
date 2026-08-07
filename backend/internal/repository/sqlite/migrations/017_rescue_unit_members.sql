-- 017_rescue_unit_members.sql: Add target membership table for responder assignments to rescue units.

CREATE TABLE IF NOT EXISTS rescue_unit_members (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    rescue_unit_id INTEGER NOT NULL REFERENCES rescue_units(id) ON DELETE CASCADE,
    responder_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_leader      INTEGER NOT NULL DEFAULT 0,
    assigned_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    removed_at     TEXT,
    UNIQUE(rescue_unit_id, responder_id, assigned_at)
);

INSERT OR IGNORE INTO rescue_unit_members (
    rescue_unit_id, responder_id, is_leader, assigned_at
)
SELECT
    id,
    user_id,
    1,
    COALESCE(created_at, strftime('%Y-%m-%dT%H:%M:%fZ','now'))
FROM rescue_units
WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rescue_unit_members_unit ON rescue_unit_members(rescue_unit_id);
CREATE INDEX IF NOT EXISTS idx_rescue_unit_members_responder ON rescue_unit_members(responder_id);
CREATE INDEX IF NOT EXISTS idx_rescue_unit_members_active ON rescue_unit_members(rescue_unit_id, removed_at);
