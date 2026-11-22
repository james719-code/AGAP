-- Give the application login a default schema so the Edge Function can run
-- single reads without wrapping each one in BEGIN / SET LOCAL / COMMIT.
-- The API detects this setting and falls back to SET LOCAL when it is absent.
DO $search_path$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agap_api') THEN
    ALTER ROLE agap_api SET search_path = agap, public;
  END IF;
END
$search_path$;
