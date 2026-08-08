-- 025_user_device_identity.sql: make device ownership deterministic.
-- Backfill every legacy primary device into the normalized device registry so a
-- hardware ID cannot later be attached to another account.
INSERT OR IGNORE INTO user_devices (user_id, device_hardware_id, created_at)
SELECT id, device_id, created_at
FROM users
WHERE TRIM(COALESCE(device_id, '')) <> '';

-- Primary account device identifiers must also be unique when present. Empty
-- device IDs remain allowed for accounts that have not registered hardware yet.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_device_id_unique_nonempty
ON users(device_id)
WHERE TRIM(device_id) <> '';
