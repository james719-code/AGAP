-- 005_schema_enhancements.sql: Implement DB Gap Analysis fixes & new entities

-- 1. Incidents enhancements
ALTER TABLE incidents ADD COLUMN dispatched_unit_id INTEGER REFERENCES responder_units(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN dispatched_at TEXT;
ALTER TABLE incidents ADD COLUMN resolved_at TEXT;
ALTER TABLE incidents ADD COLUMN description TEXT NOT NULL DEFAULT '';
ALTER TABLE incidents ADD COLUMN dispatcher_notes TEXT NOT NULL DEFAULT '';

-- 2. Users enhancements
ALTER TABLE users ADD COLUMN address_id INTEGER REFERENCES addresses(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN last_login_at TEXT;

-- 3. User Devices enhancements
ALTER TABLE user_devices ADD COLUMN device_name TEXT NOT NULL DEFAULT '';
ALTER TABLE user_devices ADD COLUMN platform TEXT NOT NULL DEFAULT '';
ALTER TABLE user_devices ADD COLUMN last_seen_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'));

-- 4. Agencies enhancements
ALTER TABLE agencies ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1;
ALTER TABLE agencies ADD COLUMN operating_hours TEXT NOT NULL DEFAULT '';

-- 5. Hop Events enhancements
ALTER TABLE hop_events ADD COLUMN relay_lat REAL NOT NULL DEFAULT 0;
ALTER TABLE hop_events ADD COLUMN relay_lon REAL NOT NULL DEFAULT 0;

-- 6. Predefined Messages enhancements
ALTER TABLE predefined_messages ADD COLUMN accident_type_id INTEGER REFERENCES accident_types(id) ON DELETE SET NULL;

-- 7. New Tables

-- Auth Refresh Tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT    NOT NULL UNIQUE,
    expires_at TEXT    NOT NULL,
    revoked    INTEGER NOT NULL DEFAULT 0,
    created_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- Incident Dispatches (Multi-unit response tracking)
CREATE TABLE IF NOT EXISTS incident_dispatches (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id   INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    unit_id       INTEGER NOT NULL REFERENCES responder_units(id) ON DELETE CASCADE,
    dispatched_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    status        TEXT    NOT NULL DEFAULT 'EN_ROUTE' CHECK(status IN ('EN_ROUTE','ON_SCENE','CLEARED')),
    dispatched_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    arrived_at    TEXT,
    cleared_at    TEXT,
    UNIQUE(incident_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_incident ON incident_dispatches(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_dispatches_unit ON incident_dispatches(unit_id);

-- Push / Offline Notifications history
CREATE TABLE IF NOT EXISTS notifications (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    incident_id INTEGER REFERENCES incidents(id) ON DELETE SET NULL,
    type        TEXT    NOT NULL,
    payload     TEXT    NOT NULL DEFAULT '',
    is_read     INTEGER NOT NULL DEFAULT 0,
    sent_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    read_at     TEXT
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_id    INTEGER REFERENCES users(id) ON DELETE SET NULL,
    action      TEXT    NOT NULL,
    entity_type TEXT    NOT NULL,
    entity_id   INTEGER NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
