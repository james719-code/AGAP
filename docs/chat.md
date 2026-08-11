# Website Incident Chat Cheat Sheet

The website chat is an operational conversation attached to one incident. It is not a general inbox or direct-message feature. The backend database is the shared source of truth for the website and Android application.

## User workflow

1. Open **Incident Chat**.
2. Choose an active incident from the left column.
3. Review messages in chronological order.
4. Send an operational update with Enter or the send button; Shift+Enter adds a line break.
5. New server events appear immediately over WebSocket. A 30-second poll repairs missed events after a disconnect.

Bubble rules are identical to the application workflow:

| Message | Position | Appearance |
|---|---|---|
| Current website user | Right | Teal bubble, labelled `You` |
| Another sender | Left | Neutral bubble, sender and role shown |
| Status/catalog update | Center | Compact system-style notice |

Only incidents in `RECEIVED`, `ACKNOWLEDGED`, `DISPATCHED`, `EN_ROUTE`, or `ON_SCENE` appear as active threads.

The Incident Threads type buttons are generated from the active data and include counts. Selecting `FIRE`, `CRIME`, or another type filters only the frontend thread list and automatically selects the first matching active incident.

The **Catalog Messages** panel below Incident Context lists active predefined messages. Clicking one posts its `predefined_message_id` to the selected incident using the `ONLINE` channel; it is stored and displayed as a centered `STATUS_UPDATE`.

## Endpoints

```http
GET /api/v1/incidents/{incidentId}/messages
Authorization: Bearer <token>
```

```http
POST /api/v1/incidents/{incidentId}/messages
Authorization: Bearer <token>
Content-Type: application/json

{
  "custom_message": "Unit 2 is en route.",
  "channel": "ONLINE"
}
```

`POST` is restricted to responder/admin accounts. Listing uses the existing incident-access middleware, including citizen ownership rules.

## Canonical website message

```json
{
  "id": 42,
  "incident_id": 17,
  "sender_user_id": 3,
  "custom_message": "Unit 2 is en route.",
  "channel": "ONLINE",
  "sent_at": "2026-08-11T10:30:00Z",
  "sender_name": "dispatcher",
  "sender_role": "ADMIN",
  "message_type": "CHAT"
}
```

Optional canonical fields are `receiver_user_id`, `receiver_name`, `predefined_message_id`, `predefined_code`, `message_en`, and `received_at`.

## Application compatibility

Do not remove these response aliases while the current Android application uses them:

| Canonical website field | Application alias |
|---|---|
| `sender_user_id` | `sender_id` |
| `custom_message` or catalog text | `message_content` |
| `sent_at` | `created_at` |

The backend also accepts Android's `message_content` request field as an alias for `custom_message`. Both formats create the same `incident_messages` row. `message_type` is explicit: `CHAT` for custom operational text and `STATUS_UPDATE` for catalog/status messages.

Never determine ownership from sender name or role; compare `sender_user_id` with the authenticated website user ID. Never infer a status message merely because `custom_message` is empty; use `message_type`.

## Live update contract

```json
{
  "type": "INCIDENT_MESSAGE_CREATED",
  "payload": { "...": "the complete message object" }
}
```

The website merges the payload only when its `incident_id` matches the open thread and ignores duplicate message IDs.

## Key files

- Website screen: `frontend/src/pages/ChatPage.tsx`
- Website API: `frontend/src/services/api.ts`
- Website type: `frontend/src/types/api.ts`
- Website WebSocket state: `frontend/src/context/WebSocketContext.tsx`
- Backend model: `backend/internal/domain/message.go`
- Backend request handler: `backend/internal/handler/message_handler.go`
- Backend persistence: `backend/internal/repository/sqlite/message_repo.go`

## Verification

```powershell
cd backend
$env:GOCACHE='<workspace>\\.tmp-go-cache'
go test ./internal/service ./internal/repository/sqlite ./internal/handler ./internal/websocket

cd ..\\frontend
npm.cmd run build
```
