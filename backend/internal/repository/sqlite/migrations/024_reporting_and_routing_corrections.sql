-- Migration 024: Emergency Type Agency Mappings, Routing & Service Area Consistency

-- 1. Seed emergency_type_agency_types mappings
-- Emergency Types: 1=MEDICAL, 2=ACCIDENT, 3=FIRE, 4=DISASTER, 5=CRIME
-- Agency Types: 1=MEDICAL, 2=FIRE, 3=POLICE, 4=RESCUE, 5=DRRMO

INSERT OR IGNORE INTO emergency_type_agency_types (emergency_type_id, agency_type_id, is_primary) VALUES
(1, 1, 1), -- MEDICAL -> MEDICAL (Primary)
(2, 4, 1), -- ACCIDENT -> RESCUE (Primary)
(2, 1, 0), -- ACCIDENT -> MEDICAL (Support)
(2, 3, 0), -- ACCIDENT -> POLICE (Support)
(3, 2, 1), -- FIRE -> FIRE (Primary)
(3, 4, 0), -- FIRE -> RESCUE (Support)
(4, 5, 1), -- DISASTER -> DRRMO (Primary)
(4, 4, 0), -- DISASTER -> RESCUE (Support)
(5, 3, 1); -- CRIME -> POLICE (Primary)

-- 2. Seed canonical & legacy Barangay codes for agency service areas
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
) b;

-- 3. Useful indexes on incident_reports
CREATE INDEX IF NOT EXISTS idx_incident_reports_uuid ON incident_reports(report_uuid);
CREATE INDEX IF NOT EXISTS idx_incident_reports_token ON incident_reports(transport_token);
CREATE INDEX IF NOT EXISTS idx_incident_reports_user ON incident_reports(reporter_user_id);
