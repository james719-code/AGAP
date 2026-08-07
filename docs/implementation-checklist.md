# AGAP Implementation Checklist

Generated from the current repository state against `AGAP_Codex_Master_Implementation_Plan.md`.

## Current Repository Structure

- `backend/`: Go API with Chi routing, SQLite repositories, embedded migrations, JWT middleware, HMAC telemetry uplink, WebSocket hub, services, and tests.
- `frontend/`: React + Vite admin web app with dashboard, login, incident, map, agency, responder, user, chat, reports, audit logs, and insights pages.
- `android/`: Kotlin Android app with Jetpack Compose, Room entities/DAOs, network clients, repositories, sync workers, location, and BLE classes.
- `scripts/`: development start/stop scripts.
- `deploy/`: service, nginx, and deployment helpers.

## Existing Backend Features

- Health endpoint at `GET /health`.
- SQLite connection and embedded migration runner with `schema_migrations`.
- Repository/service/handler layering for users, telemetry/incidents, agencies, dispatch, notifications, audit, and geography.
- JWT auth, refresh token storage, role middleware, HMAC verification for telemetry uplink, and rate limiting.
- Authenticated WebSocket hub for realtime incident, dispatch, and responder events.
- Seeded development admin, responder, and citizen accounts.

## Existing Database Schema

- Current migrations include incidents, `incident_uuid`, incident status history, incident messages, target mesh packet/event tracking, agencies, rescue units, users, geography tables, emergency types, user devices, emergency contacts, compact BLE/predefined messages, refresh tokens, incident dispatches with responder acceptance lifecycle, notifications, audit logs, responder personnel fields, account lifecycle state, and target incident lifecycle constraints.
- The schema still keeps historical migration steps for `responder_units`, `accident_types`, `hop_events`, and legacy incident `packet_id` upgrade compatibility. Runtime ingest now separates `incident_uuid` from transport `packet_uuid`, with packet transport identity owned by `mesh_packets`.

## Existing React Features

- Admin-oriented shell with login, dashboard, incident monitoring, map, user management, agency management, responder management, chat, reports, audit logs, and insights screens.
- API client and auth context exist.
- Admin user management now uses the final three backend roles, account lifecycle status actions, reviewer remarks capture, real backend JWT login, and visible account audit history.
- Incident detail dialog now loads incident status history and dispatches, showing lifecycle and dispatch timelines with controls backed by the incident and dispatch status endpoints.
- Dispatch assignment now validates that the responder unit's agency type matches the incident emergency category.
- Backend agency/responder persistence now writes deployable units to the target `rescue_units` table while preserving existing responder-unit API shapes.
- Admin reports page summarizes incident volume, active operations, BLE mesh uplinks, responder-unit capacity, incident type/barangay/agency mixes, response timing, online-vs-BLE split, false-alarm rate, BLE relay/gateway activity, responder activity, and unit deployment frequency with CSV/JSON export actions.
- Admin audit log page lists latest audit events with entity-type, entity-id, action, and date-range filters backed by `/api/v1/admin/audit-logs`.
- Admin chat page now lists incident threads and sends/loads incident-scoped operational messages through the backend incident message API.
- Admin responder roster now manages rescue-unit create/edit/deactivate/reactivate actions, live status/location updates, deployment history, and membership assignment/removal for approved responder accounts.
- Admin agency management now supports authenticated create, edit, deactivate/reactivate, valid agency types, contact information, coordinates, and operating hours.

## Existing Android Features

- Compose navigation for citizen, responder, and admin flows.
- Auth/register/login use cases, local token/profile storage, Room database module, incident/responder/agency repositories, sync workers, location repository, WebSocket client, and BLE manager/service classes.
- Android auth DTOs now consume `account_status`; role-based navigation uses only `ADMIN`, `RESPONDER`, and `CITIZEN`; incident status labels compile against the target lifecycle.
- Android registration now matches the backend pending-account response instead of expecting login tokens before admin approval.
- Android admin verification now uses the backend account lifecycle endpoint for approval, rejection, and suspension, then refreshes user lists after success.
- Android citizen profile now supports adding, editing, deleting, and setting a primary emergency contact against authenticated backend endpoints.
- Android citizen SOS now persists a local incident record for online/offline sends and uses the authenticated device id for backend citizen-targeted status updates.
- Android citizen emergency-type picker now shows the selected target agency type, and incident history/detail surfaces preserve/display that routing metadata.
- Android incident detail screen now loads dispatch assignments and lets responder/admin users advance dispatch lifecycle status.
- Android WebSocket client consumes backend realtime event envelopes and updates cached incident status for citizen-facing progress.
- Android responder dashboard now loads the signed-in responder unit's assigned dispatches and presents a direct assignment queue with dispatch lifecycle actions.
- Android responder assignment queue now includes a BLE ACK action that broadcasts a compact predefined acknowledgment packet for offline delivery.
- Android admin mobile dispatch actions now reject unsynced local incident IDs instead of falling back to an unrelated backend incident.
- Android admin mobile now includes an OSMDroid field incident map with active incident markers and the operator's current location when available.

## Existing BLE Code

- Backend has compact telemetry codec/domain tests and telemetry deduplication.
- Compact BLE upward packets now use `emergency_type_id` naming in backend domain code.
- Backend now records logical `mesh_packets` and `mesh_delivery_events` for telemetry uplinks, duplicate drops, and migrated legacy relay hop analytics.
- Android has BLE manager/service classes, a pending uplink worker, and a versioned packet codec with compact packet UUID/address fields, checksum validation, and codec unit tests.
- Android BLE codec now supports compact ACK/STATUS message codes, and the BLE manager records received acknowledgments as local mesh delivery events and acknowledged incident records.
- Android BLE SOS receive/relay now drops expired max-hop packets and only displays SOS incidents to admins or compatible responder agency types while still allowing relay behavior.
- Android gateway replay now uploads pending BLE packets with the authenticated device id and preserves compact address routing metadata through relay target ids.
- Remaining BLE work is mostly compatibility cleanup and deeper hardware/runtime validation across multiple phones.

## Completed or Partially Completed Plan Items

- Phase 1 is mostly complete: monorepo structure, Go backend, React frontend, Android project, SQLite, migrations, environment examples, health endpoint.
- Phase 2 is substantially complete: roles are constrained to `ADMIN`, `RESPONDER`, `CITIZEN`; registration/login/JWT/refresh/role middleware exist; account status has been added; pending, rejected, and suspended non-admin accounts cannot log in; admin approval/rejection/suspension APIs exist.
- Later phases have partial implementations: agencies, responders, incidents, dispatch, WebSocket, Android local persistence, and BLE foundations exist but still need deeper schema/API alignment.

## Conflicts With Master Plan

- Legacy backend migration history still contains compatibility `responder_units` steps, though deployable unit persistence and Android local caching now use the target `rescue_units` table name.
- The legacy `DISPATCHER` role has been removed from backend domain/routes, frontend types/user management, Android navigation, and fresh SQLite migrations.
- Admin web includes lifecycle controls for `PENDING`, `APPROVED`, `REJECTED`, and `SUSPENDED`.
- Incident lifecycle now uses the target statuses in backend domain logic, fresh migrations, frontend types, and Android status labels. `incident_uuid` is stored and returned by the backend as the incident identity, while packet transport identity is exposed as `packet_uuid` from `mesh_packets`.
- Emergency classification runtime reads now use `emergency_type_id`; the target migration path removes legacy `accident_type_id` columns and drops the compatibility `accident_types` table.
- BLE relay analytics runtime now uses `mesh_delivery_events`; the target migration path converts legacy `hop_events` rows into `RELAYED` delivery events and drops the compatibility table.
- BLE implementation exists before the online incident workflow is fully normalized; future work should stabilize core online workflow before expanding BLE.
- Generic DB explorer routes and the unused public DDL/CRUD handler source have been removed to comply with the master plan API surface.

## Phase 1 Checklist

- [x] Monorepo structure exists.
- [x] Go backend starts.
- [x] React frontend exists.
- [x] Android project exists.
- [x] SQLite connection exists.
- [x] Migration system exists.
- [x] Environment examples exist.
- [x] Health endpoint exists.
- [x] Verification command evidence captured: backend `go test ./...`, frontend `npm.cmd run build`, Android `:app:compileDebugKotlin`.

## Phase 2 Checklist

- [x] Replace legacy role references in frontend and Android.
- [x] Backend role constants limited to `ADMIN`, `RESPONDER`, `CITIZEN`.
- [x] Citizen/responder public registration.
- [x] Public admin self-registration blocked at handler level.
- [x] Password hashing.
- [x] JWT generation and validation.
- [x] Refresh token persistence.
- [x] Role middleware.
- [x] Account status migration.
- [x] Pending accounts denied login/protected access.
- [x] Android registration handles the backend pending-account response without creating a local authenticated session.
- [x] Add reject/suspend APIs and audit events.
- [x] Add admin verification history fields for reviewer identity and remarks.
- [x] Backend auth tests cover pending login and invalid role rejection.

## Phase 3 Checklist

- [x] Account verification actions persist review history.
- [x] Account verification actions write audit log entries.
- [x] Admin API exposes domain-specific audit logs through `/api/v1/admin/audit-logs`.
- [x] Admin user management displays account audit history.
- [x] Add richer reviewer remarks UX in the admin website.
- [x] Admin audit log screen lists latest audit events and filters by entity type, specific entity, action, and date range.
- [x] User device management endpoints exist at `GET`, `POST`, and `DELETE /api/v1/users/me/devices`.
- [x] Frontend and Android API clients include user-device response/request types and methods.
- [x] Emergency contacts support add, edit, delete, and set-primary through authenticated backend endpoints.
- [x] Backend enforces one primary emergency contact per user and defaults the first contact to primary.
- [x] Frontend and Android API clients include emergency-contact edit and set-primary methods.

## Admin Reports Checklist

- [x] Add admin website reports screen.
- [x] Reports summarize incidents by type, barangay, target agency, active operations, BLE mesh uplinks, responder units, response timing, false alarms, relay health, and deployment activity.
- [x] Reports support CSV and JSON exports from the incident dataset.

## Admin Website Checklist

- [x] Agency management create, update, deactivate/reactivate, type, contact, coordinate, and operating-hours actions call authenticated backend endpoints.
- [x] Rescue-unit management supports create, edit, deactivate/reactivate, status/location updates, member assignment/removal, current deployment, and deployment history.

## Admin Android Checklist

- [x] Admin mobile screen displays active incidents, responder-unit roster, and dispatch summary counts.
- [x] Admin mobile screen displays active incidents on a map.
- [x] Admin mobile management can approve, reject, and suspend accounts through backend lifecycle status updates.
- [x] Admin mobile screen can dispatch synced backend incidents to rescue units.
- [x] Admin mobile screen can resolve synced backend incidents.
- [x] Admin mobile dispatch/resolve actions reject unsynced local incident IDs instead of mutating a fallback incident.
- [x] Verification command evidence captured: Android `:app:compileDebugKotlin` and `:app:testDebugUnitTest`.

## Phase 6 Incident Lifecycle Checklist

- [x] Add `incident_status_history`.
- [x] Migrate backend incident statuses from `ACTIVE` to target lifecycle values.
- [x] Reject invalid status transitions in backend service logic.
- [x] Add tests for accepted and rejected incident status transitions.
- [x] Expose incident status history through `/api/v1/incidents/{id}/status-history`.
- [x] Admin website incident detail dialog displays lifecycle status history from `/api/v1/incidents/{id}/status-history`.
- [x] Align frontend and Android status enums/labels with target lifecycle.
- [x] Add `incident_uuid` column, unique index, backend domain field, and frontend/Android DTO awareness.
- [x] Separate runtime incident identity from packet identity during telemetry ingest and web UI deduplication/display.
- [x] Retire compatibility `packet_id` usage after `mesh_packets` separates transport packets from incident identity.
- [x] Add target `emergency_types` table, seed data, and incident/predefined-message forward references.
- [x] Retire compatibility `accident_types` table/columns after downstream code no longer needs fallback.
- [x] Continue dispatch workflow normalization around `EN_ROUTE`, `ON_SCENE`, and responder acceptance.

## Citizen SOS Checklist

- [x] Online SOS sends telemetry uplink packets with incident UUID, packet UUID, GPS coordinates, target id, emergency type, and authenticated sender device id.
- [x] Citizen SOS emergency selection displays the target response agency type from the synced emergency-type catalog.
- [x] Offline and forced BLE SOS saves pending packets and local incident history records for later relay/uplink.
- [x] Successful online SOS caches the backend incident response in local incident history.
- [x] Pending offline packets flush through sync push first, then fall back to per-packet uplink.
- [x] Verification command evidence captured: Android `:app:compileDebugKotlin`.

## Phase 7 Dispatch Checklist

- [x] Add target `rescue_units` table and migrate existing responder unit rows into it.
- [x] Add target `rescue_unit_members` table and migrate linked responder users as unit leaders.
- [x] Add backend repository methods/tests for adding, listing, and soft-removing rescue unit members.
- [x] Expose rescue unit member endpoints: `GET /api/v1/rescue-units/{id}/members`, `POST /api/v1/admin/rescue-units/{id}/members`, and `DELETE /api/v1/admin/rescue-units/{id}/members/{responderId}`.
- [x] Add frontend API types and client methods for rescue unit membership.
- [x] Add admin website rescue-unit membership management controls.
- [x] Expose rescue-unit deployment history through `GET /api/v1/rescue-units/{id}/dispatches` with `/responders/{id}/dispatches` compatibility for admins.
- [x] Add frontend API/client support for rescue-unit deployment history.
- [x] Add admin website rescue-unit edit/deactivate/reactivate controls and deployment-history/current-deployment dialog.
- [x] Point backend agency and dispatch repositories at `rescue_units`.
- [x] Point Android local Room cache for deployable units at `rescue_units`.
- [x] Add `/api/v1/rescue-units` route aliases and point web/Android clients at the target route name while retaining `/responders` compatibility.
- [x] Dispatch records start at `ASSIGNED` instead of skipping directly to `EN_ROUTE`.
- [x] Dispatch lifecycle supports `ASSIGNED`, `ACCEPTED`, `EN_ROUTE`, `ON_SCENE`, `COMPLETED`, and `CANCELLED`.
- [x] Dispatch lifecycle timestamps include accepted, departed, arrived, and completed times.
- [x] Backend exposes `PUT /api/v1/incidents/{id}/dispatches/{dispatchID}/status`.
- [x] Dispatch status changes drive incident lifecycle updates for `EN_ROUTE`, `ON_SCENE`, and `RESOLVED`.
- [x] Backend tests cover dispatch lifecycle progression and reject completed-dispatch regression.
- [x] Frontend and Android API clients include dispatch lifecycle types/endpoints.
- [x] Add admin web dispatch timeline controls against the dispatch status endpoint.
- [x] Add Android incident detail dispatch controls against the dispatch status endpoint.
- [x] Add a responder-focused assigned dispatch queue/list screen.
- [x] Add authenticated responder API for the signed-in responder unit's dispatch assignments.
- [x] Route emergency categories to target agency types during dispatch selection.

## Phase 8 WebSocket Realtime Checklist

- [x] WebSocket connections require a valid JWT through bearer auth or `token` query parameter.
- [x] Frontend WebSocket hook appends the stored admin token and consumes backend `{type, payload}` event envelopes.
- [x] Android WebSocket client uses the configured server WebSocket URL and consumes backend `{type, payload}` event envelopes.
- [x] Android incident repository persists `NEW_INCIDENT` payloads and applies `INCIDENT_STATUS_UPDATED` payloads to cached incidents.
- [x] Dispatch lifecycle status changes broadcast to admins/responders and the affected citizen device when the incident has a sender device id.
- [x] Verification command evidence captured: backend `go test ./...`, frontend `npm.cmd run build`, Android `:app:compileDebugKotlin`.

## Phase 5 Emergency Types Checklist

- [x] Add target `emergency_types` table and seed canonical MEDICAL, ACCIDENT, FIRE, DISASTER, and CRIME types.
- [x] Route emergency categories to target agency types during dispatch selection.
- [x] Expose authenticated `GET /api/v1/emergency-types`.
- [x] Expose admin `POST /api/v1/admin/emergency-types`.
- [x] Expose admin `PUT /api/v1/admin/emergency-types/{id}`.
- [x] Add frontend API types and client methods for emergency types.
- [x] Add admin website management screen for emergency types.
- [x] Android consumes cached dynamic emergency types for non-BLE SOS/report flows while the BLE codec remains constrained to canonical compact codes.

## Predefined Message Catalog Checklist

- [x] Add target predefined message catalog columns: `message_en`, `message_fil`, `message_bikol`, `message_type`, `is_active`, and `updated_at`.
- [x] Add target `incident_messages` table for online/BLE acknowledgments and responder/system messages.
- [x] Expose incident message history and send endpoints through `GET` and `POST /api/v1/incidents/{id}/messages`.
- [x] Add frontend API types and client methods for incident messages.
- [x] Replace the admin chat mock stream with an incident-message console backed by the incident message API.
- [x] Preserve existing seeded compact BLE messages during catalog migration.
- [x] Expose authenticated `GET /api/v1/messages/catalog`.
- [x] Expose admin `POST /api/v1/admin/messages/catalog`.
- [x] Expose admin `PUT /api/v1/admin/messages/catalog/{id}`.
- [x] Add frontend API types and client methods for the catalog endpoints.
- [x] Add admin website management screen for predefined messages.
- [x] Sync backend catalog to Android local Room cache.

## Phase 16-19 BLE Mesh Checklist

- [x] Add target `mesh_packets` table.
- [x] Add target `mesh_delivery_events` table.
- [x] Record backend uplink delivery events for accepted telemetry.
- [x] Record backend duplicate-drop delivery events without creating duplicate incidents.
- [x] Split Android BLE serialization into a versioned codec with packet UUID / compact ID, address ID, and checksum coverage.
- [x] Add Android BLE codec unit tests.
- [x] Encode/decode compact BLE ACK packets with predefined message codes.
- [x] Add responder BLE ACK broadcast action for assigned incidents.
- [x] Record received BLE ACK/STATUS packets locally as target-reached mesh delivery events.
- [x] Drop expired BLE SOS packets at the max hop count and record expiry events.
- [x] Display received BLE SOS packets only for admins or responders whose agency type matches the emergency type.
- [x] Preserve compact address routing metadata when advertising and relaying BLE SOS packets for gateway uplink.
- [x] Gateway replay uses the authenticated device id for sync-push and per-packet fallback uplinks.
- [x] Persist Android local mesh delivery events for created, received, relayed, duplicate-dropped, and uplinked packets.
- [x] Expose `POST /api/v1/mesh/events/batch` for mobile mesh delivery-event uploads.
- [x] Upload unsynced Android mesh delivery events during periodic sync.
- [x] Retire compatibility `hop_events` once mesh delivery events cover required analytics.
- [x] Verification command evidence captured: Android `:app:testDebugUnitTest`, Android `:app:compileDebugKotlin`, backend `go test ./...`, and frontend `npm.cmd run build`.

## Sync API Checklist

- [x] Expose authenticated `GET /api/v1/sync/bootstrap`.
- [x] Bootstrap response includes user profile, agencies, emergency types, predefined messages, active incidents, and responder assigned dispatches when applicable.
- [x] Add frontend API type and client method for sync bootstrap.
- [x] Add Android DTO and repository method for sync bootstrap.
- [x] Expose `POST /api/v1/sync/push` for offline telemetry and mesh delivery event replay.
- [x] Expose `GET /api/v1/sync/changes?since=<timestamp>` for incremental incident downlink with reference catalog snapshots.
- [x] Add frontend API types and client methods for sync push/changes.
- [x] Add Android DTO, API, and repository methods for sync push/changes.
- [x] Integrate Android WorkManager with `sync/push` batch uplink and `sync/changes` incremental downlink cursor.

## Master Definition of Done Audit (Section 85)

- [x] **Authentication**: Citizen & responder public registration, admin login, admin verification, role authorization (RBAC middleware), account suspension & lifecycle enforcement.
- [x] **Citizen**: Online SOS, offline BLE SOS, GPS telemetry capture, emergency type to target agency mapping, Room local persistence, status update sync, BLE ACK reception.
- [x] **Responder**: Assigned emergency queue, agency-filtered BLE emergency scanner, OSM location rendering, dispatch lifecycle actions (ACCEPT, EN_ROUTE, ON_SCENE, RESOLVED), predefined BLE ACK broadcast, store-and-forward mesh relay.
- [x] **Admin Android**: Mobile incident overview, OSM field map, user account verifications, responder roster view, dispatch actions for synced incidents.
- [x] **Admin Website**: Command center dashboard, live incident monitoring & filters, status history timeline, user lifecycle management, agency management, rescue-unit management (create/edit/deactivate/dispatches/members), emergency type catalog, predefined message catalog, analytics reports (with CSV/JSON export), audit log browser.
- [x] **Backend**: Central SQLite database, 13 embedded schema migrations, JWT auth & refresh tokens, RBAC, authenticated WebSocket hub, incident & dispatch APIs, mesh telemetry uplink & batch event APIs, incremental sync engine, idempotent offline packet handling, comprehensive audit logging.
- [x] **BLE Mesh**: Versioned packet codec with checksums & unit tests, 1-hop advertising & scanning, multi-hop store-and-forward relay, seen-packet deduplication, max-hop expiry drop, gateway uplink replay, compact offline acknowledgment broadcasts.

## Final Verification Evidence Passed

- Backend: `go test ./...` -> ALL OK (domain, handler, middleware, repository, service, platform).
- Frontend: `npm.cmd run build` -> COMPILATION PASSED (`tsc -b && vite build` completed cleanly).
- Android: `.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin` -> BUILD SUCCESSFUL (all unit tests passed, Kotlin compiled cleanly).

