-- 021_seed_accounts.sql: Seed initial Admin, Responder, and Citizen accounts with Office & Team assignments

-- Seed Admin Account (admin / admin123)
INSERT OR IGNORE INTO users (
    id, username, password_hash, role, device_id, name, phone_number, account_status, is_verified, created_at, updated_at
) VALUES (
    1, 'admin', '$2a$10$.qmQtqkhx..iPxr8xTyixOO8yQCSW3eb6Sr8vz5pgqGh/SolUNXMC', 'ADMIN', 'DEV-ADMIN-001',
    'System Administrator', '+63 917 100 0000', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Seed Responder Accounts (responder1..4 / responder123)
INSERT OR IGNORE INTO users (
    id, username, password_hash, role, device_id, agency_id, name, phone_number, account_status, is_verified, created_at, updated_at
) VALUES 
(
    2, 'responder1', '$2a$10$xShIJyZ6bR2gYZ6C8oWvRuw00UtXY3b9XxcW5I92jNSf5OcwJeCju', 'RESPONDER', 'DEV-ALBAY-001', 101,
    'Officer Juan Dela Cruz', '+63 917 888 1001', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
),
(
    3, 'responder2', '$2a$10$xShIJyZ6bR2gYZ6C8oWvRuw00UtXY3b9XxcW5I92jNSf5OcwJeCju', 'RESPONDER', 'DEV-ALBAY-002', 102,
    'Capt. Maria Santos', '+63 917 888 1002', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
),
(
    4, 'responder3', '$2a$10$xShIJyZ6bR2gYZ6C8oWvRuw00UtXY3b9XxcW5I92jNSf5OcwJeCju', 'RESPONDER', 'DEV-ALBAY-003', 103,
    'Sgt. Pedro Reyes', '+63 917 888 1003', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
),
(
    5, 'responder4', '$2a$10$xShIJyZ6bR2gYZ6C8oWvRuw00UtXY3b9XxcW5I92jNSf5OcwJeCju', 'RESPONDER', 'DEV-ALBAY-004', 104,
    'Medic Clara Gonzales', '+63 917 888 1004', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Seed Citizen Account (citizen1 / citizen123)
INSERT OR IGNORE INTO users (
    id, username, password_hash, role, device_id, name, phone_number, account_status, is_verified, created_at, updated_at
) VALUES (
    6, 'citizen1', '$2a$10$8VO8Tubfzy.AXLcSmm3DQu5z0AODW8k2ABTKoxXMkikO21RWrXZ.m', 'CITIZEN', 'DEV-CITIZEN-001',
    'Citizen Alex', '+63 917 555 2026', 'APPROVED', 1,
    (strftime('%Y-%m-%dT%H:%M:%fZ','now')), (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- Link rescue_units to lead user accounts
UPDATE rescue_units SET user_id = 2, responder_name = 'Officer Juan Dela Cruz', responder_phone = '+63 917 888 1001' WHERE id = 1;
UPDATE rescue_units SET user_id = 3, responder_name = 'Capt. Maria Santos', responder_phone = '+63 917 888 1002' WHERE id = 2;
UPDATE rescue_units SET user_id = 4, responder_name = 'Sgt. Pedro Reyes', responder_phone = '+63 917 888 1003' WHERE id = 3;
UPDATE rescue_units SET user_id = 5, responder_name = 'Medic Clara Gonzales', responder_phone = '+63 917 888 1004' WHERE id = 4;

-- Assign Responders to Rescue Unit Memberships
INSERT OR IGNORE INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at)
VALUES 
  (1, 2, 1, strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  (2, 3, 1, strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  (3, 4, 1, strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  (4, 5, 1, strftime('%Y-%m-%dT%H:%M:%fZ','now'));

-- Record Account Verifications for Seed Accounts
INSERT OR IGNORE INTO account_verifications (user_id, status, submitted_at, reviewed_at, remarks)
VALUES 
  (1, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account'),
  (2, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account'),
  (3, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account'),
  (4, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account'),
  (5, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account'),
  (6, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'System seed account');
