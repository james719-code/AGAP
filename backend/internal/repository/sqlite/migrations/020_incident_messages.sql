CREATE TABLE IF NOT EXISTS incident_messages (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    incident_id            INTEGER NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    sender_user_id         INTEGER REFERENCES users(id) ON DELETE SET NULL,
    receiver_user_id       INTEGER REFERENCES users(id) ON DELETE SET NULL,
    predefined_message_id  INTEGER REFERENCES predefined_messages(id) ON DELETE SET NULL,
    custom_message         TEXT,
    channel                TEXT NOT NULL CHECK(channel IN ('ONLINE','BLE')),
    sent_at                TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    received_at            TEXT,
    CHECK(predefined_message_id IS NOT NULL OR length(trim(COALESCE(custom_message, ''))) > 0)
);

CREATE INDEX IF NOT EXISTS idx_incident_messages_incident ON incident_messages(incident_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_incident_messages_sender ON incident_messages(sender_user_id);
CREATE INDEX IF NOT EXISTS idx_incident_messages_receiver ON incident_messages(receiver_user_id);
