-- 007_account_lifecycle.sql: Add explicit account lifecycle state for Phase 2 auth.

ALTER TABLE users ADD COLUMN account_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(account_status IN ('PENDING','APPROVED','REJECTED','SUSPENDED'));

UPDATE users
SET account_status = CASE
    WHEN is_verified = 1 THEN 'APPROVED'
    ELSE 'PENDING'
END;

CREATE TABLE IF NOT EXISTS account_verifications (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       TEXT    NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','SUSPENDED')),
    submitted_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    reviewed_by  INTEGER REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at  TEXT,
    remarks      TEXT
);

INSERT INTO account_verifications (user_id, status, submitted_at, reviewed_at)
SELECT id, account_status, created_at,
       CASE WHEN account_status = 'APPROVED' THEN updated_at ELSE NULL END
FROM users
WHERE NOT EXISTS (
    SELECT 1 FROM account_verifications av WHERE av.user_id = users.id
);
