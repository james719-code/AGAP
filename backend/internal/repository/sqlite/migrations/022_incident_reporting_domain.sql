-- Migration 022: Incident Reporting Domain & Normalization

CREATE TABLE IF NOT EXISTS agency_types (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agency_service_areas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agency_id INTEGER NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    barangay_code TEXT NOT NULL,
    city_municipality TEXT NOT NULL DEFAULT 'LEGAZPI',
    is_primary BOOLEAN NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agency_id, barangay_code)
);

CREATE TABLE IF NOT EXISTS emergency_subtypes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    emergency_type_id INTEGER NOT NULL REFERENCES emergency_types(id) ON DELETE CASCADE,
    subtype_code TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    protocol_code INTEGER NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emergency_type_agency_types (
    emergency_type_id INTEGER NOT NULL REFERENCES emergency_types(id) ON DELETE CASCADE,
    agency_type_id INTEGER NOT NULL REFERENCES agency_types(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT 1,
    PRIMARY KEY(emergency_type_id, agency_type_id)
);

CREATE TABLE IF NOT EXISTS responder_profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    agency_id INTEGER REFERENCES agencies(id) ON DELETE SET NULL,
    badge_number TEXT,
    callsign TEXT,
    rank TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    report_uuid TEXT NOT NULL UNIQUE,
    transport_token TEXT NOT NULL UNIQUE,
    incident_id INTEGER REFERENCES incidents(id) ON DELETE SET NULL,
    reporter_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    reporter_device_id TEXT,
    gateway_device_id TEXT,
    submission_type TEXT NOT NULL DEFAULT 'CITIZEN_REPORT',
    subject_relation TEXT NOT NULL DEFAULT 'SELF',
    emergency_type TEXT NOT NULL,
    subtype TEXT NOT NULL DEFAULT 'GENERAL',
    description TEXT,
    people_affected INTEGER DEFAULT 1,
    immediate_danger BOOLEAN NOT NULL DEFAULT 0,
    location_source TEXT NOT NULL DEFAULT 'GPS',
    selected_barangay_code TEXT,
    latitude REAL,
    longitude REAL,
    gps_accuracy_meters REAL,
    observed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origin_channel TEXT NOT NULL DEFAULT 'ONLINE',
    detail_state TEXT NOT NULL DEFAULT 'FULL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_agencies (
    incident_id INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    agency_id INTEGER NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'PRIMARY',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(incident_id, agency_id)
);

-- Seed initial agency types & emergency subtypes
INSERT OR IGNORE INTO agency_types (code, name, description) VALUES
('MEDICAL', 'Medical & Emergency Health Services', 'Ambulance, paramedics, hospitals'),
('FIRE', 'Bureau of Fire Protection', 'Firefighting, rescue, hazardous materials'),
('POLICE', 'Philippine National Police', 'Law enforcement, security, traffic'),
('RESCUE', 'Disaster & Rescue Operations', 'DRRMO, search and rescue'),
('DRRMO', 'Disaster Risk Reduction & Management', 'City/municipal command');

INSERT OR IGNORE INTO emergency_subtypes (emergency_type_id, subtype_code, display_name, protocol_code) VALUES
(1, 'MEDICAL_GENERAL', 'General Medical Emergency', 1),
(2, 'ACCIDENT_ROAD', 'Road Vehicular Collision', 2),
(3, 'FIRE_STRUCTURE', 'Residential / Commercial Structure Fire', 3),
(4, 'DISASTER_FLOOD', 'Flood / Landslide Emergency', 4),
(5, 'CRIME_THEFT', 'Physical Theft / Robbery', 5);

-- Backfill legacy incidents into incident_reports
INSERT OR IGNORE INTO incident_reports (
    report_uuid, transport_token, incident_id, reporter_user_id,
    emergency_type, description, latitude, longitude,
    location_source, origin_channel, detail_state, created_at
)
SELECT
    COALESCE(incident_uuid, 'LEGACY-' || id),
    COALESCE(SUBSTR(packet_hash, 1, 12), printf('%012x', id)),
    id,
    citizen_id,
    incident_type,
    description,
    latitude,
    longitude,
    CASE WHEN latitude != 0 AND longitude != 0 THEN 'GPS' ELSE 'UNKNOWN' END,
    COALESCE(channel, 'ONLINE'),
    'FULL',
    created_at
FROM incidents;
