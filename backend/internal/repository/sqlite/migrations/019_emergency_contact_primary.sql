-- 019_emergency_contact_primary.sql: Support editing and primary emergency contacts.

ALTER TABLE user_emergency_contacts ADD COLUMN is_primary INTEGER NOT NULL DEFAULT 0;

UPDATE user_emergency_contacts
SET is_primary = 1
WHERE id IN (
    SELECT MIN(id)
    FROM user_emergency_contacts
    GROUP BY user_id
);

CREATE INDEX IF NOT EXISTS idx_user_emergency_contacts_user_primary
ON user_emergency_contacts(user_id, is_primary);
