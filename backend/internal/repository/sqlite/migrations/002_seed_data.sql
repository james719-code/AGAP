-- Initial Seed Data for AGAP GIS Database

-- Seed Agencies
INSERT OR IGNORE INTO agencies (id, code, name, type, region_code, province_code, muni_code, barangay_code, contact_number, latitude, longitude)
VALUES 
  (101, 'CDRRMO-LEG', 'Legazpi City CDRRMO Emergency Command Center', 'RESCUE', '5', 'ALBAY', 'LEGAZPI', 'CENTRAL', '+63 (52) 742-1234', 13.1391, 123.7438),
  (102, 'BFP-DARAGA', 'Daraga Bureau of Fire Protection Station', 'FIRE', '5', 'ALBAY', 'DARAGA', 'SAN_ROQUE', '+63 (52) 742-9999', 13.1492, 123.7126),
  (103, 'PNP-ALBAY', 'Albay Police Provincial Office HQ', 'POLICE', '5', 'ALBAY', 'LEGAZPI', 'BARANGAY_1', '+63 (52) 742-1177', 13.1410, 123.7375),
  (104, 'RED-CROSS-ALBAY', 'Philippine Red Cross Albay Chapter', 'MEDICAL', '5', 'ALBAY', 'LEGAZPI', 'BARANGAY_16', '+63 (52) 742-2222', 13.1435, 123.7530);

-- Seed Responder Units
INSERT OR IGNORE INTO responder_units (id, agency_id, target_id, unit_callsign, device_id, status, current_lat, current_lon)
VALUES
  (1, 101, '05-05-12-01-01::RES-UNIT-01', 'RESCUE-ALPHA-1', 'DEV-ALBAY-001', 'IDLE', 13.1398, 123.7442),
  (2, 102, '05-05-12-02-09::RES-UNIT-02', 'BFP-ENGINE-101', 'DEV-ALBAY-002', 'IDLE', 13.1485, 123.7130),
  (3, 103, '05-05-12-01-04::RES-UNIT-03', 'PNP-PATROL-402', 'DEV-ALBAY-003', 'IDLE', 13.1410, 123.7375),
  (4, 104, '05-05-12-01-12::RES-UNIT-04', 'RED-CROSS-MEDIC', 'DEV-ALBAY-004', 'IDLE', 13.1435, 123.7530);

-- Seed Initial Incidents
INSERT OR IGNORE INTO incidents (
  packet_id, packet_hash, target_id_raw, region_code, province_code, 
  municipality_code, barangay_code, zone_code, incident_type, channel, 
  delivery_path, latitude, longitude, altitude, gps_accuracy, hop_count, 
  ttl, sender_device_id, status, event_timestamp, received_at
) VALUES
  (
    'INC-2026-001', 'HASH-SOS-001', '05-05-12-01-01::Peñaranda-Park-Node', 
    '05', '05', '12', '01', '01', 'MEDICAL', 'BLE_MESH', 
    'NODE-01 -> NODE-02 -> DISPATCH', 13.1391, 123.7438, 15.0, 5.0, 2, 
    10, 'DEV-CITIZEN-001', 'DISPATCHED', strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-15 minutes'), strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-15 minutes')
  ),
  (
    'INC-2026-002', 'HASH-SOS-002', '05-05-12-01-04::Rizal-St-Intersect', 
    '05', '05', '12', '01', '04', 'ACCIDENT', 'ONLINE', 
    'DIRECT_INTERNET', 13.1415, 123.7380, 12.0, 3.0, 0, 
    10, 'DEV-CITIZEN-002', 'ACTIVE', strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-45 minutes'), strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-45 minutes')
  ),
  (
    'INC-2026-003', 'HASH-SOS-003', '05-05-12-02-09::Daraga-Station', 
    '05', '05', '12', '02', '09', 'DISASTER', 'BLE_MESH', 
    'NODE-03 -> DISPATCH', 13.1492, 123.7126, 20.0, 8.0, 1, 
    10, 'DEV-CITIZEN-003', 'RESOLVED', strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-2 hours'), strftime('%Y-%m-%dT%H:%M:%SZ', 'now', '-2 hours')
  );
