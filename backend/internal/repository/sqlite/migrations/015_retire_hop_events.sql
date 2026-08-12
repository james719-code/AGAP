-- 015_retire_hop_events.sql: Retire hop_events after mesh_delivery_events takes over relay analytics.

INSERT INTO mesh_packets (
    packet_uuid, packet_type, incident_id, origin_device_identifier, created_at, server_received_at
)
SELECT
    h.packet_id,
    'SOS',
    MAX(i.id),
    MIN(h.sender_device_id),
    MIN(h.recorded_at),
    MIN(h.recorded_at)
FROM hop_events h
LEFT JOIN incidents i ON i.packet_id = h.packet_id
WHERE NOT EXISTS (
    SELECT 1 FROM mesh_packets mp WHERE mp.packet_uuid = h.packet_id
)
GROUP BY h.packet_id;

INSERT INTO mesh_delivery_events (
    packet_id, device_identifier, event_type, latitude, longitude, recorded_at, synced_at
)
SELECT
    mp.id,
    h.sender_device_id,
    'RELAYED',
    h.relay_lat,
    h.relay_lon,
    h.recorded_at,
    h.recorded_at
FROM hop_events h
JOIN mesh_packets mp ON mp.packet_uuid = h.packet_id;

DROP TABLE IF EXISTS hop_events;
