-- 006_responder_personnel.sql: Add user_id foreign key and personnel tracking columns to responder_units

ALTER TABLE responder_units ADD COLUMN user_id INTEGER REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE responder_units ADD COLUMN responder_name TEXT NOT NULL DEFAULT '';
ALTER TABLE responder_units ADD COLUMN responder_phone TEXT NOT NULL DEFAULT '';
