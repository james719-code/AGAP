CREATE TABLE IF NOT EXISTS hosted_sessions (
    access_hash TEXT PRIMARY KEY,
    refresh_hash TEXT NOT NULL UNIQUE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_expires_at TEXT NOT NULL,
    refresh_expires_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS hosted_sessions_user ON hosted_sessions(user_id);
CREATE INDEX IF NOT EXISTS hosted_sessions_expiry ON hosted_sessions(refresh_expires_at);

CREATE TABLE IF NOT EXISTS hosted_login_attempts (
    login_key TEXT PRIMARY KEY,
    window INTEGER NOT NULL,
    count INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS hosted_login_attempts_window ON hosted_login_attempts(window);
