-- Prevent duplicate incident messages when a mobile POST is committed but its
-- response is lost and the same queued message is retried.
ALTER TABLE incident_messages ADD COLUMN client_message_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_incident_messages_incident_sender_client_message
ON incident_messages(incident_id, sender_user_id, client_message_id)
WHERE sender_user_id IS NOT NULL
  AND client_message_id IS NOT NULL
  AND length(trim(client_message_id)) > 0;
