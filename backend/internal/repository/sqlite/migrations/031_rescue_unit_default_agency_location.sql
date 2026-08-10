-- 031_rescue_unit_default_agency_location.sql
-- Default rescue unit locations to their associated agency station coordinates when live GPS is not active (latitude/longitude = 0)

UPDATE rescue_units
SET current_latitude = (SELECT latitude FROM agencies WHERE agencies.id = rescue_units.agency_id),
    current_longitude = (SELECT longitude FROM agencies WHERE agencies.id = rescue_units.agency_id),
    updated_at = (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
WHERE (current_latitude = 0.0 OR current_latitude IS NULL)
  AND (current_longitude = 0.0 OR current_longitude IS NULL)
  AND EXISTS (SELECT 1 FROM agencies WHERE agencies.id = rescue_units.agency_id AND agencies.latitude != 0);

UPDATE responder_units
SET current_lat = (SELECT latitude FROM agencies WHERE agencies.id = responder_units.agency_id),
    current_lon = (SELECT longitude FROM agencies WHERE agencies.id = responder_units.agency_id),
    updated_at = (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
WHERE (current_lat = 0.0 OR current_lat IS NULL)
  AND (current_lon = 0.0 OR current_lon IS NULL)
  AND EXISTS (SELECT 1 FROM agencies WHERE agencies.id = responder_units.agency_id AND agencies.latitude != 0);
