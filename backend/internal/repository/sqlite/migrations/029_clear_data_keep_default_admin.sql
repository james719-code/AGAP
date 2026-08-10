-- 029_clear_data_keep_default_admin.sql: Clear all transactional and user data while preserving default admin account (admin / admin123)

PRAGMA foreign_keys = OFF;

-- Delete all operational, telemetry, and transactional data
DELETE FROM mesh_delivery_events;
DELETE FROM mesh_packets;
DELETE FROM incident_messages;
DELETE FROM incident_agencies;
DELETE FROM incident_dispatches;
DELETE FROM incident_status_history;
DELETE FROM incident_reports;
DELETE FROM incidents;
DELETE FROM notifications;
DELETE FROM audit_logs;
DELETE FROM refresh_tokens;
DELETE FROM user_emergency_contacts;
DELETE FROM rescue_unit_members;
DELETE FROM responder_profiles;

-- Reset rescue units operational assignment and set default location to their associated agency station coordinates
UPDATE rescue_units
SET user_id = NULL,
    status = 'AVAILABLE',
    current_latitude = COALESCE((SELECT latitude FROM agencies WHERE agencies.id = rescue_units.agency_id), 0.0),
    current_longitude = COALESCE((SELECT longitude FROM agencies WHERE agencies.id = rescue_units.agency_id), 0.0),
    last_location_at = (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at = (strftime('%Y-%m-%dT%H:%M:%fZ','now'));

-- Delete non-admin users, associated devices, and verification entries
DELETE FROM account_verifications WHERE user_id NOT IN (SELECT id FROM users WHERE username = 'admin' OR role = 'ADMIN');
DELETE FROM user_devices WHERE user_id NOT IN (SELECT id FROM users WHERE username = 'admin' OR role = 'ADMIN');
DELETE FROM users WHERE username != 'admin' AND role != 'ADMIN';

-- Ensure default admin account exists with 'admin123' password hash ($2a$10$.qmQtqkhx..iPxr8xTyixOO8yQCSW3eb6Sr8vz5pgqGh/SolUNXMC)
INSERT OR REPLACE INTO users (
    id, username, password_hash, role, device_id, name, phone_number, account_status, is_verified, created_at, updated_at
) VALUES (
    1, 'admin', '$2a$10$.qmQtqkhx..iPxr8xTyixOO8yQCSW3eb6Sr8vz5pgqGh/SolUNXMC', 'ADMIN', 'DEV-ADMIN-001',
    'System Administrator', '+63 917 100 0000', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Align account_status = 'APPROVED' and is_verified = 1 across all admin accounts
UPDATE users SET account_status = 'APPROVED', is_verified = 1 WHERE username = 'admin' OR role = 'ADMIN';

-- Ensure aligned account verification entry for default admin
DELETE FROM account_verifications WHERE user_id IN (SELECT id FROM users WHERE username = 'admin' OR role = 'ADMIN');
INSERT INTO account_verifications (user_id, status, submitted_at, reviewed_at, remarks)
SELECT id, 'APPROVED', (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now')), 'Default admin seed account'
FROM users WHERE username = 'admin' OR role = 'ADMIN';

PRAGMA foreign_keys = ON;
