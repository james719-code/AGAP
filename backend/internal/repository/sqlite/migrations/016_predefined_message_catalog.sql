-- 016_predefined_message_catalog.sql: Align predefined messages with the target catalog schema.

PRAGMA foreign_keys=OFF;

ALTER TABLE predefined_messages RENAME TO predefined_messages_legacy_016;

CREATE TABLE predefined_messages (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    code              TEXT    NOT NULL UNIQUE,
    message_en        TEXT    NOT NULL,
    message_fil       TEXT,
    message_bikol     TEXT,
    message_type      TEXT    NOT NULL DEFAULT 'ACK' CHECK (message_type IN ('SOS', 'ACK', 'STATUS')),
    emergency_type_id INTEGER REFERENCES emergency_types(id) ON DELETE SET NULL,
    is_active         INTEGER NOT NULL DEFAULT 1,
    created_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT INTO predefined_messages
    (id, code, message_en, message_type, emergency_type_id, is_active, created_at, updated_at)
SELECT
    id,
    code,
    message_text,
    CASE
        WHEN code LIKE 'STATUS_%' THEN 'STATUS'
        ELSE 'ACK'
    END,
    emergency_type_id,
    1,
    created_at,
    created_at
FROM predefined_messages_legacy_016;

DROP TABLE predefined_messages_legacy_016;

INSERT OR IGNORE INTO predefined_messages
    (code, message_en, message_fil, message_bikol, message_type, emergency_type_id)
VALUES
    ('SOS_RECEIVED', 'SOS received. Keep your phone available for updates.', 'Natanggap ang SOS. Panatilihing available ang iyong telepono para sa updates.', 'Naresibe an SOS. I-andam an saindong telepono para sa updates.', 'ACK', NULL),
    ('UNIT_ARRIVING', 'Responder unit is arriving at your location.', 'Paparating na ang responder unit sa iyong lokasyon.', 'Maabot na an responder unit sa saindong lokasyon.', 'ACK', NULL),
    ('STATUS_SAFE', 'I am safe and accounted for.', 'Ligtas ako at nabilang na.', 'Ligtas ako asin nabilang na.', 'STATUS', NULL);

CREATE INDEX IF NOT EXISTS idx_predefined_messages_emergency_type ON predefined_messages(emergency_type_id);
CREATE INDEX IF NOT EXISTS idx_predefined_messages_active ON predefined_messages(is_active);
CREATE INDEX IF NOT EXISTS idx_predefined_messages_type ON predefined_messages(message_type);

PRAGMA foreign_keys=ON;
