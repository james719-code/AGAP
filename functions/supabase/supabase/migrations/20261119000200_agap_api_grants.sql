-- The application login is created once by the project owner, outside source control.
-- Keep the test database and installations without this optional login valid.
DO $grant$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agap_api') THEN
    GRANT USAGE ON SCHEMA agap TO agap_api;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA agap TO agap_api;
    GRANT USAGE ON ALL SEQUENCES IN SCHEMA agap TO agap_api;
    ALTER DEFAULT PRIVILEGES IN SCHEMA agap GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO agap_api;
    ALTER DEFAULT PRIVILEGES IN SCHEMA agap GRANT USAGE ON SEQUENCES TO agap_api;
  END IF;
END
$grant$;
