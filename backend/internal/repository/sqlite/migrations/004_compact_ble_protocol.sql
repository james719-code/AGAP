-- 004_compact_ble_protocol.sql: Predefined Messages Catalog & Protocol Schema

CREATE TABLE IF NOT EXISTS predefined_messages (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    code         TEXT    NOT NULL UNIQUE,
    message_text TEXT    NOT NULL,
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT OR IGNORE INTO predefined_messages (id, code, message_text) VALUES
  (1, 'HELP_EN_ROUTE',    'Help is on the way! Stay in a safe location.'),
  (2, 'RESCUE_DISPATCHED','A rescue unit has been dispatched to your coordinates.'),
  (3, 'MEDIC_DEPLOYED',   'Medical ambulance unit deployed. Keep calm.'),
  (4, 'FIRE_RESPONSE',    'Fire Department responder team is en route.'),
  (5, 'INCIDENT_RESOLVED','Emergency incident resolved. Responders standing down.');
