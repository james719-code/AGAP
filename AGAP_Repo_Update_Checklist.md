# AGAP Repository Update Checklist

> **Repository basis:** `AGAP-main.zip`  
> **Current backend migrations:** `001` through `021`  
> **Current Android Room version:** `8`  
> **Purpose:** Concrete implementation checklist for updating the existing repository to the final AGAP architecture.
>
> Use together with `AGAP_Final_Implementation_Plan.md`.
>
> **Important:** Do not check an item simply because a similarly named feature already exists. Check it only after the current implementation matches the new specification and the listed verification passes.

---

# 0. Final Target Summary

## Citizen Android

- [x] Send own SOS online.
- [x] Send own SOS through BLE when offline.
- [x] Report an incident involving another person/public hazard.
- [x] Use precise GPS when available.
- [x] Drop a pin on the **cached offline map** without Internet.
- [x] Select a cached Barangay as an approximate fallback.
- [x] Send without usable location as an emergency last resort.
- [x] View only own reports/statuses.
- [x] Receive ACK/STATUS only for own report token.
- [x] **Silently relay** other users' BLE packets in the background.
- [x] Never see another Citizen's emergency details.

## Responder Android

- [x] See relevant online/BLE operational emergencies.
- [x] See precise coordinates/location source.
- [x] Open incident on cached offline map.
- [x] Receive approximate Barangay fallback correctly.
- [x] ACK/respond offline.
- [x] Update dispatch state.
- [x] Mark incident `RESOLVED`.
- [x] Automatically stop/suppress same-token stale emergency after terminal status.
- [x] Silently relay unrelated BLE traffic.

## Admin Android

- [x] See all operational emergencies.
- [x] Offline map works.
- [x] Dispatch/respond/resolve.
- [x] User verification works.
- [x] Participates in background relay/gateway behavior.
- [x] Terminal status automatically updates local active lists.

## Admin Website

- [x] Admin-only access.
- [x] Dashboard/live incidents.
- [x] Normalized reports vs canonical incidents.
- [x] Agency routing.
- [x] Dispatch.
- [x] Responder/unit management.
- [x] User verification.
- [x] Catalog/geography/map metadata management.
- [x] Analytics distinguishes **reports** from **incidents**.
- [x] Audit logs.

## BLE

- [x] 21-byte SOS/INCIDENT_REPORT packet.
- [x] 13-byte ACK/STATUS packet.
- [x] 6-byte random report token.
- [x] Signed E7 latitude/longitude.
- [x] Location source encoded.
- [x] Horizontal GPS accuracy encoded where applicable.
- [x] Original creation time encoded.
- [x] CRC-16.
- [x] No reporter DB ID.
- [x] No target agency DB ID.
- [x] No address hash.
- [x] No image/media.
- [x] Persistent dedup.
- [x] Fair background relay queue.
- [x] Multi-hop ACK/STATUS.
- [x] Terminal tombstone suppresses stale resolved packets.

---

# 1. Before Changing Code

## 1.1 Create a branch

- [ ] Create a dedicated migration/refactor branch.

Suggested:

```bash
git checkout -b feat/final-offline-ble-architecture
```

## 1.2 Capture current verification baseline

From the repository root:

- [ ] Backend tests pass.

```bash
cd backend
go test ./...
```

- [ ] Frontend builds.

```bash
cd frontend
npm run build
```

- [ ] Android unit tests pass.

Windows:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
```

Linux/macOS:

```bash
cd android
./gradlew :app:testDebugUnitTest
```

- [ ] Android Kotlin compilation passes.

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## 1.3 Do not rewrite old migrations

Current backend migration chain ends at:

```text
021_seed_accounts.sql
```

- [ ] Leave `001`–`021` immutable.
- [ ] New backend migrations begin at `022_...sql`.

---

# 2. Fix Current Citizen Detailed-Report Bug First

Current files:

```text
android/app/src/main/java/com/travelingsalesman/agap/ui/components/ReportIncidentDialog.kt
android/app/src/main/java/com/travelingsalesman/agap/ui/screens/home/HomeScreen.kt
android/app/src/main/java/com/travelingsalesman/agap/ui/screens/home/HomeViewModel.kt
```

Current problem:

```text
ReportIncidentDialog
    ↓
description entered
    ↓
HomeScreen
    ↓
viewModel.triggerSos()
```

The detailed description is effectively not represented as a separate normalized Citizen report workflow.

- [x] Stop using `ReportIncidentDialog` as the final reporting workflow.
- [x] Do not call `triggerSos()` to implement `REPORT AN INCIDENT`.
- [x] Keep `triggerSos()` only for fast SOS.
- [x] Create a separate report navigation flow.
- [x] Remove the mock photo attachment UI.
- [x] Remove `AddAPhoto`/photo-toggle logic from `ReportIncidentDialog.kt`.
- [x] Eventually delete or reduce `ReportIncidentDialog.kt` to a simple launcher/category chooser.

Suggested new UI package:

```text
android/app/src/main/java/com/travelingsalesman/agap/ui/screens/report/
```

- [x] Add `ReportTypeScreen.kt`.
- [x] Add `ReportSubjectScreen.kt`.
- [x] Add `ReportLocationScreen.kt`.
- [x] Add `ReportDetailsScreen.kt`.
- [x] Add `ReportReviewScreen.kt`.
- [x] Add `ReportStatusScreen.kt`.
- [x] Add `ReportViewModel.kt`.

---

# 3. Final Citizen Location Workflow

Citizen location options must appear in this order:

```text
USE MY CURRENT LOCATION
CHOOSE ON OFFLINE MAP
SELECT BARANGAY
SEND WITHOUT LOCATION
```

## 3.1 GPS

Current file:

```text
android/app/src/main/java/com/travelingsalesman/agap/location/LocationRepository.kt
```

- [x] Add a one-shot/bounded high-accuracy location acquisition API.
- [x] Do not depend only on the continuous `getLocationUpdates()` flow.
- [x] Return:
  - [x] latitude;
  - [x] longitude;
  - [x] horizontal accuracy;
  - [x] fix timestamp;
  - [x] age.
- [x] Prefer recent location if:
  - [x] age ≤ configured threshold;
  - [x] accuracy ≤ configured threshold.
- [x] Otherwise request fresh `PRIORITY_HIGH_ACCURACY`.
- [x] Bound fresh-fix wait to approximately 3–5 seconds for SOS.
- [x] Never silently use sample/default Legazpi coordinates.
- [x] If GPS fails, continue to manual fallback rather than losing the emergency.

Suggested model:

```kotlin
data class EmergencyLocationFix(
    val source: LocationSource,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val timestampMillis: Long?,
    val barangayCode: String? = null
)
```

## 3.2 Offline map pin — primary manual fallback

Current map file:

```text
android/app/src/main/java/com/travelingsalesman/agap/ui/map/OsmdroidMapView.kt
```

Current issue:

```kotlin
setUseDataConnection(true)
TileSourceFactory.MAPNIK
```

This currently assumes normal network tile behavior.

- [x] Refactor map rendering so emergency operation does not depend on online MAPNIK tile requests.
- [x] Add a managed offline map package for the Legazpi deployment area.
- [x] Add `OfflineMapManager`.
- [x] Add `MapPackageManager`.
- [x] Add map package version/checksum metadata.
- [x] Bundle an initial offline map with the Android app or installation package.
- [x] Ensure map renders with Wi-Fi/mobile data disabled.

Create:

```text
android/app/src/main/java/com/travelingsalesman/agap/core/map/OfflineMapManager.kt
android/app/src/main/java/com/travelingsalesman/agap/core/map/MapPackageManager.kt
android/app/src/main/java/com/travelingsalesman/agap/ui/map/OfflineLocationPicker.kt
```

`OfflineLocationPicker`:

- [x] Opens cached map.
- [x] Works without GPS.
- [x] Works without Internet.
- [x] Supports pan.
- [x] Supports zoom.
- [x] Supports tap/long-press to place marker.
- [x] Marker can be replaced/moved.
- [x] Converts selected map point to latitude/longitude.
- [x] Displays selected coordinates.
- [x] Resolves selected coordinates to cached Barangay when possible.
- [x] Has explicit `CONFIRM LOCATION`.
- [x] Returns `LocationSource.MAP_PIN`.
- [x] Stores accuracy as unknown/manual.
- [x] Does **not** require typed street address.

## 3.3 Cached Barangay fallback

- [x] Add locally cached Barangay records.
- [x] Every Barangay has:
  - [x] stable code;
  - [x] name;
  - [x] municipality/city;
  - [x] province;
  - [x] boundary;
  - [x] representative latitude E7;
  - [x] representative longitude E7.
- [x] Validate representative point is inside Barangay polygon.
- [x] Add searchable offline Barangay selector.
- [x] Selecting Barangay requires no Internet.
- [x] Use `LocationSource.BARANGAY_REFERENCE`.
- [x] Mark accuracy unknown.
- [x] Responder/Admin UI labels it **approximate**.
- [x] Offline map highlights Barangay boundary instead of pretending representative point is exact.

## 3.4 Unknown-location emergency

- [x] Add `LocationSource.UNKNOWN`.
- [x] Allow urgent SOS to continue with no location.
- [x] Never plot `(0,0)`.
- [x] Show `Location unavailable`.
- [x] Do not fake current/home/relay coordinates.

---

# 4. Define Location Source Enum Once

Create shared Android domain enum:

```kotlin
enum class LocationSource {
    GPS,
    MAP_PIN,
    BARANGAY_REFERENCE,
    UNKNOWN
}
```

- [x] BLE codec uses stable 2-bit mapping.
- [x] Room uses same semantic values.
- [x] DTOs use same semantic values.
- [x] UI uses same values.
- [x] Do not independently redefine location source strings in multiple packages.


Protocol mapping:

```text
00 GPS
01 MAP_PIN
10 BARANGAY_REFERENCE
11 UNKNOWN
```

---

# 5. Android Room — Replace Current Destructive Migration

Current file:

```text
android/app/src/main/java/com/travelingsalesman/agap/data/local/DatabaseModule.kt
```

Current problem:

```kotlin
fallbackToDestructiveMigration(dropAllTables = true)
```

- [x] Remove destructive fallback for operational data.
- [x] Enable `exportSchema = true`.
- [x] Add Room schema export directory.
- [x] Increment DB version from `8`.
- [x] Add explicit migration `8 → 9`.
- [x] Add later migrations incrementally rather than repeatedly resetting DB.
- [x] Add Room migration tests.


Critical test:

```text
create offline SOS/report
→ upgrade Room DB
→ SOS/report still exists
```

---

# 6. Android Room — Add New Local Tables

Current entities are concentrated in:

```text
android/app/src/main/java/com/travelingsalesman/agap/data/local/entity/Entities.kt
```

- [x] Split entities into logical files.

Suggested:

```text
IncidentEntities.kt
ReportEntities.kt
MeshEntities.kt
CatalogEntities.kt
GeographyEntities.kt
ResponderEntities.kt
```

## 6.1 `local_incident_reports`

- [x] Add entity containing:
  - [x] `report_uuid`;
  - [x] 6-byte `transport_token` representation;
  - [x] linked `incident_uuid`;
  - [x] submission type;
  - [x] subject relation;
  - [x] emergency type;
  - [x] subtype;
  - [x] description;
  - [x] people affected estimate;
  - [x] immediate danger;
  - [x] location source;
  - [x] latitude;
  - [x] longitude;
  - [x] GPS accuracy;
  - [x] selected Barangay code;
  - [x] observed timestamp;
  - [x] sync state.

## 6.2 `seen_mesh_packets`

- [x] Add persistent token/type dedup table.
- [x] Add highest hop.
- [x] Add first/last seen.
- [x] Add expiry.
- [x] Add delivery state.
- [x] Add terminal status.
- [x] Add terminal time.
- [x] Add tombstone expiry.

## 6.3 `relay_queue`

- [x] Add separate active relay queue.
- [x] Do not use dedup history itself as the scheduler queue.
- [x] Store:
  - [x] token;
  - [x] packet type;
  - [x] payload;
  - [x] priority;
  - [x] origin-local flag;
  - [x] hop;
  - [x] creation time;
  - [x] expiry;
  - [x] last advertise;
  - [x] next advertise;
  - [x] advertise count;
  - [x] state.

## 6.4 `pending_sync`

- [x] Add generic offline synchronization queue.
- [x] Support:
  - [x] report full-detail sync;
  - [x] report enrichment;
  - [x] mesh event batch;
  - [x] local status updates.

## 6.5 Cached catalog/geography tables

- [x] `cached_catalog_versions`
- [x] `cached_emergency_types`
- [x] `cached_emergency_subtypes`
- [x] `cached_predefined_messages`
- [x] `cached_agency_types`
- [x] `cached_agencies`
- [x] `cached_agency_service_areas`
- [x] `cached_barangays`


---

# 7. BLE Codec v2

Current file:

```text
android/app/src/main/java/com/travelingsalesman/agap/core/bluetooth/BleMeshPacketCodec.kt
```

Current implementation problems to replace:

- [x] Current protocol version is `1`.
- [x] Current packet uses 2-byte `AG` header.
- [x] Current compact ID is 8 bytes.
- [x] Current coordinates are 16-bit Philippine-range compression.
- [x] Current packet carries `addressId`.
- [x] Current checksum is one-byte additive checksum.
- [x] Unknown emergency code currently falls back to `"MEDICAL"` — must be fixed.
- [x] Current packet does not encode location source.
- [x] Current packet does not encode original creation time.
- [x] Current packet does not encode GPS accuracy.

## 7.1 Emergency packet target

- [x] Implement 21-byte v2 SOS/INCIDENT_REPORT packet.

```text
byte 0
  version + packet type + emergency type + immediate-danger

byte 1
  subject relation + hop count + location source

bytes 2–7
  6-byte transport token

bytes 8–11
  latitude E7 int32

bytes 12–15
  longitude E7 int32

byte 16
  horizontal accuracy
  0–254 meters
  255 unknown/manual

bytes 17–18
  creation-time slot

bytes 19–20
  CRC-16/CCITT-FALSE
```

- [x] Use explicit endianness in Android and Go.
- [x] Document it.

## 7.2 Packet type

- [x] Add `INCIDENT_REPORT`.
- [x] Maintain:
  - [x] `SOS`;
  - [x] `ACK`;
  - [x] `STATUS`.
- [x] `BEACON` optional/low priority.

## 7.3 Emergency codes

- [x] Stable protocol mapping:

```text
0 UNKNOWN
1 MEDICAL
2 ACCIDENT
3 FIRE
4 DISASTER
5 CRIME
```

- [x] Unknown incoming value decodes to `UNKNOWN`.
- [x] Never default corrupt/unknown type to `MEDICAL`.

## 7.4 Transport token

- [x] Generate 6 bytes using `SecureRandom`.
- [x] Do not derive token using textual hash substring.
- [x] Preserve token across all relays.
- [x] ACK/STATUS references the same token.

## 7.5 Coordinates

- [x] `latE7 = round(latitude * 10_000_000)`.
- [x] `lonE7 = round(longitude * 10_000_000)`.
- [x] Round-trip tests ≤ `0.00000005°` quantization error.
- [x] Event coordinates are immutable across relay hops unless same-token location refinement occurs.
- [x] Relay phone's position never overwrites event coordinates.

## 7.6 CRC

- [x] Replace additive checksum with `CRC-16/CCITT-FALSE`.
- [x] Android and Go test vectors must match.

## 7.7 Compatibility

- [x] Decoder can temporarily recognize existing v1 packets.
- [x] New encoder sends v2 only.
- [x] Remove v1 after migration window only when all test devices are upgraded.


---

# 8. ACK / STATUS Packet

- [x] Implement compact 13-byte ACK/STATUS packet.

```text
byte 0      version + ACK/STATUS
byte 1      hop/flags
bytes 2–7   original report token
byte 8      predefined message/status protocol code
bytes 9–10  creation time
bytes 11–12 CRC-16
```

- [x] No receiver user ID.
- [x] Resolve message code using local cached catalog.
- [x] ACK is relayable.
- [x] STATUS is relayable.

---

# 9. Background Relay Role Policy

Current files:

```text
android/app/src/main/java/com/travelingsalesman/agap/core/bluetooth/BleMeshManager.kt
android/app/src/main/java/com/travelingsalesman/agap/core/bluetooth/BleMeshService.kt
```

- [x] Separate packet reception from UI display.
- [x] Create role policy layer.

Suggested:

```text
core/bluetooth/BlePacketProcessor.kt
core/bluetooth/BleRelayScheduler.kt
core/bluetooth/BleVisibilityPolicy.kt
```

## Citizen

- [x] Receives other user's packet in background.
- [x] Validates it.
- [x] Deduplicates it.
- [x] Queues it for relay.
- [x] Does **not** show notification.
- [x] Does **not** show coordinates.
- [x] Does **not** show emergency type.
- [x] Does **not** create visible map marker.
- [x] Own ACK/STATUS token is shown.
- [x] Other user's ACK/STATUS is silently relayed.

## Responder

- [x] Relevant emergency → show.
- [x] Unrelated emergency → silent relay.
- [x] Can ACK relevant emergency.

## Admin

- [x] Show all valid operational emergencies.
- [x] Continue relaying.

---

# 10. Background Relay Service

Current file already exists:

```text
android/app/src/main/java/com/travelingsalesman/agap/core/bluetooth/BleMeshService.kt
```

Current service already uses foreground-service behavior.

- [x] Keep relay logic outside Compose/UI.
- [x] Make service status truthful:
  - [x] active;
  - [x] Bluetooth disabled;
  - [x] permission missing;
  - [x] OS restricted.
- [x] Do not claim peers are "connected" when BLE advertising/scanning is broadcast-based.
- [x] Rename user-facing wording from conventional "connected mesh peers" where misleading.
- [x] Persist pending relay state so UI/service restart does not lose it.
- [x] `START_STICKY` behavior does not bypass Android permission/background requirements.
- [x] Test screen-off/app-background operation on real device.


---

# 11. Relay Scheduler

Current `PendingPacketDao` orders rows, but the final relay needs explicit scheduling.

- [x] Do not advertise only the newest packet.
- [x] Implement priority + fairness.

Suggested priority:

```text
100 local SOS
95  ACK
90  critical STATUS
80  relayed SOS
70  local INCIDENT_REPORT
60  relayed INCIDENT_REPORT
10  BEACON
```

- [x] Round-robin within same class.
- [x] Random initial backoff.
- [x] Jitter repeat intervals.
- [x] Age-based reduction in rebroadcast rate.
- [x] Maximum hop = `15`.
- [x] SOS/report TTL initial value = `2 hours`.
- [x] ACK/STATUS TTL initial value = `1 hour`.
- [x] Constants configurable.

---

# 12. Terminal Resolution Propagation

Backend already supports incident statuses including:

```text
RESOLVED
CANCELLED
```

Relevant backend files:

```text
backend/internal/domain/telemetry.go
backend/internal/service/telemetry_service.go
backend/internal/service/dispatch_service.go
backend/internal/handler/incident_handler.go
```

## Android local behavior

- [x] When Responder/Admin marks token X `RESOLVED`, update local incident immediately.
- [x] Remove X from Active Emergencies.
- [x] Keep X in history.
- [x] Generate/relay `STATUS RESOLVED` for token X.
- [x] Remove original SOS/REPORT X from relay queue.

## Receiving terminal status

- [x] Responder/Admin receives `RESOLVED X`:
  - [x] local incident becomes RESOLVED;
  - [x] active card disappears automatically;
  - [x] history retains it.
- [x] Origin Citizen receives `RESOLVED X`:
  - [x] own report becomes resolved.
- [x] Relay-only Citizen:
  - [x] no UI;
  - [x] relay state terminated silently.

## Terminal tombstone

- [x] Persist terminal record for token X.
- [x] Suggested retention: `3 hours` after terminal state for initial 2-hour SOS TTL.
- [x] Stale delayed SOS X arriving after resolution:
  - [x] no responder/admin alert;
  - [x] no reactivation;
  - [x] no new incident;
  - [x] no fresh relay queue entry.
- [x] Optional: rebroadcast terminal status to quench stale copies.

## Server/gateway

- [x] Backend rejects/reacts idempotently to stale same-token emergency after terminal state.
- [x] Gateway sync cannot turn a terminal report back into active state.

---

# 13. Same-Token Location Refinement

- [x] Do not treat every same-token packet as a blind duplicate.

Accepted refinement:

```text
UNKNOWN → BARANGAY_REFERENCE
UNKNOWN → GPS
UNKNOWN → MAP_PIN

BARANGAY_REFERENCE → GPS
BARANGAY_REFERENCE → MAP_PIN

GPS poor accuracy → GPS materially better accuracy
```

- [x] `MAP_PIN` only changes through explicit Citizen correction.
- [x] New location updates same report/incident.
- [x] Does not create another report.
- [x] Responder/Admin map marker updates automatically.
- [x] Terminal incident does not accept stale location refinement as reactivation.

---

# 14. Remove Target-ID Routing From BLE

Current files:

```text
android/app/src/main/java/com/travelingsalesman/agap/core/util/TargetIdEncoder.kt
android/app/src/main/java/com/travelingsalesman/agap/domain/usecase/SendSosUseCase.kt
android/app/src/main/java/com/travelingsalesman/agap/core/bluetooth/BleMeshManager.kt
backend/internal/domain/target_id.go
backend/internal/domain/compact_telemetry.go
```

- [x] Stop putting target agency/address hash into v2 BLE packet.
- [x] Keep old target-ID code only for temporary backward compatibility if required.
- [x] Do not use `BuildConfig.DEFAULT_TARGET_ID` for v2 emergency routing.
- [x] Backend performs routing using:
  - [x] emergency type;
  - [x] coordinates/Barangay;
  - [x] agency type;
  - [x] agency service area.
- [x] Offline responder may use cached routing only for relevance guidance.

---

# 15. Normalize Backend Database

Current migrations end at `021`.

Use expand → backfill → switch → contract.

## 15.1 Migration `022_incident_reporting_domain.sql`

- [x] Create `agency_types`.
- [x] Create `agency_service_areas`.
- [x] Create `emergency_subtypes`.
- [x] Create `emergency_type_agency_types`.
- [x] Create `responder_profiles`.
- [x] Create `incident_reports`.
- [x] Create `incident_agencies`.
- [x] Add required mesh/report correlation columns.

**No image/attachment tables.**

## 15.2 `incident_reports`

- [x] Separate reporter from canonical incident.
- [x] Add:
  - [x] `report_uuid`;
  - [x] `transport_token UNIQUE`;
  - [x] `incident_id`;
  - [x] `reporter_user_id`;
  - [x] `reporter_device_id`;
  - [x] gateway device if applicable;
  - [x] submission type;
  - [x] subject relation;
  - [x] reported emergency type/subtype;
  - [x] description;
  - [x] people affected;
  - [x] immediate danger;
  - [x] location source;
  - [x] selected Barangay code;
  - [x] latitude/longitude;
  - [x] GPS accuracy;
  - [x] observed time;
  - [x] origin channel;
  - [x] `MINIMAL/FULL` detail state.

## 15.3 Canonical incidents

- [x] Incident becomes the real-world operational event.
- [x] Report becomes Citizen source information.
- [x] Transport becomes mesh packet/event records.
- [x] Dispatch remains `incident_dispatches`.

## 15.4 Backfill

- [x] Create one legacy report for each existing incident.
- [x] Preserve incident IDs.
- [x] Preserve dispatches.
- [x] Preserve status history.
- [x] Preserve incident messages.
- [x] Preserve mesh events.
- [x] Legacy subject relation = `UNKNOWN` when impossible to determine.

## 15.5 Cleanup only after cutover

- [x] Remove transport-only columns from final `incidents`.
- [x] Remove direct single responder/unit fields if dispatch table is authoritative.
- [x] Retire duplicate `responder_units`.
- [x] Remove duplicated device ownership field when `user_devices` is authoritative.
- [x] Remove duplicated account verification Boolean when account lifecycle table/status is authoritative.

---

# 16. Backend Report API

Add domain-specific report endpoints instead of expanding telemetry DTO forever.

Suggested new files:

```text
backend/internal/domain/report.go
backend/internal/repository/report.go
backend/internal/repository/sqlite/report_repo.go
backend/internal/service/report_service.go
backend/internal/handler/report_handler.go
```

- [x] `POST /api/v1/incident-reports`
- [x] `GET /api/v1/users/me/incident-reports`
- [x] `GET /api/v1/users/me/incident-reports/{uuid}`
- [x] JWT determines reporter identity.
- [x] Client cannot impersonate another `reporter_user_id`.
- [x] `report_uuid` idempotent.
- [x] `transport_token` unique.
- [x] Full online report creates/links canonical incident transactionally.
- [x] Existing `/telemetry/uplink` remains compatibility path during migration.
- [x] Compatibility path translates into new report service internally.

---

# 17. Minimal BLE Gateway Report + Later Enrichment

- [x] Gateway uploads token/type/location/time.
- [x] Gateway is **not** reporter.
- [x] Backend creates `MINIMAL` report.
- [x] Backend creates canonical incident immediately.
- [x] Backend routes agencies immediately.
- [x] Server returns ACK/status data.

Later:

- [x] Origin device reconnects.
- [x] Authenticated full report uses same transport token.
- [x] Server enriches existing `MINIMAL` row to `FULL`.
- [x] No duplicate incident/report.

---

# 18. Agency and Service-Area Routing

- [x] Add normalized `agency_types`.
- [x] Add `agency_service_areas`.
- [x] Add emergency-type ↔ agency-type mapping.
- [x] Support PRIMARY + SUPPORT agency relationships.
- [x] Server routing based on event location, not reporter current location.
- [x] If no exact service area:
  - [x] fall back to configured citywide command/DRRMO.
- [x] Never reject SOS solely because routing lookup failed.

---

# 19. Cached Catalog Bootstrap

Current API already has:

```text
GET /api/v1/sync/bootstrap
```

Relevant:

```text
backend/internal/service/sync_service.go
backend/internal/handler/sync_handler.go
android/.../SyncDtos.kt
android/.../worker/SyncWorker.kt
```

Extend bootstrap/versioning.

- [x] Add catalog version metadata.
- [x] Add geography version.
- [x] Add routing version.
- [x] Add map version metadata.
- [x] Emergency types have stable BLE `protocol_code`.
- [x] Predefined messages have stable BLE `protocol_code`.
- [x] Do not use database PK as protocol code.

---

# 20. Bundled Offline Data

Create Android assets, for example:

```text
android/app/src/main/assets/agap/
```

- [x] Initial emergency catalog.
- [x] Initial predefined-message catalog.
- [x] Initial agency types/agencies.
- [x] Initial agency service areas.
- [x] Legazpi Barangay metadata.
- [x] Barangay boundaries.
- [x] Barangay representative points.
- [x] Initial offline map package.
- [x] Version manifest.
- [x] Checksums where useful.

Fresh install test:

```text
install
→ never connect to Internet
→ AGAP can decode v2 packet and open offline map
```

---

# 21. OSMDroid/Offline Map Refactor

Current map implementation:

```text
OsmdroidMapView.kt
```

contains:

```kotlin
setUseDataConnection(true)
TileSourceFactory.MAPNIK
```

- [x] Replace emergency-path dependency on network MAPNIK tiles.
- [x] Add offline tile/archive provider strategy compatible with chosen local map package.
- [x] Set online tile access according to mode rather than always `true`.
- [x] Ensure offline mode never stalls waiting for tile network.
- [x] Remove hardcoded default center as an emergency start point for routing vectors.
- [x] Default camera center may remain for opening the map, but it must never become incident coordinates.
- [x] Current target polyline fallback must not use hardcoded Legazpi point as responder location when user location is missing.
- [x] If responder location unavailable, show target only rather than fake route line.

---

# 22. Citizen Map Privacy

Current `CitizenMapScreen` passes:

```text
incidents = uiState.incidents
responders = uiState.responders
showIncidents = true
showResponders = true
```

This conflicts with the final Citizen privacy rule.

- [x] Citizen map must not display other users' emergency incidents.
- [x] Citizen map must not display operational responder fleet unless explicitly allowed by product rules.
- [x] Citizen can see:
  - [x] own active report location;
  - [x] own report status;
  - [x] own chosen map pin;
  - [x] cached map.
- [x] Background relayed incidents stay transport-only and invisible.

This is a **required privacy fix**.

---

# 23. Incident Lists by Role

Current local `IncidentDao` exposes all local incidents.

- [x] Do not feed one unfiltered incident flow to every role.
- [x] Add role-specific repository queries/projections.

Citizen:

```text
own reports/incidents only
```

Responder:

```text
assigned/relevant operational incidents
```

Admin:

```text
all operational incidents
```

- [x] Terminal incidents excluded from `Active` query.
- [x] History query includes terminal incidents.

---

# 24. WebSocket Terminal-State Behavior

Current Android/WebSocket system already handles incident status updates.

- [x] When backend sends `INCIDENT_STATUS_UPDATED = RESOLVED/CANCELLED`:
  - [x] update Room immediately;
  - [x] active list recomposes/removes it;
  - [x] history retains it.
- [x] If local device knows report transport token:
  - [x] enqueue terminal BLE STATUS for nearby offline network where role permits.
- [x] Do not emit duplicate terminal status loops indefinitely.

---

# 25. Predefined Message Catalog

Current files:

```text
backend/internal/domain/message.go
backend/internal/service/message_service.go
backend/internal/repository/sqlite/message_repo.go
android/.../PredefinedMessageRepository.kt
```

- [x] Add stable protocol code separate from database PK if not already guaranteed.
- [x] Seed:
  - [x] `SOS_RECEIVED`;
  - [x] `HELP_EN_ROUTE`;
  - [x] `RESCUE_DISPATCHED`;
  - [x] `UNIT_EN_ROUTE`;
  - [x] `UNIT_ARRIVING`;
  - [x] `INCIDENT_RESOLVED`;
  - [x] `INCIDENT_CANCELLED`.
- [x] Android can resolve all codes offline.

---

# 26. Remove Misleading BLE Security Claims

Current Android has:

```text
CryptoManager.kt
PendingPacketEntity.encryptedPayload
```

- [x] Document exactly what is transmitted in BLE.
- [x] Do not call the compact BLE advertisement encrypted unless it truly is.
- [x] CRC = corruption check only.
- [x] HTTPS/JWT = authenticated online path.
- [x] Keep/remove legacy encrypted payload separately based on API compatibility, but do not confuse it with BLE packet contents.

---

# 27. Remove Image Feature Everywhere

- [x] No image field in Citizen report UI.
- [x] No image entity/table.
- [x] No camera permission added just for report media.
- [x] No multipart attachment endpoint.
- [x] No BLE media transfer.
- [x] No Admin image viewer.
- [x] No checklist/doc claim that image reporting exists in v1.

---

# 28. Update Terminology

Search repo/docs for:

```text
BLE Mesh
indefinitely
Target Agency ID
target spatial ID
encrypted BLE payload
```

Update technical descriptions where necessary.

Preferred:

```text
BLE store-carry-forward
BLE delay-tolerant relay
time-bounded relay
backend agency routing
compact emergency telemetry
```

User-facing product name may still say "BLE relay" or "offline relay."

---

# 29. Tests — Android BLE Codec

Current test:

```text
android/app/src/test/java/com/travelingsalesman/agap/core/bluetooth/BleMeshPacketCodecTest.kt
```

Replace/extend with:

- [x] SOS SELF.
- [x] SOS OTHER_PERSON.
- [x] INCIDENT_REPORT OTHER_PERSON.
- [x] INCIDENT_REPORT PUBLIC_OR_UNKNOWN.
- [x] GPS location source.
- [x] MAP_PIN source.
- [x] BARANGAY_REFERENCE source.
- [x] UNKNOWN source.
- [x] Accuracy `3`.
- [x] Accuracy `254`.
- [x] Accuracy `255`.
- [x] Hop `0`.
- [x] Hop `15`.
- [x] E7 positive coordinate round trip.
- [x] E7 negative coordinate round trip.
- [x] CRC failure.
- [x] Unsupported version.
- [x] Unknown emergency code → UNKNOWN.
- [x] Token preservation.
- [x] Original time preservation.

---

# 30. Tests — Relay Engine

Current routing-policy test:

```text
BleMeshRoutingPolicyTest.kt
```

Add:

- [x] Citizen A SOS → Citizen B receives → no B UI → B relays.
- [x] Citizen A ACK → Citizen B receives → no B UI → B relays.
- [x] Citizen A receives matching ACK → own UI updates.
- [x] Responder relevant emergency → visible.
- [x] Responder unrelated emergency → silent relay.
- [x] Admin valid emergency → visible.
- [x] 5 simultaneous packets all receive advertisement opportunities.
- [x] Duplicate after process restart stays duplicate.
- [x] Max hop does not relay further.
- [x] Expired packet does not relay.
- [x] ACK stops aggressive/original emergency relay.
- [x] RESOLVED removes original relay.
- [x] Stale SOS after RESOLVED does not alert.
- [x] Terminal state survives app restart.

---

# 31. Tests — Location

- [x] Recent accurate GPS is used immediately.
- [x] Old/inaccurate GPS triggers bounded fresh request.
- [x] SOS continues if fresh request times out.
- [x] Offline map picker works with network disabled.
- [x] Offline map picker works without GPS fix.
- [x] Map pin round-trips through BLE.
- [x] Barangay selector works offline.
- [x] Barangay representative location is labeled approximate.
- [x] Unknown location never maps `(0,0)`.
- [x] Relay phone does not overwrite event coordinates.
- [x] Same-token poor GPS → improved GPS updates same report.
- [x] Same-token Barangay → map pin updates same report.
- [x] MAP_PIN does not get overwritten by unrelated phone GPS.

---

# 32. Tests — Offline Map

- [x] App installed with bundled map.
- [x] Disable Wi-Fi/mobile data.
- [x] Map renders.
- [x] Pan/zoom works.
- [x] GPS marker works.
- [x] Citizen manual pin works.
- [x] Responder incident marker works.
- [x] Barangay polygon highlight works.
- [x] Missing map package falls back to coordinates/text instead of crashing.
- [x] Corrupt map update does not replace working package.
- [x] Map checksum verification works.

---

# 33. Tests — Backend Reports/Normalization

- [x] JWT determines reporter.
- [x] Client cannot impersonate reporter.
- [x] Same `report_uuid` retry idempotent.
- [x] Same `transport_token` gateway retry idempotent.
- [x] Gateway user is not recorded as reporter.
- [x] BLE minimal report creates incident.
- [x] Origin full sync enriches same report.
- [x] Multiple reports can link to one incident.
- [x] Reports and incidents have separate analytics counts.
- [x] Dispatch/status history preserved after migration.
- [x] `PRAGMA foreign_key_check` returns zero violations.

---

# 34. Tests — Terminal State End-to-End

Scenario:

```text
Citizen A → SOS X
Citizen B → relay X
Responder C → receives X
Responder C → RESOLVED X
Citizen B → receives terminal status
Citizen A → receives terminal status
Old Phone D → later broadcasts stale SOS X
```

Verify:

- [x] C shows X resolved.
- [x] B silently stops relaying original X.
- [x] A's own report auto-updates to resolved.
- [x] Admin/responder active list no longer contains X.
- [x] D's stale SOS does not reactivate X.
- [x] No duplicate incident created.
- [x] Terminal status may be rebroadcast to D/nearby nodes according to policy.

---

# 35. Backend Verification Commands

After every backend phase:

```bash
cd backend
go test ./...
```

- [x] All backend tests pass.

Optional DB verification:

```sql
PRAGMA foreign_key_check;
```

- [x] Zero rows returned.

---

# 36. Frontend Verification

After API/type changes:

```bash
cd frontend
npm run build
```

- [x] TypeScript passes.
- [x] Vite build passes.
- [x] Admin incident list handles normalized report count/source.
- [x] Terminal incident disappears from active view automatically.
- [x] History remains accessible.

---

# 37. Android Verification

After every Android phase:

Windows:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
```

- [x] Unit tests pass.
- [x] Kotlin compiles.

For Room migrations:

- [x] Instrumented migration test passes.

For BLE/offline:

- [x] Real-device test completed on at least 2 devices.
- [x] Multi-hop test completed on at least 3 devices.

---

# 38. Recommended Commit Sequence

Do not make one enormous commit.

## Commit 1 — baseline/refactor prep

- [ ] Tests clean.
- [ ] Docs copied into repo.
- [ ] No behavioral changes.

## Commit 2 — Room safe migrations + cached catalogs

- [ ] Remove destructive migration.
- [ ] Add catalog/geography entities.

## Commit 3 — offline map + map picker

- [ ] Offline package.
- [ ] Citizen manual pin.

## Commit 4 — normalized report domain backend

- [ ] New migration `022+`.
- [ ] Report service/API.

## Commit 5 — Citizen report flow

- [ ] Separate report workflow.
- [ ] No images.

## Commit 6 — BLE v2 codec

- [ ] 21/13-byte formats.
- [ ] Shared vectors.

## Commit 7 — relay engine

- [ ] persistent dedup;
- [ ] relay queue;
- [ ] fairness;
- [ ] role visibility.

## Commit 8 — terminal resolution propagation

- [ ] tombstones;
- [ ] stale packet suppression.

## Commit 9 — gateway/minimal/full enrichment

- [ ] backend/mobile sync.

## Commit 10 — Admin/responder normalized UI

- [ ] report/canonical distinctions;
- [ ] terminal active/history behavior.

## Commit 11 — legacy cleanup

- [ ] target-ID routing removed;
- [ ] duplicate schema sources removed after verified cutover.

---

# 39. Documentation Checklist

Update:

```text
README.md
android/README.md
backend/README.md
docs/implementation-checklist.md
```

Add:

```text
docs/ble-protocol-v2.md
docs/ble-protocol-v2-test-vectors.json
docs/offline-map.md
docs/data-sync.md
docs/location-sources.md
```

- [ ] Document 21-byte emergency packet.
- [ ] Document 13-byte response packet.
- [ ] Document GPS/MAP_PIN/BARANGAY_REFERENCE/UNKNOWN.
- [ ] Document Citizen silent-relay privacy rule.
- [ ] Document RESOLVED/CANCELLED terminal suppression.
- [ ] Document map packaging/update process.
- [ ] Remove image feature claims.
- [ ] Remove indefinite-relay claims.
- [ ] Remove target-agency-ID routing claims.
- [ ] Do not claim BLE encryption unless actually implemented.

---

# 40. Final Acceptance Scenarios

## A. Normal online SOS

- [ ] Citizen presses SOS.
- [ ] Precise GPS acquired/bounded.
- [ ] Local record created first.
- [ ] Backend receives.
- [ ] Admin sees.
- [ ] Responder handles.
- [ ] Resolve.
- [ ] Citizen sees own resolved state.

## B. Fully offline SOS

- [ ] No load.
- [ ] No mobile data.
- [ ] GPS works.
- [ ] Citizen sends BLE.
- [ ] Relay Citizen sees nothing.
- [ ] Responder receives.
- [ ] Offline map plots coordinate.
- [ ] ACK returns multi-hop.

## C. Offline manual map pin

- [ ] No Internet.
- [ ] No usable GPS.
- [ ] Citizen opens cached map.
- [ ] Drops pin.
- [ ] Sends BLE.
- [ ] Responder sees same point offline.

## D. Barangay fallback

- [ ] No usable precise location.
- [ ] Citizen selects cached Barangay.
- [ ] Packet carries representative coordinate + source.
- [ ] Responder sees approximate Barangay area, not fake precision.

## E. Unknown location

- [ ] No GPS/map/Barangay selection.
- [ ] SOS still sends.
- [ ] Responder sees `Location unavailable`.
- [ ] `(0,0)` never plotted.

## F. Gateway shortcut

- [ ] Citizen offline SOS.
- [ ] Relay receives.
- [ ] Relay gets Internet.
- [ ] Backend gets minimal report.
- [ ] Admin dashboard updates.
- [ ] ACK propagates back.

## G. Resolved stale-packet suppression

- [ ] SOS X relays.
- [ ] Responder resolves X.
- [ ] STATUS RESOLVED X propagates.
- [ ] Active lists update.
- [ ] Old delayed SOS X arrives later.
- [ ] It is suppressed and cannot resurrect incident.

## H. Full-detail enrichment

- [ ] Minimal BLE report reaches backend first.
- [ ] Original Citizen later gets Internet.
- [ ] Description/subtype/full metadata sync.
- [ ] Existing report becomes FULL.
- [ ] No duplicate.

---

# 41. Final Definition of Done

Do **not** consider the repository updated until all of these are true:

- [ ] Citizen report is separate from quick SOS.
- [ ] No image reporting exists in v1.
- [ ] No typed address is required.
- [ ] Offline map pin works without GPS/Internet.
- [ ] Cached Barangay fallback works.
- [ ] 21-byte v2 emergency packet implemented.
- [ ] 13-byte ACK/STATUS implemented.
- [ ] Precise E7 coordinates implemented.
- [ ] Location source implemented.
- [ ] Citizen silent background relay enforced.
- [ ] Responder/Admin visibility enforced.
- [ ] Persistent dedup implemented.
- [ ] Fair relay scheduler implemented.
- [ ] Terminal status/tombstone implemented.
- [ ] Resolved emergency cannot reappear from stale same-token packet.
- [ ] Room destructive migration removed.
- [ ] Offline catalogs bundled.
- [ ] Offline map bundled/cached.
- [ ] Backend report/incident normalization completed.
- [ ] Backend routing no longer depends on BLE target agency ID.
- [ ] Gateway minimal → full enrichment works.
- [ ] All backend tests pass.
- [ ] Frontend build passes.
- [ ] Android unit/compile checks pass.
- [ ] Real-device 3-phone relay/ACK/resolution demo passes.
