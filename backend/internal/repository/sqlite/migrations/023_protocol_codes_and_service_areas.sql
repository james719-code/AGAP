-- Migration 023: Protocol Codes & Agency Service Areas

ALTER TABLE emergency_types ADD COLUMN protocol_code INTEGER DEFAULT 0;
ALTER TABLE predefined_messages ADD COLUMN protocol_code INTEGER DEFAULT 0;

UPDATE emergency_types SET protocol_code = 1 WHERE code = 'MEDICAL';
UPDATE emergency_types SET protocol_code = 2 WHERE code = 'ACCIDENT';
UPDATE emergency_types SET protocol_code = 3 WHERE code = 'FIRE';
UPDATE emergency_types SET protocol_code = 4 WHERE code = 'DISASTER';
UPDATE emergency_types SET protocol_code = 5 WHERE code = 'CRIME';

UPDATE predefined_messages SET protocol_code = 1 WHERE code = 'SOS_RECEIVED';
UPDATE predefined_messages SET protocol_code = 2 WHERE code = 'HELP_EN_ROUTE';
UPDATE predefined_messages SET protocol_code = 3 WHERE code = 'RESCUE_DISPATCHED';
UPDATE predefined_messages SET protocol_code = 4 WHERE code = 'UNIT_EN_ROUTE';
UPDATE predefined_messages SET protocol_code = 5 WHERE code = 'UNIT_ARRIVING';
UPDATE predefined_messages SET protocol_code = 6 WHERE code = 'INCIDENT_RESOLVED';
UPDATE predefined_messages SET protocol_code = 7 WHERE code = 'INCIDENT_CANCELLED';

-- Seed default agency service areas for Legazpi City
INSERT OR IGNORE INTO agency_service_areas (agency_id, barangay_code, city_municipality, is_primary)
SELECT a.id, b.code, 'LEGAZPI', 1
FROM agencies a
CROSS JOIN (
    SELECT '050506001' AS code UNION ALL
    SELECT '050506002' UNION ALL
    SELECT '050506003' UNION ALL
    SELECT '050506004' UNION ALL
    SELECT '050506005' UNION ALL
    SELECT '050506006' UNION ALL
    SELECT '050506007' UNION ALL
    SELECT '050506008' UNION ALL
    SELECT '050506009' UNION ALL
    SELECT '050506010'
) b;
