-- Extended Schema Migration for Normalized AGAP Entities & Geographic Hierarchy

-- 1. Geographic Hierarchy Tables
CREATE TABLE IF NOT EXISTS provinces (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT    NOT NULL UNIQUE,
    name       TEXT    NOT NULL,
    created_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS municipalities (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    province_id INTEGER NOT NULL REFERENCES provinces(id) ON DELETE CASCADE,
    code        TEXT    NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS barangays (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    municipality_id INTEGER NOT NULL REFERENCES municipalities(id) ON DELETE CASCADE,
    code            TEXT    NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS addresses (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    barangay_id    INTEGER NOT NULL REFERENCES barangays(id) ON DELETE CASCADE,
    street_address TEXT    NOT NULL DEFAULT '',
    latitude       REAL    NOT NULL DEFAULT 0,
    longitude      REAL    NOT NULL DEFAULT 0,
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- 2. Accident / Emergency Category Table
CREATE TABLE IF NOT EXISTS accident_types (
    id                            INTEGER PRIMARY KEY AUTOINCREMENT,
    codename                      TEXT    NOT NULL UNIQUE,
    name                          TEXT    NOT NULL,
    possible_messages_description TEXT    NOT NULL DEFAULT '',
    created_at                    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- 3. Normalized User Relations (Devices & Emergency Contacts)
CREATE TABLE IF NOT EXISTS user_devices (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id            INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_hardware_id TEXT    NOT NULL UNIQUE,
    created_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS user_emergency_contacts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_name TEXT    NOT NULL,
    phone_number TEXT    NOT NULL,
    relationship TEXT    NOT NULL DEFAULT '',
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

-- 4. Extend Users Table with Citizen Attributes
ALTER TABLE users ADD COLUMN name TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN birthday TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN gender TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN phone_number TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN medical_info TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN national_id TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN is_verified INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN image_url TEXT NOT NULL DEFAULT '';

-- 5. Extend Incidents / Reports Table
ALTER TABLE incidents ADD COLUMN citizen_id INTEGER REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN responder_id INTEGER REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN accident_type_id INTEGER REFERENCES accident_types(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN address_id INTEGER REFERENCES addresses(id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN false_positive INTEGER NOT NULL DEFAULT 0;

-- 6. Seed Accident Types & PSGC Geographic Data for Albay, Bicol
INSERT OR IGNORE INTO accident_types (id, codename, name, possible_messages_description) VALUES
  (1, 'MEDICAL', 'Medical Emergency', 'Urgent medical assistance required, cardiac, injury, or respiratory distress.'),
  (2, 'ACCIDENT', 'Traffic / Vehicular Accident', 'Collision, road accident, crash requiring rescue and ambulance.'),
  (3, 'FIRE', 'Fire Emergency', 'Structure fire, wildfire, or chemical fire outbreak.'),
  (4, 'DISASTER', 'Natural Disaster', 'Typhoon, flooding, landslide, volcanic eruption (Mayon), or earthquake emergency.'),
  (5, 'CRIME', 'Crime / Security Distress', 'Active assault, robbery, security threat, or civil disturbance.');

INSERT OR IGNORE INTO provinces (id, code, name) VALUES (1, '05-ALBAY', 'Albay');

INSERT OR IGNORE INTO municipalities (id, province_id, code, name) VALUES
  (1, 1, '05-ALBAY-LEGAZPI', 'Legazpi City'),
  (2, 1, '05-ALBAY-DARAGA', 'Daraga');

INSERT OR IGNORE INTO barangays (id, municipality_id, code, name) VALUES
  (1, 1, '05-ALBAY-LEGAZPI-CENTRAL', 'Central Barangay 1 (Poblacion)'),
  (2, 1, '05-ALBAY-LEGAZPI-ORANI', 'Barangay 16 (Peñaranda Park Zone)'),
  (3, 2, '05-ALBAY-DARAGA-SANROQUE', 'San Roque');

INSERT OR IGNORE INTO addresses (id, barangay_id, street_address, latitude, longitude) VALUES
  (1, 1, 'Rizal Street, Legazpi City', 13.1391, 123.7438),
  (2, 2, 'Peñaranda Park, Legazpi City', 13.1415, 123.7380),
  (3, 3, 'Station Road, Daraga', 13.1492, 123.7126);
