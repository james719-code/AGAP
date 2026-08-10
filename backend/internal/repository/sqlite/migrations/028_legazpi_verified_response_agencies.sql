-- 028_legazpi_verified_response_agencies.sql
-- Replace original demonstration response-agency data with researched Legazpi
-- deployment facilities and retain source/coordinate provenance.
--
-- Verified/reviewed: 2026-08-10
--
-- IDs 101..104 are intentionally preserved because legacy seeded responder
-- units/users already reference them. New agencies are keyed by stable code,
-- never by a guessed numeric ID, so this migration cannot overwrite an agency
-- created by a real deployment before this migration is applied.

CREATE TABLE IF NOT EXISTS agency_location_sources (
    agency_id             INTEGER PRIMARY KEY REFERENCES agencies(id) ON DELETE CASCADE,
    official_address      TEXT NOT NULL DEFAULT '',
    official_source_name  TEXT NOT NULL DEFAULT '',
    official_source_url   TEXT NOT NULL DEFAULT '',
    coordinate_source     TEXT NOT NULL DEFAULT '',
    coordinate_source_url TEXT NOT NULL DEFAULT '',
    coordinate_method     TEXT NOT NULL DEFAULT '',
    coordinate_accuracy   TEXT NOT NULL DEFAULT '',
    verified_on           TEXT NOT NULL,
    notes                 TEXT NOT NULL DEFAULT ''
);

-- Recreate legacy IDs only when missing. UPDATE statements below are the
-- authoritative values when those historical rows already exist.
INSERT OR IGNORE INTO agencies (
    id, code, name, type, region_code, province_code, muni_code, barangay_code,
    contact_number, latitude, longitude, is_active, operating_hours
) VALUES
    (101, 'CDRRMO-LEG', 'Legazpi City Disaster Risk Reduction and Management Office', 'DRRMO', '05', 'ALBAY', 'LEGAZPI', 'BRGY_07_BANO', '09399747001', 13.139900, 123.733700, 1, ''),
    (102, 'BFP-LEGAZPI', 'Bureau of Fire Protection - Legazpi City Fire Station', 'FIRE', '05', 'ALBAY', 'LEGAZPI', 'LEGAZPI_PORT', '09171859984', 13.1414625, 123.759953125, 1, ''),
    (103, 'PNP-LEG-CPS', 'Legazpi City Police Station', 'POLICE', '05', 'ALBAY', 'LEGAZPI', 'LEGAZPI_PORT', '', 13.145920, 123.744750, 1, ''),
    (104, 'RED-CROSS-ALBAY', 'Philippine Red Cross Albay-Legazpi City Chapter', 'MEDICAL', '05', 'ALBAY', 'LEGAZPI', 'OLD_ALBAY', '09771466761', 13.139560, 123.735560, 1, '');

UPDATE agencies
SET code = 'CDRRMO-LEG',
    name = 'Legazpi City Disaster Risk Reduction and Management Office',
    type = 'DRRMO',
    region_code = '05',
    province_code = 'ALBAY',
    muni_code = 'LEGAZPI',
    barangay_code = 'BRGY_07_BANO',
    contact_number = '09399747001',
    latitude = 13.139900,
    longitude = 123.733700,
    is_active = 1,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id = 101;

UPDATE agencies
SET code = 'BFP-LEGAZPI',
    name = 'Bureau of Fire Protection - Legazpi City Fire Station',
    type = 'FIRE',
    region_code = '05',
    province_code = 'ALBAY',
    muni_code = 'LEGAZPI',
    barangay_code = 'LEGAZPI_PORT',
    contact_number = '09171859984',
    latitude = 13.1414625,
    longitude = 123.759953125,
    is_active = 1,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id = 102;

UPDATE agencies
SET code = 'PNP-LEG-CPS',
    name = 'Legazpi City Police Station',
    type = 'POLICE',
    region_code = '05',
    province_code = 'ALBAY',
    muni_code = 'LEGAZPI',
    barangay_code = 'LEGAZPI_PORT',
    -- Do not retain the original demonstration number. The migration leaves
    -- contact blank rather than presenting an unverified number as official.
    contact_number = '',
    latitude = 13.145920,
    longitude = 123.744750,
    is_active = 1,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id = 103;

UPDATE agencies
SET code = 'RED-CROSS-ALBAY',
    name = 'Philippine Red Cross Albay-Legazpi City Chapter',
    type = 'MEDICAL',
    region_code = '05',
    province_code = 'ALBAY',
    muni_code = 'LEGAZPI',
    barangay_code = 'OLD_ALBAY',
    contact_number = '09771466761',
    latitude = 13.139560,
    longitude = 123.735560,
    is_active = 1,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id = 104;

-- New hospital: upsert by stable code instead of reserving ID 105.
INSERT INTO agencies (
    code, name, type, region_code, province_code, muni_code, barangay_code,
    contact_number, latitude, longitude, is_active, operating_hours
)
SELECT
    'LCH-LEGAZPI', 'Legazpi City Hospital', 'MEDICAL', '05', 'ALBAY', 'LEGAZPI',
    'LEGAZPI_PORT', '09611787866', 13.145420, 123.744700, 1, '24/7'
WHERE NOT EXISTS (
    SELECT 1 FROM agencies WHERE code = 'LCH-LEGAZPI'
);

UPDATE agencies
SET name = 'Legazpi City Hospital',
    type = 'MEDICAL',
    region_code = '05',
    province_code = 'ALBAY',
    muni_code = 'LEGAZPI',
    barangay_code = 'LEGAZPI_PORT',
    contact_number = '09611787866',
    latitude = 13.145420,
    longitude = 123.744700,
    is_active = 1,
    operating_hours = '24/7',
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE code = 'LCH-LEGAZPI';

-- Provenance for the four preserved legacy IDs.
INSERT OR REPLACE INTO agency_location_sources (
    agency_id, official_address, official_source_name, official_source_url,
    coordinate_source, coordinate_source_url, coordinate_method,
    coordinate_accuracy, verified_on, notes
) VALUES
(
    101,
    '2F, Albay Public Market Building, Brgy. 7-Baño, Old Albay District, Legazpi City, Albay',
    'City Government of Legazpi - City Disaster Risk Reduction and Management Office',
    'https://legazpi.gov.ph/disaster-risk-reduction-and-management/',
    'Barangay 7-Baño public geographic reference',
    'https://www.philatlas.com/luzon/r05/albay/legazpi/barangay-7-bano.html',
    'ADDRESS_AREA',
    'APPROXIMATE_BARANGAY',
    '2026-08-10',
    'Official city source confirms the Old Albay office/contact. Exact office-building coordinates were not exposed by the authoritative source, so this is explicitly an approximate area point.'
),
(
    102,
    'Legazpi Blvd, Legazpi Port District, Legazpi City, Albay',
    'BFP Legazpi City Fire Station public emergency-contact listing',
    'https://www.facebook.com/lccfs.sealion.5/',
    'Mapped full Open Location Code for Legazpi City Central Fire Station',
    'https://www.google.com/maps/search/?api=1&query=7Q554QR5%2BHXP',
    'OPEN_LOCATION_CODE',
    'EXACT_MAPPED_POI',
    '2026-08-10',
    'Coordinate is the decoded center of the full Open Location Code associated with the mapped Legazpi City Central Fire Station listing.'
),
(
    103,
    'F. Imperial St, Legazpi Port District, Legazpi City, Albay',
    'Legazpi City Police Station mapped public office',
    'https://www.openstreetmap.org/node/1827443583',
    'OpenStreetMap / Mapcarta POI',
    'https://mapcarta.com/N1827443583',
    'OSM_POI',
    'EXACT_MAPPED_POI',
    '2026-08-10',
    'Publicly mapped station coordinate. Contact remains blank rather than retaining the original demonstration phone number.'
),
(
    104,
    'Gov. Locsin cor. Fr. JL Bates St., Albay, Legazpi City, Albay',
    'Philippine Red Cross - Albay-Legazpi City chapter information',
    'https://redcross.org.ph/',
    'Named building area from nearby public OSM references',
    'https://mapcarta.com/W252999061',
    'ADDRESS_AREA_FROM_NEARBY_OSM',
    'APPROXIMATE_BUILDING_AREA',
    '2026-08-10',
    'Public Red Cross information confirms the chapter/address/contact. Exact building geometry was not independently exposed in the retrieved source, so this point is marked approximate.'
);

-- Hospital provenance uses whatever numeric ID the stable code received.
INSERT OR REPLACE INTO agency_location_sources (
    agency_id, official_address, official_source_name, official_source_url,
    coordinate_source, coordinate_source_url, coordinate_method,
    coordinate_accuracy, verified_on, notes
)
SELECT
    id,
    'Terminal Road 3, Legazpi Port District, Legazpi City, Albay',
    'City Government of Legazpi - Legazpi City Hospital',
    'https://legazpi.gov.ph/hospital/',
    'OpenStreetMap / Mapcarta Legazpi City Hospital',
    'https://mapcarta.com/W1005777914',
    'OSM_POI',
    'EXACT_MAPPED_POI',
    '2026-08-10',
    'Official city information confirms the hospital/contact; the mapped OSM POI supplies the coordinate.'
FROM agencies
WHERE code = 'LCH-LEGAZPI';

-- Keep DB compatibility data aligned with dispatch_service.go. CDRRMO can
-- support accidents and fires in addition to being primary for disasters.
INSERT OR IGNORE INTO emergency_type_agency_types (emergency_type_id, agency_type_id, is_primary)
VALUES
    (2, 5, 0), -- ACCIDENT -> DRRMO support
    (3, 5, 0); -- FIRE -> DRRMO support

-- Cover the current Legazpi service-area code sets used by older and newer
-- clients. Use stable agency code for the newly inserted hospital.
INSERT OR IGNORE INTO agency_service_areas (agency_id, barangay_code, city_municipality, is_primary)
SELECT a.id, b.code, 'LEGAZPI CITY', 1
FROM agencies a
CROSS JOIN (
    SELECT 'PH-ALB-LEG-001' AS code UNION ALL
    SELECT 'PH-ALB-LEG-002' UNION ALL
    SELECT 'PH-ALB-LEG-003' UNION ALL
    SELECT 'PH-ALB-LEG-004' UNION ALL
    SELECT 'PH-ALB-LEG-005' UNION ALL
    SELECT 'PH-ALB-LEG-006' UNION ALL
    SELECT 'PH-ALB-LEG-007' UNION ALL
    SELECT 'PH-ALB-LEG-008' UNION ALL
    SELECT 'PH-ALB-LEG-009' UNION ALL
    SELECT 'PH-ALB-LEG-010' UNION ALL
    SELECT '050506001' UNION ALL
    SELECT '050506002' UNION ALL
    SELECT '050506003' UNION ALL
    SELECT '050506004' UNION ALL
    SELECT '050506005' UNION ALL
    SELECT '050506006' UNION ALL
    SELECT '050506007' UNION ALL
    SELECT '050506008' UNION ALL
    SELECT '050506009' UNION ALL
    SELECT '050506010' UNION ALL
    SELECT 'BRGY_01' UNION ALL
    SELECT 'BRGY_02' UNION ALL
    SELECT 'BRGY_03' UNION ALL
    SELECT 'BRGY_04' UNION ALL
    SELECT 'BRGY_05' UNION ALL
    SELECT 'BRGY_06' UNION ALL
    SELECT 'BRGY_07' UNION ALL
    SELECT 'BRGY_08' UNION ALL
    SELECT 'BRGY_09' UNION ALL
    SELECT 'BRGY_10'
) b
WHERE a.id BETWEEN 101 AND 104
   OR a.code = 'LCH-LEGAZPI';

-- Never seed fabricated live responder positions. Existing demonstration units
-- retain identity/account linkage, but routing waits for real device GPS.
UPDATE rescue_units
SET current_latitude = 0,
    current_longitude = 0,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id BETWEEN 1 AND 4
  AND device_id IN ('DEV-ALBAY-001','DEV-ALBAY-002','DEV-ALBAY-003','DEV-ALBAY-004');

-- Keep the legacy table coherent for old admin/debug tools.
UPDATE responder_units
SET current_lat = 0,
    current_lon = 0,
    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE id BETWEEN 1 AND 4
  AND device_id IN ('DEV-ALBAY-001','DEV-ALBAY-002','DEV-ALBAY-003','DEV-ALBAY-004');
