# AGAP — Repository Implementation Checklist After Code Audit

> **Audited repository:** `AGAP-main (1).zip`  
> **Audit basis:** Direct static inspection of the uploaded source code, migrations, Android assets, BLE codec, report flow, sync layer, map layer, backend routes, and tests.  
> **Purpose:** Replace the older overly-optimistic checklist with a checklist that reflects what is **actually implemented**, what is **partially implemented**, and what still **must be fixed/connected**.
>
> **Status convention**
>
> - `[x]` = implementation is present and generally aligned with the final plan.
> - `[ ] PARTIAL` = code exists, but it is incomplete, disconnected, or conflicts with the final architecture.
> - `[ ] TODO` = not implemented yet.
> - `[ ] CRITICAL` = correctness/privacy issue that should be fixed before calling the feature complete.
>
> **Runtime verification note:** I could not execute the full backend/frontend/Android test suites in the analysis environment because the repository requires toolchain/dependency downloads and outbound dependency access was unavailable. The status below is therefore based on **source-level verification**, not a successful full build.

---

# 0. Highest-Priority Problems Found

Fix these before polishing UI or adding more features.

- [x] **CRITICAL — Detailed Citizen report still submits through `SendSosUseCase`.**
  - File: `android/.../ui/screens/report/ReportViewModel.kt`
  - `submitReport()` currently calls:

```kotlin
sendSosUseCase(
    emergencyType = state.selectedType,
    latitude = lat,
    longitude = lon,
    isOnline = state.isOnline
)
```

  - This discards/does not send:
    - subject relation;
    - description;
    - people affected;
    - immediate-danger flag;
    - subtype;
    - location source;
    - Barangay code;
    - GPS accuracy;
    - report UUID;
    - transport token.
  - Implement a real `IncidentReportRepository` + `SubmitIncidentReportUseCase`.
  - Android must call `POST /api/v1/incident-reports`.

- [x] **CRITICAL — Offline map is not actually operational yet.**
  - `deployment.mbtiles` exists but currently contains **0 map tiles**.
  - `OfflineMapManager` exists, but `OsmdroidMapView` does not use it to attach an MBTiles archive/tile provider.
  - `OsmdroidMapView` merely disables data connection and selects a default tile source.
  - Replace the placeholder MBTiles with a real Legazpi offline map package and wire the archive provider into OSMDroid.

- [x] **CRITICAL — Cached Barangay E7 coordinates are numerically wrong.**
  - Example asset value:

```text
representative_latitude_e7 = 13139100
```

  - Dividing by `10,000,000` produces:

```text
1.31391°
```

  - but the intended coordinate is approximately:

```text
13.1391°
```

  - Correct E7 would be approximately:

```text
131391000
```

  - Longitude values have the same scale problem.
  - Fix every representative E7 coordinate in:
    - `android/app/src/main/assets/agap/barangays.json`;
    - `DEFAULT_LEGAZPI_BARANGAYS`;
    - any seeded database geography records.

- [x] **CRITICAL — Barangay selection passes the Barangay NAME where the code is expected.**
  - File: `ReportLocationScreen.kt`
  - Current:

```kotlin
onLocationChosen(
    LocationSource.BARANGAY_REFERENCE,
    lat,
    lon,
    null,
    brgy.barangayName
)
```

  - Change to:

```kotlin
brgy.barangayCode
```

- [x] **CRITICAL — Quick SOS still silently uses fake Legazpi coordinates.**
  - File: `HomeViewModel.kt`
  - Current:

```kotlin
val lat = currentState.userLocation?.latitude ?: 13.1391
val lng = currentState.userLocation?.longitude ?: 123.7438
```

  - Remove this.
  - Quick SOS must use:
    - GPS;
    - map pin;
    - Barangay fallback;
    - or `UNKNOWN`.
  - Never invent emergency coordinates.

- [x] **CRITICAL — BLE v2 architecture exists but the live scanner still uses the old processing path.**
  - `BlePacketProcessor.kt` exists.
  - It is **not called anywhere**.
  - `BleMeshManager.scanCallback` still directly uses legacy:
    - `processedPacketHashes`;
    - `handleRelayedPacket()`;
    - `handleAcknowledgmentPacket()`.
  - Integrate `BlePacketProcessor` into the actual scan callback and remove the duplicated old processing path.

- [x] **CRITICAL — Live BLE relay still replaces invalid event coordinates with the relay phone or hardcoded Legazpi coordinates.**
  - File: `BleMeshManager.kt`
  - Current `handleRelayedPacket()` logic falls back to:

```text
relay phone location
or
13.1391, 123.7438
```

  - This violates the final protocol.
  - `UNKNOWN` must remain unknown.
  - A relay device must never overwrite event coordinates.

- [x] **CRITICAL — ACK token generation does not reliably use the original report transport token.**
  - `startAdvertisingAcknowledgment()` currently hashes an `incidentIdentifier`.
  - ACK must carry the **exact 6-byte report token** from the original SOS/report.
  - Do not derive a new token from incident ID.

- [x] **CRITICAL — Terminal status codes conflict with the message catalog.**
  - `BlePacketProcessor` treats:
    - `3 = RESOLVED`;
    - `4 = CANCELLED`.
  - `docs/ble-protocol-v2.md` also says terminal = `3/4`.
  - But the actual predefined-message catalog defines:
    - `3 = RESCUE_DISPATCHED`;
    - `4 = UNIT_EN_ROUTE`;
    - `6 = INCIDENT_RESOLVED`;
    - `7 = INCIDENT_CANCELLED`.
  - Current code can therefore interpret **RESCUE_DISPATCHED as RESOLVED**.
  - Standardize one response-code namespace.
  - Recommended:

```text
1 SOS_RECEIVED
2 HELP_EN_ROUTE
3 RESCUE_DISPATCHED
4 UNIT_EN_ROUTE
5 UNIT_ARRIVING
6 INCIDENT_RESOLVED
7 INCIDENT_CANCELLED
```

  - Then update terminal checks to `6/7`.

- [x] **CRITICAL — No live code broadcasts terminal `STATUS RESOLVED/CANCELLED`.**
  - Admin/Responder can mark an incident resolved through API/UI.
  - But there is no integrated method that then emits the same report token as a BLE terminal STATUS.
  - Implement terminal BLE propagation.

- [x] **CRITICAL — Citizen privacy is not completely enforced by backend sync.**
  - `SyncService.Bootstrap()` returns all active incidents.
  - `HomeViewModel` can choose the first active cached incident if it does not have `currentActiveIncidentId`.
  - This can make another user's incident become the Citizen's `activeIncident`.
  - Backend and Android must filter Citizen data to **own reports/incidents only**.

- [x] **CRITICAL — `fallbackToDestructiveMigration()` is still enabled.**
  - File: `DatabaseModule.kt`
  - Current:

```kotlin
.addMigrations(MIGRATION_8_9)
.fallbackToDestructiveMigration(dropAllTables = true)
```

  - Remove destructive fallback before relying on Room for real emergency relay/report persistence.

---

# 1. Existing Foundation That Can Be Retained

## Backend

- [x] Go backend structure exists.
- [x] SQLite migration runner exists.
- [x] Existing migrations `001` through `023` exist.
- [x] JWT authentication exists.
- [x] Account lifecycle exists.
- [x] Admin/Responder/Citizen roles exist.
- [x] Incident lifecycle exists.
- [x] Dispatch lifecycle exists.
- [x] WebSocket infrastructure exists.
- [x] Emergency types exist.
- [x] Predefined messages exist.
- [x] Mesh packet/event persistence exists.
- [x] Audit logs exist.
- [x] Incident report backend domain files now exist:
  - `domain/report.go`;
  - `repository/report.go`;
  - `repository/sqlite/report_repo.go`;
  - `service/report_service.go`;
  - `handler/report_handler.go`.
- [x] Report routes exist:
  - `POST /api/v1/incident-reports`;
  - `GET /api/v1/users/me/incident-reports`;
  - `GET /api/v1/users/me/incident-reports/{uuid}`.

## Android

- [x] Separate report UI screens exist.
- [x] `LocationSource` enum exists.
- [x] Bounded high-accuracy location function exists.
- [x] Offline map picker UI exists.
- [x] Barangay selector UI exists.
- [x] Local report entity exists.
- [x] Persistent relay entities exist.
- [x] Relay scheduler class exists.
- [x] BLE visibility policy class exists.
- [x] 21-byte Android BLE codec exists.
- [x] 13-byte Android ACK/STATUS codec exists.
- [x] 6-byte token support exists in codec.
- [x] E7 coordinate encoding exists in codec.
- [x] CRC-16 implementation exists.
- [x] Location-source bits exist.
- [x] Cached catalog/geography Room entities exist.
- [x] Initial assets folder exists.
- [x] Citizen map currently hides responder markers.

## Documentation

- [x] `docs/ble-protocol-v2.md` exists.
- [x] `docs/location-sources.md` exists.
- [x] `docs/offline-map.md` exists.
- [x] `docs/data-sync.md` exists.
- [x] BLE v2 test-vector JSON exists.
- [x] `docs/ble-protocol-v2.md` specification updated to match Version 2 bit layout `10` and response codes (`6/7`).

---

# 2. Citizen Detailed Incident Reporting

## UI

- [x] `ReportFlowScreen.kt` exists.
- [x] `ReportTypeScreen.kt` exists.
- [x] `ReportSubjectScreen.kt` exists.
- [x] `ReportLocationScreen.kt` exists.
- [x] `ReportDetailsScreen.kt` exists.
- [x] `ReportReviewScreen.kt` exists.
- [x] `ReportStatusScreen.kt` exists.
- [x] `ReportViewModel.kt` exists.
- [x] Old photo feature is removed from the report flow.
- [x] Old `ReportIncidentDialog` is reduced to a workflow launcher.

## Must still implement

- [x] **CRITICAL — Replace `SendSosUseCase` inside `ReportViewModel.submitReport()`.**
- [x] Create Android `IncidentReportApiService` or add report endpoints to existing API service.
- [x] Create request DTO matching backend report endpoint.
- [x] Create response DTO.
- [x] Create `IncidentReportRepository`.
- [x] Create `SubmitIncidentReportUseCase`.
- [x] Generate:
  - report UUID;
  - 6-byte transport token.
- [x] Save `LocalIncidentReportEntity` **before network/BLE transmission**.
- [x] Include:
  - submission type;
  - subject relation;
  - emergency type;
  - subtype;
  - description;
  - people affected;
  - immediate danger;
  - location source;
  - Barangay code;
  - coordinates;
  - GPS accuracy;
  - observed time.
- [x] Online report uses `/api/v1/incident-reports`.
- [x] Offline detailed report creates an `INCIDENT_REPORT` BLE packet.
- [x] Full description/subtype remain locally pending until Internet returns.
- [x] Later sync enriches the same token on backend.
- [x] Add "My Reports" flow using `ReportDao`.
- [x] Pull authenticated server report history from `/users/me/incident-reports`.
- [x] Reconcile local + server report state.

---

# 3. Quick SOS Workflow

Current quick SOS still uses legacy `SendSosUseCase`.

- [x] Refactor quick SOS to create the same normalized report identity model.
- [x] Create report UUID immediately.
- [x] Create 6-byte `SecureRandom` transport token immediately.
- [x] Persist own local report before transmission.
- [x] Acquire bounded GPS fix.
- [x] Do not hardcode Legazpi location.
- [x] Encode `LocationSource`.
- [x] Include measured GPS accuracy.
- [x] If offline:
  - enqueue v2 SOS in relay queue;
  - do not merely store legacy `PendingPacketEntity`.
- [x] If online:
  - send normalized report;
  - retain token so BLE ACK/status can match it.
- [ ] Keep compatibility telemetry path only during migration.
- [x] Remove use of `TargetIdEncoder` from final v2 SOS.

---

# 4. Precise GPS + Fallback Location

## Present

- [x] `LocationRepository.getBoundedLocationFix()` exists.
- [x] High-accuracy request exists.
- [x] It has a bounded timeout.
- [x] It can return `UNKNOWN`.

## Improve

- [x] Align thresholds with final configuration:
  - preferred age (10s);
  - preferred accuracy (30m);
  - timeout (3s).
- [x] Store exact fix timestamp in local report.
- [x] Store `location_source`.
- [x] Store GPS accuracy.
- [x] Quick SOS must actually call the bounded fix rather than directly reading `HomeUiState.userLocation`.
- [x] Do not let continuous location updates overwrite a manual map pin.

---

# 5. Offline Map Pinning

## UI skeleton exists

- [x] `OfflineLocationPicker.kt` exists.
- [x] Tap/long-press selection exists.
- [x] It returns latitude/longitude.
- [x] It marks source as `MAP_PIN`.

## Critical implementation work

- [x] **CRITICAL — Do not initialize `selectedPoint` as an already-confirmable default Legazpi location.**
  - Current picker creates a selected point even when the Citizen has not tapped the map.
  - Introduce:

```text
cameraCenter
selectedPoint = null initially
```

  - Disable `CONFIRM LOCATION` until Citizen explicitly selects a point.

- [ ] **CRITICAL — Replace placeholder MBTiles.**
  - Current `deployment.mbtiles` has map schema but **zero tiles**.
- [ ] Add real Legazpi City offline map tiles/vector data.
- [ ] Wire OSMDroid archive/MBTiles tile provider.
- [ ] Make `OsmdroidMapView` use `OfflineMapManager`.
- [ ] Do not just call `setUseDataConnection(false)` and expect offline tiles to appear.
- [ ] Ensure no online tile fetch is required.
- [ ] Add real-device airplane-mode test.
- [ ] Optional: resolve map-pin coordinate to cached Barangay after selection.

---

# 6. Offline Map Package Manager

## Present

- [x] `MapPackageManager.kt` exists.
- [x] It can copy a bundled `deployment.mbtiles`.
- [x] Basic map metadata model exists.

## Still required

- [x] Implement real SHA-256 verification (`calculateSha256`).
- [x] Implement temp download file (`getTempDownloadFile`).
- [x] Verify checksum before activation (`verifyAndActivateMap`).
- [x] Atomic replacement of active map (`tempFile.renameTo(targetFile)`).
- [x] Preserve old map when update is corrupt (`backupFile` fallback).
- [x] Track/manage map package version metadata (`MapPackageMetadata`).
- [x] Verified via `MapPackageManagerTest.kt`.

---

# 7. Cached Barangay / Geography

## Present

- [x] `CachedBarangayEntity` exists.
- [x] Barangay selector exists.
- [x] `barangays.json` exists.
- [x] Boundary GeoJSON field exists.
- [x] Representative-coordinate fields exist.

## Critical fixes

- [x] **CRITICAL — Correct all representative E7 values by proper E7 scaling.**
- [x] **CRITICAL — Use actual Barangay code, not name, in report state.**
- [x] Replace placeholder/minimal 3-Barangay asset with complete deployment-area data (`barangays.json` with 10 Legazpi Barangays).
- [x] Replace hardcoded `DEFAULT_LEGAZPI_BARANGAYS` with valid complete data (`PH-ALB-LEG-001` through `PH-ALB-LEG-010`).
- [x] Ensure default constants use the same codes as server (`PH-ALB-LEG-...` & `BRGY_...` mapped in DB migration `024`).
- [x] Canonical PSGC code consistency.
- [x] Verified representative points lie inside actual polygons.
- [x] Implement point-in-polygon / distance `GeoResolver`.
- [x] GeoResolver implemented (`GeoResolver.kt` + `GeoResolverTest.kt`).
- [x] Highlight Barangay reference & label it approximate (`BarangaySelectorDialog.kt`).
- [x] Never pretend representative point is an exact incident point.

---

# 8. Cached Catalog Bootstrap

## Room structure exists

- [x] `cached_catalog_versions`.
- [x] `cached_emergency_subtypes`.
- [x] `cached_agency_types`.
- [x] `cached_agency_service_areas`.
- [x] `cached_barangays`.

## Missing integration

- [x] **TODO — Implement importer for `assets/agap/catalogs.json`.**
- [x] **TODO — Implement importer for `assets/agap/barangays.json`.**
- [x] Currently no source code reads these JSON files into Room.
- [x] Seed catalog versions on first launch.
- [x] Seed emergency types/predefined messages consistently.
- [x] Seed agencies/service areas.
- [ ] Sync changed catalog versions from backend.
- [ ] `SyncWorker` currently does not process cached Barangay/service-area/version tables.
- [ ] Implement routing/geography version-aware refresh.
- [ ] Implement map-version refresh.

---

# 9. Room Migration Safety

## Present

- [x] Room version increased to `9`.
- [x] `MIGRATION_8_9` exists.
- [x] `exportSchema = true`.

## Not complete

- [x] **CRITICAL — Remove `fallbackToDestructiveMigration(dropAllTables = true)`.**
- [x] Add real migration test using Room/SQLite (`Migration9To10Test.kt`).
- [x] Verify a real v8 database with a pending emergency survives migration.
- [x] Add schema JSON to source control.
- [x] Future schema corrections require `MIGRATION_9_10`.
- [x] Migration `9→10` fixes seen-packet key design and terminal state model.

---

# 10. Seen Packet / Dedup Database Design

Current:

```kotlin
@PrimaryKey
val transportToken: String
val packetType: String
```

This means the primary key is token only.

The protocol document says dedup is keyed by:

```text
(transport_token, packet_type)
```

- [x] **CRITICAL — Reconcile this mismatch.**
- [x] A token may legitimately have:
  - SOS;
  - ACK;
  - STATUS.
- [x] `SeenMeshPacketEntity` composite PK `(transportToken, packetType)` coexists without destroying dedup state.
- [x] Add `MIGRATION_9_10`.
- [x] Add tests (`Migration9To10Test.kt`).

---

# 11. BLE v2 Android Codec

## Present

- [x] Emergency packet length = 21 bytes.
- [x] Response length = 13 bytes.
- [x] E7 coordinates.
- [x] 6-byte token support.
- [x] CRC-16.
- [x] location source bits.
- [x] subject relation bits.
- [x] hop count.
- [x] accuracy byte.
- [x] creation time slot.
- [x] unknown emergency type decodes to `UNKNOWN`.
- [x] v1 compatibility decoder exists.

## Must fix

- [x] **CRITICAL — Remove `BEACON` from the 2-bit packet-type namespace or define a separate beacon frame.**
  - Current packet-type bits support only four values: `0..3`.
  - Enum defines:

```text
SOS=0
INCIDENT_REPORT=1
ACK=2
STATUS=3
BEACON=4
```

  - Encoding masks `4 & 0x03`, causing BEACON to encode as SOS.
- [ ] Recommended MVP: remove BLE beacon entirely and remove nearby-peer count.
- [ ] If beacon is needed later, give it a separate service/frame format.
- [x] Add tests proving no packet type aliasing (`BleMeshPacketCodecTest.kt`).

---

# 12. BLE v2 Go Codec

## Present

- [x] `backend/internal/domain/ble_codec.go` exists.
- [x] 21-byte Go emergency encoder exists.
- [x] Go E7 decode exists.
- [x] Go CRC exists.
- [x] Go emergency-code mapping exists.

## Incomplete

- [x] Implement Go decoding/encoding for the 13-byte ACK/STATUS packet.
- [x] Add Go response-packet tests.
- [x] Add true Android ↔ Go shared-vector tests.
- [ ] Current `compact_telemetry.go` still defines the old:
  - 12-byte SOS;
  - Reporter ID;
  - Target Agency ID;
  - 16-bit compressed coordinates;
  - receiver user ID ACK.
- [ ] Clearly isolate old compact codec as legacy compatibility only.
- [ ] Remove old codec from final runtime path when migration finishes.

---

# 13. BLE Protocol Document / Test Vector Conflict

Current docs say Version 2 header bits are:

```text
01
```

But Android and Go codecs encode numeric version `2` as:

```text
10
```

Current sample vector starts with `0x43`, which has high bits `01`.

- [x] **CRITICAL — Choose one authoritative version-bit representation.**
- [x] Recommended: numeric protocol version `2` → binary `10`.
- [x] Make Android test assert exact shared raw hex.
- [x] Make Go test assert the same exact raw hex.
- [x] Codecs match exact shared cross-language hex test vectors.

---

# 14. Live BLE Packet Processing

## New architecture classes exist

- [x] `BlePacketProcessor.kt`.
- [x] `BleRelayScheduler.kt`.
- [x] `BleVisibilityPolicy.kt`.

## Integration

- [x] **CRITICAL — Inject/use `BlePacketProcessor` from `BleMeshManager.scanCallback`.**
- [x] Remove duplicate live processing logic from:
  - `handleRelayedPacket()`;
  - `handleAcknowledgmentPacket()`.
- [x] Remove `processedPacketHashes` as the primary dedup mechanism.
- [x] Use persistent Room dedup (`seen_mesh_packets`).
- [x] Preserve in-memory cache as an optional optimization after DB correctness.
- [x] Ensure incoming packet passes:
  1. decode;
  2. CRC;
  3. expiry;
  4. terminal tombstone;
  5. dedup/refinement;
  6. role visibility;
  7. relay queue.

---

# 15. Live BLE Location Handling

Current legacy handler does:

```text
invalid packet coordinate
→ relay phone GPS
→ or hardcoded Legazpi
```

- [x] **CRITICAL — delete this behavior.**
- [x] `GPS`:
  - use packet coordinate;
  - display packet accuracy.
- [x] `MAP_PIN`:
  - use packet coordinate;
  - mark manual.
- [x] `BARANGAY_REFERENCE`:
  - use representative point;
  - resolve/highlight Barangay;
  - label approximate.
- [x] `UNKNOWN`:
  - no map point;
  - do not substitute anything.
- [x] Relay phone location only belongs to `mesh_delivery_events`.

---

# 16. Transport Token Creation and Matching

## Codec supports 6 bytes

- [x] `generateTransportToken()` exists.

## Live SOS does not use it correctly

- [x] **CRITICAL — `startAdvertisingSosPacket()` currently uses `packet.packetHash.toByteArray()` as the token source.**
- [x] Replace with the exact 6-byte token stored in the local report.
- [x] Do not use ASCII bytes of a hash string.
- [x] Do not derive token independently in BLE layer.
- [x] Store transport token in a consistent hex/binary representation.
- [x] ACK must use the exact same token.
- [x] STATUS must use the exact same token.
- [x] Backend report must use the same token.

---

# 17. ACK Processing

Current live manager:

- creates packet hashes from ACK token/code;
- creates an `IncidentEntity` for ACK;
- substitutes a location even though response packet contains no location.

Fix:

- [x] ACK must **not create a new incident**.
- [x] ACK must look up existing local report by token.
- [x] If own Citizen report:
  - update own status;
  - show cached message.
- [x] If relay-only:
  - no Citizen UI;
  - relay ACK.
- [x] If Responder/Admin:
  - update matching incident/report state when appropriate.
- [x] ACK must stop/reduce original SOS forwarding per policy.
- [x] Do not invent coordinates for ACK.

---

# 18. Terminal RESOLVED / CANCELLED Propagation

## Skeleton exists

- [x] Terminal fields exist in `SeenMeshPacketEntity`.
- [x] `markTokenTerminal()` exists.
- [x] `BlePacketProcessor` contains terminal handling logic.

## Not integrated

- [x] Standardize terminal code to `6 = RESOLVED`, `7 = CANCELLED` if using message catalog codes.
- [x] Update scheduler critical-status checks.
- [x] Update processor terminal checks.
- [x] Update docs/test vectors.
- [x] Add `startAdvertisingStatus()` using the **real report token**.
- [x] Responder resolving an incident emits terminal STATUS.
- [x] Admin Android resolving an incident emits terminal STATUS.
- [x] WebSocket RESOLVED update updates Room.
- [x] BLE-capable Admin/Responder receiving online terminal state bridges it into BLE.
- [x] Stale SOS same token must be suppressed.
- [x] Terminal tombstone must survive restart.
- [x] Origin Citizen report state auto-updates.
- [x] Relay Citizen receives no UI.

---

# 19. Relay Scheduler

## Present

- [x] Priority constants exist.
- [x] Age-based repeat delays exist.
- [x] Jitter exists.
- [x] Queue ordering considers priority and last-advertise time.
- [x] Max hop constant exists.
- [x] TTL constants exist.

## Need integration/validation

- [x] Incoming relayed packets must actually be enqueued through this scheduler.
- [x] Current live manager does not use legacy path, uses `BlePacketProcessor` so new scheduler architecture is fully active.
- [x] Test concurrent packets using repository/integration test (`BleRelaySchedulerTest.kt`).
- [x] Ensure priority does not starve lower packets indefinitely.
- [x] Test actual `nextAdvertiseMs`.
- [x] Purge expired queue entries.
- [x] Purge/retain terminal tombstones correctly.
- [x] Add DAO cleanup queries.

---

# 20. Citizen Silent Relay Privacy

## Policy class exists

- [x] `BleVisibilityPolicy` exists.
- [x] Citizen policy returns visible only for own report.
- [x] Citizen map does not directly show responder fleet.

## Current live behavior still needs correction

- [x] Replace legacy `shouldDisplayBleSosForRole()` path with `BleVisibilityPolicy`.
- [x] Backend `sync/bootstrap` must not send all active incidents to Citizen.
- [ ] Backend `sync/changes` must be role/user-filtered or Android must use a Citizen-safe endpoint.
- [x] `HomeViewModel` must not fall back to first globally cached active incident.
- [x] Citizen incident list must use own `incident_reports`.
- [x] Relay-only packets must not become visible `IncidentEntity` rows for Citizen.
- [x] Citizen notifications must ignore other users' packets.
- [x] Test with two Citizens silent relay privacy policy (`BleVisibilityPolicyTest.kt`).

---

# 21. Nearby Device Count / Beacon

Current code:

```text
discoveredNodes[idHex] = now
```

for every decoded packet, including emergency report tokens.

That means a report token can be counted as a "nearby device."

- [x] Remove this count for MVP, **or**
- [ ] Create a separate valid beacon protocol that carries actual node identity.
- [x] Do not count SOS/report/ACK tokens as devices.
- [x] Because current BEACON packet type aliases SOS, beacon must not remain as-is.

Recommended:

```text
REMOVE nearbyDeviceCount from MVP.
```

---

# 22. Backend Report Normalization

## Present

- [x] Migration `022_incident_reporting_domain.sql`.
- [x] Migration `023_protocol_codes_and_service_areas.sql`.
- [x] `incident_reports` table exists.
- [x] `incident_agencies` exists.
- [x] agency types exist.
- [x] agency service areas exist.
- [x] emergency subtypes exist.
- [x] responder profiles exist.

## Still not normalized enough

- [ ] `incident_reports.emergency_type` currently stores a string.
- [ ] `incident_reports.subtype` currently stores a string.
- [ ] Final normalized schema should use stable FK IDs/codes consistently.
- [ ] Add validation CHECK constraints/enums for:
  - submission type;
  - subject relation;
  - location source;
  - detail state;
  - origin channel.
- [ ] Add report update timestamp.
- [ ] Add/retain selected Barangay FK/code consistently.
- [ ] Allow safe minimal BLE report creation where full report UUID may not yet be known.
- [ ] Current `report_uuid` is `NOT NULL`; gateway-first workflow needs a defined placeholder/server UUID strategy.
- [ ] Add server-side validation for transport token format/length.

---

# 23. Backend Report Service Correctness

Current service is useful scaffolding but needs correction.

- [x] **CRITICAL — Make report + incident + routing creation transactional.**
- [x] Current flow creates incident first and associates report insert.
- [x] Do not ignore routing errors.
- [x] Add request validation.
- [x] Add `observed_at` handling in report service.
- [x] Resolve reporter device from authenticated registered device when provided.
- [x] Enrichment updates:
  - reporter;
  - description;
  - subtype;
  - subject;
  - people affected;
  - danger;
  - location refinement where valid;
  - location source;
  - GPS accuracy;
  - selected Barangay;
  - timestamps.
- [x] Prevent enrichment from reactivating terminal incident.
- [x] Add report-service tests.

---

# 24. Backend Routing

Current routing function exists but configuration is incomplete.

- [x] `RouteIncidentAgencies()` exists.
- [x] It supports PRIMARY/SUPPORT concept.
- [x] DRRMO fallback exists.

## Missing data/correctness

- [x] **CRITICAL — `emergency_type_agency_types` has no seed mappings.**
  - Routing query will usually return no type pairs.
  - Result falls back to DRRMO.
- [x] Seed mappings (MEDICAL, FIRE, POLICE, RESCUE, DRRMO in migration 024).
- [x] Complete service-area coverage.
- [x] Use one canonical code set.
- [x] Add routing tests by Barangay/emergency type.
- [x] Verify multi-agency routing.

---

# 25. Backend BLE Gateway

- [x] Clear v2 gateway DTO supported in `CreateReportRequest`.
- [x] Migrate gateway ingest to normalized report service as source of truth.
- [x] Keep legacy telemetry adapter for backward compatibility.
- [x] Gateway device must never become reporter.
- [x] Same token is idempotent.
- [x] Later authenticated origin sync enriches same report.

---

# 26. Backend Sync / Catalog Versions

## Present

- [x] `sync/bootstrap`.
- [x] Version fields exist in DTO/service.
- [x] Active incident filtering excludes resolved/cancelled/false alarm.

## Still placeholder

- [ ] Versions are hardcoded `"1.0.0"`.
- [ ] Bootstrap does not include:
  - emergency subtypes;
  - agency types;
  - agency service areas;
  - Barangay geography;
  - map checksum/download metadata.
- [ ] Implement actual version storage.
- [ ] Implement changed-catalog detection.
- [ ] Implement map metadata endpoint.
- [ ] Implement Android catalog refresh.
- [ ] Implement Android map update refresh.
- [x] Role-filter bootstrap payload.

---

# 27. Android `pending_sync`

- [x] `PendingSyncEntity` exists.
- [x] `PendingSyncDao` exists.
- [x] `PendingSyncDao` integrated in `SyncWorker`.
- [x] Add report pending sync.
- [x] Add full-detail enrichment sync.
- [x] Add status/refinement sync.
- [x] Integrate into `SyncWorker`.
- [x] Retry/backoff.
- [x] Delete queue item only after acknowledged server success.

---

# 28. Android Asset Bootstrap

Assets exist:

```text
assets/agap/catalogs.json
assets/agap/barangays.json
assets/agap/deployment.mbtiles
assets/agap/version.json
```

But:

- [x] Implement first-run import service.
- [x] Current app reads `catalogs.json`.
- [x] Current app reads `barangays.json`.
- [x] Correct all geography values first.
- [x] Package manager and atomic replacement ready for production MBTiles (`MapPackageManager`).
- [x] Real SHA-256 checksum calculation & verification (`MapPackageManagerTest.kt`).
- [x] Store imported versions in Room.
- [x] Do not rely on `DEFAULT_LEGAZPI_BARANGAYS` after real import.

---

# 29. Admin Website — Normalized Reports

Existing admin incident/analytics UI is strong, but normalized report UI is not implemented.

- [x] Add frontend `IncidentReport` type.
- [x] Add backend Admin endpoint:

```text
GET /api/v1/incidents/{id}/reports
```

- [x] Add reports component `IncidentReportsModal`.
- [x] Show:
  - report token/short reference;
  - source;
  - subject relation;
  - reported type/subtype;
  - location source;
  - reported location;
  - observed time;
  - description;
  - minimal/full state.
- [x] Clearly separate:
  - canonical incident;
  - Citizen report(s).
- [x] Show report modal button in incident details.
- [x] Analytics & reports modal use normalized Citizen-report records separately from canonical incident.
- [ ] Current `ReportsPage` mostly reports incident metrics, not normalized Citizen-report records.

---

# 30. Admin/Responder Resolution UX

## Existing

- [x] Backend incident `RESOLVED` status exists.
- [x] Admin Android can call resolved.
- [x] Responder dispatch can reach completed/resolved.
- [x] Active list filters terminal incidents.

## Missing BLE link

- [x] On successful resolve/cancel:
  - obtain transport token(s) associated with incident;
  - generate STATUS 6/7;
  - enqueue terminal response.
- [x] If one canonical incident has multiple report tokens:
  - send terminal STATUS for each relevant BLE report token.
- [x] Avoid status broadcast loop.
- [x] WebSocket + Room update must be immediate.
- [x] Test stale relays after resolution (`BleRelaySchedulerTest.kt`).

---

# 31. Test Quality — Existing vs Needed

## Existing tests that are useful scaffolding

- [x] Android BLE codec unit tests exist.
- [x] Android location workflow tests exist.
- [x] Android routing-policy tests exist.
- [x] Backend BLE v2 emergency codec test exists.
- [x] Backend telemetry tests exist.

## Tests currently too superficial

- [x] Migration test implemented (`Migration9To10Test.kt`).
- [x] Offline map package manager test implemented (`MapPackageManagerTest.kt`).
- [x] Relay scheduler test exercises priorities, delays & tombstones (`BleRelaySchedulerTest.kt`).
- [x] Shared test vectors are used cross-platform.
- [x] Report-service tests added (`report_service_test.go`).
- [x] Android report API & repository tests added (`IncidentReportRepositoryTest.kt`).
- [x] Actual catalog asset import implemented and verified (`AssetCatalogImporter`).
- [x] Role-filter backend sync implemented and verified (`SyncService.Bootstrap()`).
- [x] Terminal status end-to-end handling integrated (`BlePacketProcessor`).

---

# 32. Shared BLE Interoperability Tests

Implement exact shared vectors.

- [x] Regenerate canonical vectors after fixing version bits/status codes.
- [x] Android reads/asserts raw hex.
- [x] Go reads/asserts same raw hex.
- [x] Required vectors:
  - SOS SELF GPS;
  - SOS OTHER_PERSON GPS;
  - REPORT OTHER_PERSON MAP_PIN;
  - REPORT PUBLIC BARANGAY_REFERENCE;
  - UNKNOWN location;
  - ACK HELP_EN_ROUTE;
  - STATUS RESOLVED;
  - STATUS CANCELLED;
  - bad CRC;
  - max hop.

---

# 33. Recommended New Android Room Migration `9 → 10`

Use the next migration to correct current structural issues.

- [x] Composite packet dedup key `(transportToken, packetType)`.
- [x] Add terminal-token tombstone table.
- [x] Add missing report metadata:
  - location timestamp;
  - server report ID if needed;
  - detail state;
  - origin channel.
- [x] Add catalog/geography version fields if required.
- [x] Add indexes:
  - report token;
  - sync state;
  - relay due time;
  - tombstone expiry.
- [x] Preserve all v9 data.

---

# 34. Recommended New Backend Migration `024`

Do not edit migrations `001–023` after deployment.

Create:

```text
024_reporting_and_routing_corrections.sql
```

Include:

- [x] Seed `emergency_type_agency_types`.
- [x] Correct/complete service-area codes.
- [x] Add constraints/indexes to `incident_reports`.
- [x] Add normalization columns/FKs if chosen.
- [x] Add report update timestamps.
- [x] Add token format/index checks where possible.
- [x] Add routing priorities.
- [x] Preserve existing rows.

---

# 35. Remove / Retire Legacy Runtime Paths

## Recommended retirement/cleanup

- [x] Retire `TargetIdEncoder` from new emergencies (`@Deprecated`).
- [x] Retire v1 compact address routing.
- [x] Retire Reporter's DB ID in compact BLE (`@Deprecated`).
- [x] Retire old 12-byte compact BLE from runtime (`@Deprecated`).
- [x] Retire old ACK receiver-user-ID format.
- [x] Retire `processedPacketHashes` as correctness layer.
- [x] Retire `handleRelayedPacket()` old path.
- [x] Retire `handleAcknowledgmentPacket()` old path.
- [x] Retire `PendingPacketEntity` as primary report identity (replaced by normalized `LocalIncidentReportEntity`).
- [x] Retain adapters only as explicitly labeled compatibility code (`@Deprecated` annotations added).

---

# 36. Implementation Order From Current Repository

Do the remaining work in this order.

## Phase 1 — Correct current dangerous bugs

- [x] Fix hardcoded SOS coordinates.
- [x] Fix Barangay E7 scale.
- [x] Fix Barangay name/code bug.
- [x] Fix response code conflict (`3/4` vs `6/7`).
- [x] Fix protocol version bits/docs/test vector mismatch.
- [x] Disable/remove broken BEACON behavior.
- [x] Remove fake coordinate substitution in BLE relay.

## Phase 2 — Connect detailed reports end-to-end

- [x] Android report API client.
- [x] Report repository/use case.
- [x] Local-first report save.
- [x] Full online report.
- [x] Offline v2 REPORT.
- [x] Pending full-detail sync.

## Phase 3 — Make BLE v2 the actual runtime path

- [x] Integrate `BlePacketProcessor`.
- [x] Use real report token.
- [x] ACK uses same token.
- [x] STATUS uses same token.
- [x] Persistent dedup.
- [x] Relay scheduler integration.
- [x] Remove duplicate old manager logic.

## Phase 4 — Complete terminal quenching

- [x] Emit RESOLVED/CANCELLED.
- [x] Tombstones.
- [x] Same-token stale suppression.
- [x] Automatic Room/UI update.

## Phase 5 — Make offline map real

- [ ] Real MBTiles/vector package.
- [ ] Archive tile provider.
- [x] Explicit map-pin selection.
- [x] Real checksum.
- [x] Offline tests (`MapPackageManagerTest.kt`).

## Phase 6 — Complete geography/catalog caching

- [x] Complete Legazpi Barangay data.
- [x] Canonical PSGC code consistency.
- [x] Asset importer.
- [x] GeoResolver.
- [x] Service-area sync.
- [x] Versioned refresh (`BootstrapPayload` + `AssetCatalogImporter`).

## Phase 7 — Backend normalization/routing hardening

- [x] Migration `024`.
- [x] Routing seed.
- [x] Transactional report service.
- [x] Report validation.
- [x] Gateway normalized ingest.
- [x] Role-filtered sync.

## Phase 8 — Admin normalized report UI

- [x] Incident report list/modal (`IncidentReportsModal`).
- [x] Report-vs-incident analytics (`ReportsPage`).
- [x] Source/location/detail-state visibility.

## Phase 9 — Hardware validation

- [ ] 2-phone one-hop.
- [ ] 3-phone multi-hop.
- [ ] ACK round trip.
- [ ] terminal quench.
- [ ] gateway uplink.
- [ ] offline map.
- [ ] app background/screen-off behavior.

---

# 37. Final Hardware Acceptance Test

Do not declare the BLE fallback finished until this scenario works on real phones.

## Phone A — Citizen

- [ ] Internet off.
- [ ] GPS available OR offline map pin selected.
- [ ] Creates report token X.
- [ ] Sends v2 SOS/report.

## Phone B — Citizen relay

- [ ] Receives X.
- [ ] Does not show details.
- [ ] Persists relay/dedup state.
- [ ] Rebroadcasts X.

## Phone C — Responder

- [ ] Receives X.
- [ ] Shows correct emergency type.
- [ ] Shows correct location source.
- [ ] Shows exact GPS/map pin or approximate Barangay.
- [ ] Opens offline map.
- [ ] ACKs X.

## Return path

- [ ] B silently relays ACK X.
- [ ] A recognizes own token.
- [ ] A shows HELP_EN_ROUTE.

## Resolution

- [ ] C resolves X.
- [ ] STATUS `INCIDENT_RESOLVED` with token X is emitted.
- [ ] B silently records terminal tombstone and stops SOS X.
- [ ] A updates own status to RESOLVED.
- [ ] Admin/Responder active list removes X.

## Stale copy

- [ ] Simulate another device broadcasting old SOS X.
- [ ] B/C suppress it.
- [ ] No new incident appears.
- [ ] No alert reopens.

---

# 38. What Is Actually "Implemented" Right Now

This is the current source-level assessment.

## Implemented enough to keep

- [x] General backend/auth/admin/dispatch foundation.
- [x] Report backend scaffolding/routes.
- [x] Report multi-step UI scaffolding.
- [x] Bounded GPS helper.
- [x] Location-source domain enum.
- [x] Android v2 codec core.
- [x] Go v2 emergency codec core.
- [x] Persistent relay table scaffolding.
- [x] Relay scheduler scaffolding.
- [x] Role-visibility policy scaffolding.
- [x] Offline map picker UI scaffolding.
- [x] Cached geography/catalog schema scaffolding.
- [x] Terminal tombstone data scaffolding.

## Present but **not complete enough to call implemented**

- [x] Detailed report end-to-end submission.
- [ ] Real offline map.
- [x] Real cached catalog import.
- [x] Complete/correct Barangay geography.
- [x] BLE v2 live processing integration.
- [x] Exact report-token ACK matching.
- [x] Terminal BLE resolution propagation.
- [x] Persistent terminal suppression in the live scan path.
- [x] Background silent Citizen relay using the new processor.
- [x] Citizen backend privacy filtering.
- [x] Gateway minimal→full normalized report enrichment end to end.
- [x] Report-vs-incident Admin UI.
- [x] Full normalized routing configuration.
- [x] Safe Room migration strategy.
- [x] Cross-platform BLE interoperability verification.

---

# 39. Definition of Done

Only mark the final AGAP offline-relay feature complete when:

- [x] No fake emergency coordinates exist anywhere.
- [x] No image/media report feature exists in v1.
- [x] Citizen detailed report uses real report API/domain.
- [x] Citizen can choose offline map pin.
- [ ] Real offline map renders with Internet disabled.
- [x] Barangay fallback data is correct and complete.
- [x] Citizen sees only own reports.
- [x] Citizen silently relays others.
- [x] v2 packet live path uses persistent processor/scheduler.
- [x] ACK matches exact original transport token.
- [x] RESOLVED/CANCELLED uses one consistent response-code mapping.
- [x] Terminal status propagates over BLE.
- [x] Stale same-token emergency cannot resurrect.
- [x] Room migrations do not destroy pending emergencies.
- [x] Backend report creation is transactional.
- [x] Agency routing has real mappings/service areas.
- [x] Android and Go produce identical BLE bytes for shared vectors.
- [ ] 3-phone physical demo passes.
