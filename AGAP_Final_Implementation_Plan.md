# AGAP — Final Implementation Plan: BLE, Offline Map Pinning, Location Fallback, Resolution Propagation, Background Relay and Cached-System Architecture

> **Revision focus:** Finalize AGAP's compact BLE packet design, remove image/media transmission, add offline map support, cache system catalogs on-device, and make the Android app capable of decoding and displaying emergency information with zero Internet connectivity.
>
> **Target:** Codex implementation plan  
> **Website:** Admin only  
> **Android:** Citizen, Responder, Admin  
> **Primary path:** Internet / Wi-Fi  
> **Fallback path:** BLE store-carry-forward  
> **BLE media:** Compact telemetry only — **NO images, video, audio, or long text**  
> **Offline map:** Preloaded/cached deployment-area map data  
> **Offline system data:** Preloaded + synchronized catalogs stored locally

---


# 0. Feature Summary

## 0.1 Citizen Android Features

Citizens are **reporters and background relay nodes only**.

They can:

```text
Register / login
View account verification state
Send SOS
Report an incident involving another person
Report a public/unknown emergency hazard
Choose current GPS, drop a pin on the cached offline map, or use a cached Barangay fallback
Enter a short description
Set emergency type/subtype
Set subject relation
Set immediate-danger flag
Enter estimated people affected
View only their own submitted emergency/report status
Receive ACK/status only for their own report token
Maintain local offline report history
Automatically participate in BLE background relaying
Synchronize their own pending reports when Internet returns
Cache system catalogs and offline map data
```

Citizens **cannot**:

```text
See emergency messages/reports belonging to other Citizens
Open details of relayed emergencies
See other Citizens' coordinates
See other Citizens' emergency type or description in the UI
See responder/admin operational alerts
Acknowledge another Citizen's emergency
Dispatch units
Change incident status
Manually browse the BLE relay queue
Manually choose which other emergency to relay
```

When a Citizen phone receives another person's BLE emergency:

```text
Receive packet in background
        ↓
Validate
        ↓
Deduplicate
        ↓
Store minimal relay state
        ↓
Schedule automatic rebroadcast
        ↓
Do NOT show the emergency to the Citizen
```

The Citizen device acts as a **silent background carrier**.

---

## 0.2 Responder Android Features

Responders are allowed to see operational emergency messages relevant to their role.

They can:

```text
Receive and view BLE/online emergency alerts
See emergency type
See subject relation
See immediate-danger status
See incident coordinates
Resolve barangay offline
Open the incident on the offline map
See short description when it has synchronized online
See report/incident status
See assigned dispatch
Acknowledge an emergency
Send predefined ACK/status messages
Update EN_ROUTE
Update ON_SCENE
Resolve assigned incidents
Automatically remove/mark a resolved incident from the active-emergency view
Broadcast/relay terminal RESOLVED or CANCELLED status for the same report token
Participate in BLE background relaying
Upload cached BLE emergencies when Internet becomes available
View responder-relevant cached agency/routing data
```

Responder phones may still relay emergencies that are not relevant to their own agency, but those unrelated packets should be handled silently in the background.

---

## 0.3 Admin Android Features

Admin Android is a field-oriented operational client.

Admin can:

```text
See all active emergency alerts
See BLE-origin and online-origin incidents
See emergency coordinates
Open offline map
Resolve barangay offline
See report/incident details
Acknowledge incidents
View routing
Dispatch available units
View responder status
Resolve/cancel incidents and propagate terminal status
Verify accounts
Participate in BLE background relaying
Act as an Internet gateway for cached BLE packets
```

---

## 0.4 Admin Website Features

The website is **Admin only**.

It includes:

```text
Admin authentication
Dashboard
Live incident monitoring
Incident list
Incident detail
Canonical incident timeline
Citizen report list per incident
Online/BLE source visibility
Agency routing
Primary/support agency assignment
Dispatch management
Responder management
Rescue-unit management
User verification
Agency management
Emergency-type management
Predefined-message management
Reports and analytics
Audit logs
Catalog/version management
Offline-map package metadata/version management
```

---

## 0.5 BLE / Offline Features

```text
21-byte SOS / INCIDENT_REPORT packet
13-byte ACK / STATUS packet
6-byte random report transport token
32-bit E7 latitude
32-bit E7 longitude
Original creation time
Subject relation
Immediate-danger flag
Hop count
CRC-16
Persistent duplicate suppression
Time-bounded relaying
Hop limit
Randomized relay backoff
Fair multi-packet scheduling
Multi-hop ACK
ACK stops original emergency relay
STATUS RESOLVED/CANCELLED stops relay
Gateway uplink when any relay phone gets Internet
Silent Citizen background relaying
Responder/Admin operational alert display
Offline map
Offline barangay resolution
Offline cached Barangay selector with fixed representative coordinates
Same-token location refinement when a better location becomes available
RESOLVED/CANCELLED terminal status suppresses stale copies of the same emergency
Automatic active-alert status update on Responder/Admin phones
Bundled initial system catalogs
Versioned catalog synchronization
Versioned map package synchronization
```

---

## 0.6 Visibility Rule

This is a core privacy and UX rule:

```text
CITIZEN
    ├── Can see own reports
    ├── Can see ACK/status for own report token
    └── Cannot see other relayed emergencies

RESPONDER
    ├── Can see relevant operational emergencies
    └── Silently relays unrelated emergency traffic

ADMIN
    └── Can see all operational emergencies
```

The BLE transport layer and the UI layer must therefore be separate.

Receiving a BLE packet does **not** automatically mean the logged-in user is allowed to view it.


# 1. Final Architectural Principle

AGAP must be designed so that **BLE carries only information that changes per emergency**, while the Android phone already stores the static information needed to understand that packet.

The architecture is:

```text
STATIC / CACHED ON PHONE
--------------------------------
Emergency type definitions
Emergency subtype definitions
Predefined response messages
Agency types
Agencies
Agency service areas
Barangays
Barangay boundaries
Offline map data
Protocol-code definitions

                +

DYNAMIC BLE PACKET
--------------------------------
Packet type
Emergency type code
Subject relation
Immediate-danger flag
Report token
Incident coordinates
Creation time
Hop count
CRC

                ↓

OFFLINE DECODER
                ↓
LOCAL DATA ENRICHMENT
                ↓
OFFLINE MAP + HUMAN-READABLE ALERT
```

This means a responder can receive a compact BLE packet and still display:

```text
ACCIDENT
Another person involved
Immediate danger
Barangay ...
13.xxxxxx, 123.xxxxxx
Reported at ...
```

without Internet.

---

# 2. Core Design Decision — No Image Transmission

AGAP v1 must **not transmit or store report images as part of the emergency-report feature**.

Remove from scope:

```text
Image attachment
Video
Audio
Camera capture for reports
Gallery picker
Media upload queue
Media BLE transfer
Attachment database tables
Admin media viewer
```

Reasons:

```text
BLE airtime cost
battery cost
storage cost
sync complexity
privacy risk
server-storage overhead
larger implementation scope
unnecessary for first-response routing
```

If media is added in a future version, it must be an **Internet-only optional extension** and must never block the emergency telemetry path.

---

# 3. Correct Technical Description of the BLE Network

AGAP should not describe its fallback transport as if it were a conventional routed mesh.

Use:

> **BLE Store-Carry-Forward Delay-Tolerant Relay**

or:

> **BLE Opportunistic Store-Carry-Forward Network**

A phone does not know a path toward the final responder.

Instead:

```text
Phone A stores packet
      ↓
Phone B receives packet
      ↓
Phone B carries packet
      ↓
Phone B encounters Phone C
      ↓
Phone C receives packet
      ↓
...
```

There is no BLE routing table such as:

```text
Responder is 3 hops through Device D
```

Therefore the packet should not rely on a hardcoded destination agency ID for routing.

---

# 4. Remove Target Agency ID From BLE Routing

Do not put the backend's `agency_id` inside the BLE emergency packet.

The BLE packet should answer:

```text
WHAT happened?
WHERE did it happen?
WHEN did it happen?
WHICH report is this?
WHO/WHAT needs help?
IS immediate danger indicated?
HOW many relay hops?
IS the packet intact?
```

Agency selection happens later.

## Offline responder encounter

```text
BLE emergency
      ↓
Emergency type
      ↓
Cached responder/agency capability
      ↓
Is this relevant to this responder?
```

Example:

```text
FIRE
  ↓
BFP / fire responder
  ↓
show local emergency alert
```

An unrelated device still relays the packet.

## Internet gateway encounter

```text
BLE emergency
      ↓
Any AGAP phone gets Internet
      ↓
Backend
      ↓
Emergency type + coordinates
      ↓
Agency service-area rules
      ↓
Primary/support agencies
```

This supports one incident requiring several agencies.

---

# 5. Server Remains Authoritative for Routing

Cached routing data on Android is a fallback.

When Internet is available:

```text
SERVER ROUTING = authoritative
```

When Internet is unavailable:

```text
LOCAL CACHED ROUTING = best-effort fallback
```

This prevents stale phones from overriding newer server configuration.

---

# 6. Stable Protocol Codes, Not Database Primary Keys

Never send arbitrary SQL primary keys inside BLE.

Bad:

```text
agency_id = 47
barangay_id = 93
emergency_type row id = 12
```

Those values can differ across databases or versions.

Instead define stable protocol codes.

Example:

```text
Emergency Type Protocol Codes
-----------------------------
0 = UNKNOWN
1 = MEDICAL
2 = ACCIDENT
3 = FIRE
4 = DISASTER
5 = CRIME
```

These codes are part of:

```text
docs/ble-protocol-v2.md
```

and must not be changed casually.

The local database may map them to its own rows.

Example:

```text
protocol_code 2
      ↓
cached_emergency_types
      ↓
ACCIDENT
```

---


# 6A. Final Citizen Location UX — No Typed Address Required

The normal Citizen emergency/report workflow must **not require typing a street address**.

The location choices are:

```text
1. USE MY CURRENT LOCATION
   → GPS/GNSS

2. CHOOSE ON OFFLINE MAP
   → pan/zoom cached map
   → tap or long-press
   → drop incident pin
   → obtain precise latitude/longitude from map

3. SELECT BARANGAY
   → cached local Barangay list
   → approximate fallback only

4. SEND WITHOUT LOCATION
   → urgent last resort
```

Priority:

```text
GPS
 ↓ if unsuitable/unavailable
OFFLINE MAP PIN
 ↓ if Citizen cannot identify exact point
CACHED BARANGAY
 ↓ if even area is unknown
UNKNOWN
```

## Offline Map Pin Is Fully Offline

A map pin does not require GPS or Internet when the map package is already stored on the device.

```text
No mobile load
No Internet
No GPS fix
Cached offline map available
        ↓
Citizen pans/zooms map
        ↓
Citizen taps incident location
        ↓
Map converts selected point to latitude/longitude
        ↓
location_source = MAP_PIN
        ↓
coordinates go into BLE packet
```

The selected coordinates remain precise numeric coordinates, but they are **Citizen-selected**, not GNSS-measured.

Therefore:

```text
location_source = MAP_PIN
gps_accuracy     = unknown / 255
```

Responder/Admin UI should say:

```text
Citizen-selected incident location
```

rather than displaying a fake GPS accuracy.

## Address Text

The Citizen may optionally enter a **landmark/description** in the full report:

```text
"Near the school gate"
"Beside the market"
"At the bridge"
```

This text is:

```text
local/full Internet report data only
```

It is not required for location and is not sent through the compact BLE packet.

The authoritative emergency location remains:

```text
location_source
+
latitude
+
longitude
```

or, for coarse fallback:

```text
BARANGAY_REFERENCE
```

Do not add a required free-text street-address field to the emergency flow.

---

# 7. Location Must Be Usable Offline

Every emergency packet contains coordinate fields, but the receiver must also know **how those coordinates were obtained**.

AGAP supports four protocol-level location sources:

```text
GPS
MAP_PIN
BARANGAY_REFERENCE
UNKNOWN
```

The BLE packet does **not** carry full address text such as:

```text
"Barangay 18, Legazpi City"
"Rizal Street"
"Near XYZ Building"
```

Instead:

```text
GPS / selected location
        ↓
coordinates + location-source code
        ↓
BLE
        ↓
receiver's cached geography
        ↓
barangay / municipality
        ↓
offline map
```

## GPS

Use the actual phone GNSS/location fix.

```text
location_source = GPS
coordinates      = precise E7 location
accuracy          = Android-reported horizontal accuracy
```

## MAP_PIN

The Citizen deliberately selects the incident point on the cached offline map.

```text
location_source = MAP_PIN
coordinates      = selected map position
accuracy          = 255 / unknown-manual
```

This is especially useful when the Citizen is reporting an incident that is not at their own current location.

## BARANGAY_REFERENCE

If GPS is unavailable or the Citizen only knows the Barangay, the app can display a locally cached Barangay list downloaded/seeded from the AGAP database.

Each cached Barangay has a fixed **representative point**:

```text
barangay_code
barangay_name
reference_latitude_e7
reference_longitude_e7
boundary_data
```

The representative point must be deliberately selected to lie **inside the Barangay polygon**. Do not assume a mathematical polygon centroid is always inside a concave Barangay boundary.

When the Citizen selects a Barangay:

```text
location_source = BARANGAY_REFERENCE
coordinates      = Barangay representative point
accuracy          = 255 / approximate-area
```

These coordinates are **not the exact incident position**. They identify the approximate Barangay area so the emergency can still be routed and displayed offline.

Responder/Admin UI must clearly label it:

```text
Approximate location: Barangay X
Exact incident point unavailable
```

When offline map boundaries are available, highlight the Barangay polygon instead of presenting the representative point as an exact emergency marker.

## UNKNOWN

If no GPS, map pin, or Barangay fallback is available:

```text
location_source = UNKNOWN
latitude_e7      = 0
longitude_e7     = 0
accuracy         = 255
```

Receivers must interpret `UNKNOWN` as **no usable location** and must never plot `(0,0)` as the incident.

The SOS may still be sent.

This avoids delaying or losing an emergency solely because location is unavailable.

---


# 8. Offline Map Requirement

The responder/admin Android app must be capable of displaying emergency coordinates on a map without Internet.

There are two separate offline data concerns:

## 8.1 Offline visual map

Contains:

```text
roads
street geometry
water
land
labels
points of interest as available
```

Recommended deployment scope:

```text
Legazpi City
+
small surrounding buffer
```

Do not package the entire Philippines in the first version.

## 8.2 Offline geographic/reference data

Contains:

```text
barangay names
barangay codes
barangay boundaries/polygons
municipality
province
agency coordinates
agency service areas
```

This data allows:

```text
latitude + longitude
      ↓
point-in-polygon
      ↓
Barangay
```

even if the visual map layer is temporarily unavailable.

---

# 9. Recommended Offline Map Storage

Keep offline map data separate from Room when practical.

Recommended structure:

```text
Android app storage
│
├── Room DB
│   ├── catalog metadata
│   ├── emergency types
│   ├── agencies
│   ├── service areas
│   ├── barangay metadata
│   └── report / relay state
│
└── offline_map/
    ├── deployment.mbtiles
    └── version metadata
```

A package such as:

```text
MBTiles / offline vector tiles
```

is preferable to storing every rendered map tile as database rows.

The exact map renderer may be chosen during implementation.

---

# 10. Initial Offline Data Must Ship With the App

A fresh AGAP installation should not require Internet before it can understand emergency packets.

Bundle initial data with the APK/app package:

```text
Emergency types
Predefined message codes
Agency types
Initial agencies
Agency service areas
Barangays
Barangay boundaries
Offline deployment map
```

On first run:

```text
Bundled seed data
      ↓
Room / local files
      ↓
AGAP immediately offline-capable
```

Then online synchronization updates the data.

---

# 11. Versioned Cached Data

Every synchronized static dataset must have a version.

Recommended version groups:

```text
catalog_version
geography_version
agency_version
routing_version
map_version
protocol_catalog_version
```

Example local metadata:

```text
catalog_version   = 27
geography_version = 5
agency_version    = 12
routing_version   = 9
map_version       = 3
```

Server bootstrap response:

```json
{
  "versions": {
    "catalog": 28,
    "geography": 5,
    "agency": 12,
    "routing": 10,
    "map": 3
  }
}
```

Android compares versions and downloads only changed resources.

---

# 12. Cached-System Room Tables

Recommended static/cached tables:

```text
cached_emergency_types
cached_emergency_subtypes
cached_predefined_messages
cached_agency_types
cached_agencies
cached_agency_service_areas
cached_barangays
cached_catalog_versions
```

Operational/local tables remain separate:

```text
local_incidents
local_incident_reports
relay_queue
seen_mesh_packets
mesh_delivery_events
pending_sync
```

Do not mix reference catalogs with transport queue state.

---

# 13. `cached_catalog_versions`

```text
cached_catalog_versions
-----------------------
catalog_name PK
version
updated_at
source
```

Example:

```text
EMERGENCY_TYPES | 28 | ...
AGENCIES        | 12 | ...
GEOGRAPHY       | 5  | ...
ROUTING         | 10 | ...
MAP             | 3  | ...
```

---

# 14. `cached_emergency_types`

```text
cached_emergency_types
----------------------
protocol_code PK
server_id NULL
code
name
description
default_priority
is_active
catalog_version
```

Important:

```text
protocol_code
```

is what BLE uses.

`server_id` is only for API/database synchronization.

---

# 15. `cached_emergency_subtypes`

Subtypes are not transmitted in BLE v2.

They are used for:

```text
full online reports
local detailed report UI
later synchronization
```

Schema:

```text
cached_emergency_subtypes
-------------------------
server_id PK
emergency_protocol_code
code
name
priority_adjustment
is_active
catalog_version
```

---

# 16. `cached_predefined_messages`

```text
cached_predefined_messages
--------------------------
protocol_code PK
server_id NULL
code
message_en
message_fil
message_bikol
message_type
is_active
catalog_version
```

Example:

```text
1 = SOS_RECEIVED
2 = HELP_EN_ROUTE
3 = RESCUE_DISPATCHED
4 = UNIT_EN_ROUTE
5 = UNIT_ARRIVING
6 = INCIDENT_RESOLVED
```

BLE ACK/STATUS carries only the stable protocol code.

---

# 17. `cached_agency_types`

```text
cached_agency_types
-------------------
protocol_code PK
server_id NULL
code
name
is_active
catalog_version
```

Example stable codes:

```text
1 = DRRMO
2 = RESCUE
3 = POLICE
4 = FIRE
5 = MEDICAL
```

These may be used locally for responder relevance filtering.

They are **not required in every emergency packet**.

---

# 18. `cached_agencies`

```text
cached_agencies
---------------
server_id PK
code
name
agency_type_protocol_code
address_text NULL
latitude
longitude
contact_number NULL
is_active
catalog_version
```

This is local reference data.

Do not send `server_id` over BLE.

---

# 19. `cached_agency_service_areas`

```text
cached_agency_service_areas
---------------------------
agency_server_id
barangay_code
routing_role
routing_priority
catalog_version

PRIMARY KEY (agency_server_id, barangay_code)
```

This allows offline best-effort routing.

---

# 20. `cached_barangays`

```text
cached_barangays
----------------
barangay_code PK
name
municipality_code
municipality_name
province_code
province_name

reference_latitude_e7
reference_longitude_e7

boundary_data
geography_version
```

The fixed:

```text
reference_latitude_e7
reference_longitude_e7
```

are the fallback coordinates used only when a Citizen explicitly selects that Barangay because a more exact incident position is unavailable.

Requirements:

```text
representative point must lie inside the Barangay polygon
representative point is stable for a geography version
representative point is not presented as an exact address
```

`boundary_data` may be:

```text
compact GeoJSON
encoded polygon
or reference to a bundled geography file
```

Do not store huge visual map-tile data in this table.

---


# 21. Offline Geographic Resolution and Barangay Selection

Add:

```text
GeoResolver
BarangaySelector
```

## GeoResolver

Responsibility:

```text
latitude + longitude
      ↓
load local Barangay boundaries
      ↓
point-in-polygon
      ↓
Barangay result
```

Return:

```text
barangay code
barangay name
municipality
province
found flag
```

If no polygon matches:

```text
display raw coordinates
```

Never fail emergency handling because geographic enrichment failed.

## BarangaySelector

Must work with zero Internet.

The Citizen can search/select:

```text
Province
  ↓
Municipality / City
  ↓
Barangay
```

For the initial Legazpi deployment, the UI may start directly with the locally cached Legazpi Barangay list.

Selection returns:

```text
barangay_code
barangay_name
reference_latitude_e7
reference_longitude_e7
```

The selector uses only cached/bundled data.

No Internet lookup is required during an SOS.

---


# 21A. Precise Location Acquisition and Fallback Policy

Packet precision alone is not enough. AGAP must obtain the best practical event location without delaying an urgent SOS for too long.

## SOS Location Flow

Recommended behavior:

```text
Citizen presses SOS
        ↓
create report token immediately
        ↓
save SOS locally immediately
        ↓
check recent high-quality location
        ↓
good recent fix?
   /            \
 YES             NO
  ↓               ↓
use GPS      request high-accuracy GNSS fix
                  ↓
             wait only briefly
                  ↓
             best fix available?
               /        \
             YES         NO
              ↓           ↓
           use GPS    allow cached fallback:
                      - offline map pin
                      - Barangay
                      - or UNKNOWN
        ↓
encode BLE packet
        ↓
broadcast
```

Recommended initial GPS thresholds:

```text
preferred location age      ≤ 30 seconds
preferred horizontal error  ≤ 20 meters
high-accuracy wait timeout   3–5 seconds
```

These are configurable implementation defaults, not hard protocol limits.

If the best GPS fix is ±40 m:

```text
send it
+
include accuracy = 40
```

Do not refuse an urgent SOS because GPS is not perfect.

## Detailed Incident Report

The Citizen may choose:

```text
current GPS
offline map pin
cached Barangay
```

because the incident may be somewhere other than the Citizen's current position.

## GPS Failure

If GPS/location services cannot provide a useful fix, the app should offer:

```text
SELECT LOCATION ON OFFLINE MAP
SELECT BARANGAY
SEND WITHOUT LOCATION
```

The Barangay list is local and does not require mobile data.

## Same-Token Location Refinement

The first emergency transmission must not become permanently stuck with a poor or missing location.

A later packet using the **same report token** may improve location information.

Allowed refinement examples:

```text
UNKNOWN
   ↓
BARANGAY_REFERENCE

UNKNOWN
   ↓
GPS

BARANGAY_REFERENCE
   ↓
GPS

BARANGAY_REFERENCE
   ↓
MAP_PIN

GPS ±80 m
   ↓
GPS ±6 m
```

Do not treat those as duplicate incidents.

Update the same local/server report token and map marker.

Do **not** automatically overwrite an explicit `MAP_PIN` with the phone's later GPS, because the Citizen may be reporting an incident at a different location.

Suggested refinement rules:

```text
UNKNOWN → any known source                  ACCEPT
BARANGAY_REFERENCE → GPS or MAP_PIN         ACCEPT
GPS → GPS with materially better accuracy   ACCEPT
MAP_PIN → only explicit Citizen correction  ACCEPT
exact same/poorer information               IGNORE AS DUPLICATE
```

## Relay Devices

A relay phone must never replace the event coordinates with its own location.

Relay-device coordinates, if collected for telemetry, belong only in:

```text
mesh_delivery_events
```

The event's coordinates and location source remain tied to the report token across relays.

---


# 22. Offline Map Display by Location Source

After decoding the packet:

```text
location_source
      ↓
display policy
```

## GPS

```text
plot exact decoded coordinate marker
show horizontal accuracy
optionally draw accuracy circle
```

Example:

```text
13.1391234, 123.7438567
Accuracy: ±5 m
```

## MAP_PIN

```text
plot selected coordinate marker
label:
"Citizen-selected incident point"
```

Do not display GPS accuracy because it is a manual point.

## BARANGAY_REFERENCE

```text
resolve representative coordinate
      ↓
identify cached Barangay polygon
      ↓
highlight Barangay area
```

UI:

```text
Approximate location: Barangay X
Exact incident point unavailable
```

The representative point may be used to center the offline map, but it must not visually imply meter-level incident precision.

## UNKNOWN

```text
do not plot 0,0
show:
"Location unavailable"
```

ACK, dispatch, and relay behavior must continue even when location is unavailable.

---


# 23. Offline Map Availability States

The app should distinguish:

```text
MAP_READY
MAP_UPDATE_AVAILABLE
MAP_MISSING
MAP_CORRUPT
```

If map data is unavailable:

```text
still display coordinates
still display locally resolved barangay if possible
still allow ACK / response
```

Map failure must not break emergency processing.

---

# 24. Map Update Strategy

Do not update the offline map through BLE.

Map updates happen only through Internet.

Flow:

```text
Internet available
      ↓
GET map metadata
      ↓
map_version changed?
      ↓ yes
download new map package
      ↓
verify checksum
      ↓
atomic replace
```

Do not overwrite the working map file until the new package is fully downloaded and verified.

---


# 24A. Offline Map Location Picker

Create a dedicated reusable component:

```text
OfflineLocationPicker
```

It must support:

```text
initial camera center
pan
zoom
tap / long-press to drop pin
move/replace pin
confirm selected location
cancel
display selected latitude/longitude
resolve selected point to cached Barangay
```

Recommended result model:

```text
SelectedIncidentLocation
------------------------
source = MAP_PIN
latitude
longitude
barangay_code NULL
barangay_name NULL
selected_at
```

When the Citizen taps the map:

```text
screen coordinate
      ↓
MapView projection
      ↓
GeoPoint
      ↓
latitude / longitude
      ↓
GeoResolver
      ↓
optional Barangay label
```

The map picker must use local map data only while offline.

It must not silently contact an online tile server merely because network connectivity happens to exist.

For production/emergency mode, map rendering should prefer the managed offline map package.

---

# 25. Cached Catalog Update Strategy

Use:

```text
bootstrap
+
version checks
+
incremental sync where practical
```

Recommended endpoints:

```http
GET /api/v1/sync/bootstrap
GET /api/v1/catalog/emergency-types
GET /api/v1/catalog/messages
GET /api/v1/catalog/agencies
GET /api/v1/catalog/geography
GET /api/v1/catalog/routing
GET /api/v1/map/metadata
```

For the first version, a single bootstrap package may be easier.

---

# 26. Offline Map / Catalog Integrity

Each downloadable package should include:

```text
version
size
SHA-256 checksum
generated_at
```

Android:

```text
download
      ↓
hash file
      ↓
matches server checksum?
      ↓
yes → activate
no  → discard
```

---


# 26A. Background Relay and Role-Based Visibility

BLE reception must run independently of the emergency-alert UI.

The architecture is:

```text
BLE Scanner
    ↓
Packet Processor
    ↓
Validation / CRC
    ↓
Deduplication
    ↓
Persistent Seen State
    ↓
Role Policy
    ├── CITIZEN
    │     └── silent relay only
    │
    ├── RESPONDER
    │     ├── show if operationally relevant
    │     └── otherwise silent relay
    │
    └── ADMIN
          └── show operational alert
    ↓
Relay Queue
```

## Citizen Behavior

For a packet that does **not** belong to the Citizen:

```text
Citizen phone receives emergency token X
        ↓
validate X
        ↓
store seen state
        ↓
enqueue for background relay
        ↓
NO notification
NO incident card
NO map marker
NO emergency type shown
NO coordinates shown
```

The only Citizen-facing exception is when the packet refers to one of the Citizen's own locally created report tokens.

Example:

```text
Citizen owns token A73C91F2084D
        ↓
receives ACK token A73C91F2084D
        ↓
show:
"Your emergency was received."
```

If the Citizen receives:

```text
ACK token B112...
```

which belongs to someone else:

```text
relay silently
```

## Responder Behavior

Responder receives SOS/REPORT:

```text
decode
      ↓
check emergency type against cached responder/agency capability
      ↓
relevant?
   /        \
 YES         NO
  ↓           ↓
show alert   silent relay
```

A responder should not be prevented from relaying a medical/fire/crime packet merely because it is outside their own operational specialization.

## Admin Behavior

Admin may view all valid operational emergency packets received by the device.

Admin still participates in the same relay process.

## Background Execution

The relay engine must be implemented as an Android background-capable component using platform-compliant mechanisms.

Recommended responsibilities:

```text
Foreground BLE relay service where Android requires it
BLE scanner
BLE advertiser
relay scheduler
persistent relay queue
persistent seen-token store
network monitor
gateway uploader
ACK/status processor
```

The service should continue relaying while the app UI is not open, subject to Android OS limitations and required permissions.

Do not promise "always running forever."

The UI should clearly indicate when the AGAP relay service is:

```text
ACTIVE
PAUSED
BLUETOOTH OFF
PERMISSION REQUIRED
BATTERY/OS RESTRICTED
```

## Privacy Rule

Citizen devices may temporarily store the minimum packet bytes needed for forwarding, but the Citizen UI must not expose another person's emergency.

Do not store another Citizen's:

```text
name
account profile
phone number
description
medical information
full server report
```

on a relay-only Citizen phone.

The local relay store should contain only transport-level data required for forwarding and deduplication.


# 27. BLE Packet Design — Final v2 Emergency Format

Use a **21-byte emergency packet**.

The coordinates must preserve substantially more precision than the phone's real GPS accuracy so that BLE encoding is never the main source of location error.

Use Android-style **E7 coordinates**:

```text
encoded_latitude  = round(latitude  × 10,000,000)
encoded_longitude = round(longitude × 10,000,000)
```

Each value is stored as a signed 32-bit integer.

Example:

```text
13.1391234  → 131391234
123.7438567 → 1237438567
```

This gives a coordinate resolution of approximately:

```text
0.0000001 degree
≈ 1.1 centimeters latitude
```

Actual emergency-location accuracy will normally be limited by the phone's GNSS/location fix, often several meters, not by BLE encoding.

| Byte(s) | Size | Field |
|---|---:|---|
| `0` | 1 | Version + packet type + emergency type + immediate-danger bit |
| `1` | 1 | Subject relation + hop count + location source |
| `2–7` | 6 | Report transport token |
| `8–11` | 4 | Latitude E7 (`int32`) |
| `12–15` | 4 | Longitude E7 (`int32`) |
| `16` | 1 | Location accuracy in meters |
| `17–18` | 2 | Original creation-time slot |
| `19–20` | 2 | CRC-16 |

Total:

```text
21 bytes
```

No:

```text
Reporter user ID
Target agency ID
Address ID
Description
Image
Phone number
Full address
Database primary keys
```

## Location Accuracy Byte

Byte `16` tells the receiver how trustworthy the GPS fix is.

Use:

```text
0–254 = estimated horizontal accuracy in meters
255   = unknown / unavailable
```

Examples:

```text
3   → ±3 meters
8   → ±8 meters
25  → ±25 meters
255 → accuracy unavailable
```

If Android reports an accuracy larger than 254 meters:

```text
encode 254
```

and keep the exact accuracy in the local/full online report.

This allows the responder to distinguish:

```text
📍 13.1391234, 123.7438567
Accuracy: ±4 m
```

from:

```text
📍 13.1391234, 123.7438567
Accuracy: ±85 m
```

Both coordinates may contain many decimal digits, but the second fix is physically less reliable.



# 28. Byte 0 Bit Layout

Recommended:

```text
bits 7..6 = protocol version
bits 5..4 = packet type
bits 3..1 = emergency type
bit 0     = immediate danger
```

Packet type:

```text
00 = SOS
01 = INCIDENT_REPORT
10 = ACK
11 = STATUS
```

Emergency type:

```text
000 = UNKNOWN
001 = MEDICAL
010 = ACCIDENT
011 = FIRE
100 = DISASTER
101 = CRIME
```

ACK/STATUS may interpret the lower emergency bits as reserved/zero.

---

# 29. Byte 1 Bit Layout

```text
bits 7..6 = subject relation
bits 5..2 = hop count
bits 1..0 = location source
```

Subject:

```text
00 = SELF
01 = OTHER_PERSON
10 = PUBLIC_OR_UNKNOWN
11 = UNKNOWN / RESERVED
```

Hop:

```text
0..15
```

Location source:

```text
00 = GPS
01 = MAP_PIN
10 = BARANGAY_REFERENCE
11 = UNKNOWN
```

This uses the two previously reserved bits without increasing packet size.

---


# 30. Report Transport Token

Use exactly:

```text
6 random binary bytes
```

Example:

```text
A7 3C 91 F2 08 4D
```

Human display:

```text
A73C91F2084D
```

Bluetooth transmits only:

```text
6 bytes
```

Generate using:

```text
SecureRandom
```

Do not use:

```text
textual hash substring
database user ID
database report ID
```

---

# 31. Why 6 Bytes

A 48-bit token provides:

```text
2^48
= 281,474,976,710,656 possible tokens
```

This is a strong compromise for the expected local/prototype scale while keeping the packet short.

The server must still enforce:

```text
UNIQUE transport_token
```

and handle the extremely unlikely collision case safely.

---

# 32. Coordinate Encoding — High Precision

Do **not** compress latitude/longitude to 16-bit or 24-bit values in the final protocol.

Use signed 32-bit **E7 fixed-point coordinates**:

```text
latE7 = round(latitude * 10^7)
lonE7 = round(longitude * 10^7)
```

Examples:

```text
13.1391234  → 131391234
123.7438567 → 1237438567
```

Decoder:

```text
latitude  = latE7 / 10,000,000.0
longitude = lonE7 / 10,000,000.0
```

Advantages:

```text
very high coordinate resolution
simple Android/Go interoperability
no geographic bounding-box dependency
no precision loss significant to emergency response
easy offline-map plotting
easy database storage
```

The BLE packet therefore preserves the coordinate much more precisely than typical phone GNSS can measure it.

The system must also carry the **reported horizontal accuracy** separately so responders know the actual confidence of the fix.



# 33. Coordinate and Location-Source Rules

For every SOS/report, the packet must explicitly identify the location source.

```text
GPS
MAP_PIN
BARANGAY_REFERENCE
UNKNOWN
```

Never silently substitute:

```text
sample Legazpi coordinates
home address coordinates
relay-device coordinates
```

for an emergency location.

A cached home Barangay may be offered as a selectable option, but it must never be assumed to be the incident location without Citizen confirmation.

Encoding rules:

```text
GPS:
  E7 coordinates = measured event/current position
  accuracy = measured meters

MAP_PIN:
  E7 coordinates = Citizen-selected map point
  accuracy = 255

BARANGAY_REFERENCE:
  E7 coordinates = cached representative point
  accuracy = 255

UNKNOWN:
  E7 coordinates = 0,0
  accuracy = 255
  receiver must not plot the coordinates
```

The full Room/server report should also store:

```text
location_source
selected_barangay_code NULL
gps_accuracy_meters NULL
location_timestamp NULL
```

This prevents a Barangay fallback coordinate from later being mistaken for an exact GPS fix.

---


# 34. Original Creation Time

Use a 16-bit creation-time slot.

Recommended:

```text
minute count modulo 65536
```

from a documented AGAP epoch.

Because emergency packets expire within hours, the receiving app can reconstruct the closest plausible timestamp.

The original time must remain unchanged across relays.

Do not replace it with relay receive time.

---

# 35. CRC

Use:

```text
CRC-16/CCITT-FALSE
```

over bytes:

```text
0..18
```

Stored in:

```text
19..20
```

CRC means:

```text
accidental corruption detection
```

It does not mean:

```text
encryption
authentication
anti-forgery
```

---

# 36. ACK/STATUS Format

ACK/STATUS do not need coordinates again.

Use a compact 13-byte response packet.

| Byte(s) | Size | Field |
|---|---:|---|
| `0` | 1 | Version + packet type |
| `1` | 1 | Hop / flags |
| `2–7` | 6 | Original report token |
| `8` | 1 | Message/status protocol code |
| `9–10` | 2 | Creation-time slot |
| `11–12` | 2 | CRC-16 |

Total:

```text
13 bytes
```

This is enough for:

```text
SOS_RECEIVED
HELP_EN_ROUTE
RESCUE_DISPATCHED
UNIT_EN_ROUTE
UNIT_ARRIVING
RESOLVED
```

The receiving phone resolves the code using:

```text
cached_predefined_messages
```

---

# 37. Offline ACK Decode Example

BLE receives:

```text
ACK
token = A73C91F2084D
message code = 2
```

Local lookup:

```text
2
 ↓
cached_predefined_messages
 ↓
HELP_EN_ROUTE
 ↓
"Help is on the way."
```

No Internet required.

---

# 38. ACK Uses Report Token, Not User ID

The ACK should refer to:

```text
report token
```

not:

```text
Citizen account ID
```

Original Citizen phone:

```text
incoming ACK token
       =
my local report token?
```

If yes:

```text
display message
```

If no:

```text
relay if eligible
```

---

# 39. Multi-Hop ACK

ACK must be store-carry-forward relayable.

Example:

```text
Citizen A creates token X
      ↓
Phone B
      ↓
Responder C
      ↓
Responder C creates ACK X
      ↓
Phone B
      ↓
Citizen A
```

Intermediate phones do not need to know who Citizen A is.

---

# 40. ACK Stops Original Emergency Forwarding

If a relay node has:

```text
SOS token X
```

and later receives:

```text
ACK token X
```

it should:

```text
mark SOS/REPORT X = ACKNOWLEDGED
remove the original packet from aggressive active-SOS forwarding
retain deduplication/history state
relay ACK for its ACK lifetime
```

Whether a low-frequency post-ACK status relay remains active is controlled by the relay policy.

---

# 40A. RESOLVED/CANCELLED Is a Terminal Network State

A `STATUS` packet with the same report token can terminate the emergency across the BLE network.

Terminal status codes:

```text
RESOLVED
CANCELLED
```

Example:

```text
SOS token X
      ↓
Responder handles emergency
      ↓
Responder marks RESOLVED
      ↓
local incident status = RESOLVED immediately
      ↓
create STATUS(RESOLVED, token X)
      ↓
BLE store-carry-forward
      ↓
other AGAP devices receive terminal status
```

## On Responder/Admin Phones

When `STATUS RESOLVED` for token X is received:

```text
find active incident/report token X
        ↓
change local value/status to RESOLVED
        ↓
remove from Active Emergencies
        ↓
retain in history as RESOLVED
        ↓
remove SOS/REPORT X from relay queue
```

The same applies to `CANCELLED`.

The UI must update automatically without requiring the user to reopen the incident.

## On the Originating Citizen Phone

If token X belongs to that Citizen:

```text
own report status = RESOLVED
```

and display the appropriate cached message/status.

## On Relay-Only Citizen Phones

Do not show another person's resolution.

Internally:

```text
mark token X terminal
remove X from relay queue
retain terminal deduplication state
```

## Stale SOS Suppression

This is critical.

If a device already knows:

```text
token X = RESOLVED
```

and later receives an older delayed:

```text
SOS/INCIDENT_REPORT token X
```

the device must **not**:

```text
show it again to Admin
show it again to Responder
recreate an active incident
re-enqueue it as a new emergency
```

Instead:

```text
drop/suppress the stale emergency
```

and, if the local terminal-status relay policy requires it, rebroadcast the known terminal `RESOLVED` status to help quench stale copies elsewhere.

## Terminal Tombstone

Persist a small terminal-state record:

```text
transport_token
terminal_status
terminal_at
tombstone_expires_at
```

Keep it longer than the original emergency's normal relay lifetime.

Recommended starting policy for a 2-hour SOS TTL:

```text
terminal tombstone retention = 3 hours after terminal status
```

Make configurable.

This prevents an old store-carried packet from reappearing as active shortly after resolution while avoiding indefinite storage.

## Online Resolution

If an incident is resolved through the backend:

```text
Backend
   ↓
WebSocket/API status update
   ↓
Responder/Admin Android
   ↓
local status becomes RESOLVED
```

A BLE-capable Responder/Admin device that knows token X may then propagate:

```text
STATUS RESOLVED token X
```

into the nearby offline relay network.

The website itself does not transmit BLE; Android devices provide that bridge.

---


# 41. Time-Bounded Relaying

Remove "relay indefinitely."

Initial recommended values:

```text
SOS expiration              = 2 hours
Incident-report expiration  = 2 hours
ACK expiration              = 1 hour
STATUS expiration           = 1 hour
Maximum hop count           = 15
```

Keep configurable.

---

# 42. Persistent Deduplication

Create:

```text
seen_mesh_packets
```

Schema:

```text
seen_mesh_packets
-----------------
transport_token
packet_type
first_seen_at
last_seen_at
highest_hop_seen
expires_at
delivery_state
terminal_status NULL
terminal_at NULL
tombstone_expires_at NULL

PRIMARY KEY (transport_token, packet_type)
```

Possible states:

```text
PENDING
RELAYING
UPLINKED
ACKNOWLEDGED
RESOLVED
EXPIRED
```

Do not rely only on an in-memory `Set`.

---

# 43. Separate Relay Queue

Create:

```text
relay_queue
```

Schema:

```text
relay_queue
-----------
transport_token
packet_type
encoded_payload
priority
origin_local
hop_count
created_at
expires_at
last_advertised_at
next_advertise_at
advertise_count
state
```

Deduplication history and active scheduling are separate responsibilities.

A terminal `RESOLVED` or `CANCELLED` state must remove matching SOS/REPORT entries from `relay_queue` while preserving a terminal tombstone in `seen_mesh_packets`.

---

# 44. Fair Multi-Packet Relay Scheduler

Do not advertise only the latest pending packet.

Use:

```text
priority
+
round-robin fairness
+
randomized backoff
```

Suggested transport priority:

```text
100 local SOS
95  ACK
90  critical STATUS
80  relayed SOS
70  local incident report
60  relayed incident report
10  beacon
```

This is BLE scheduling priority, not medical triage priority.

---

# 45. Random Relay Backoff

When many devices hear the same packet:

```text
do not all rebroadcast instantly
```

Use jitter.

Example first relay:

```text
random 1–5 seconds
```

Later rebroadcasts should also use randomized timing.

Tune empirically.

---

# 46. Relay Aging

Reduce advertisement frequency as a packet ages.

Example policy:

```text
first minute       → frequent
next 10 minutes    → moderate
later              → low frequency
after expiration   → stop
```

A local SOS should still receive stronger priority than relayed background traffic.

---

# 47. Beacon Handling

Beacons are optional.

If kept:

```text
Beacon carries NODE identity
Emergency packet carries REPORT token
```

Never count an emergency token as a nearby-device ID.

If nearby-node count is not essential:

```text
remove it from MVP
```

This reduces BLE airtime.

---

# 48. Full Report Data

The Citizen detailed report may include:

```text
Emergency type
Emergency subtype
Subject relation
Incident location source
Incident coordinates or cached Barangay selection
Observed time
Immediate danger
People affected estimate
Short description
Reporter contact permission
```

No image.

---

# 49. Online Detailed Report

```text
Citizen completes report
      ↓
save locally
      ↓
Internet available
      ↓
HTTPS full report
      ↓
Backend
      ↓
Incident + routing
```

---

# 50. Offline Detailed Report

```text
Citizen completes report
      ↓
save full report locally
      ↓
create 21-byte BLE INCIDENT_REPORT
      ↓
store-carry-forward
```

The BLE packet includes only:

```text
type
subject
urgent flag
token
coordinates
creation time
hop
CRC
```

Locally retain:

```text
description
subtype
people estimate
full timestamps
authenticated reporter identity
```

---

# 51. Later Full Synchronization

When the originating phone regains Internet:

```text
transport_token
+
full report_uuid
+
authenticated Citizen
+
description
+
subtype
+
people estimate
+
full timestamps
      ↓
Backend
```

Server:

```text
find minimal report by transport_token
      ↓
enrich same report
```

Do not create a duplicate incident.

---

# 52. Minimal Gateway Report

If a relay device gets Internet before the original phone:

```text
BLE token X
      ↓
gateway uplink
      ↓
Backend creates:
```

```text
incident_report
---------------
transport_token = X
reporter_user_id = NULL
emergency_type
subject_relation
latitude
longitude
observed_at
origin_channel = BLE_MESH
detail_state = MINIMAL
```

Also create the canonical incident immediately.

Do not wait for the originating Citizen.

---

# 53. Relay Gateway Is Not the Reporter

Store:

```text
gateway_device_id
```

separately.

Do not write:

```text
reporter_user_id = relay user
```

unless that user actually created the emergency.

---

# 54. Revised `incident_reports`

```text
incident_reports
----------------
id
report_uuid NULL
transport_token UNIQUE
incident_id

reporter_user_id NULL
reporter_device_id NULL
gateway_device_id NULL

submission_type
subject_relation

reported_emergency_type_id
reported_emergency_subtype_id NULL

description NULL
people_affected_estimate NULL
immediate_danger
reporter_contact_allowed

location_source
selected_barangay_code NULL
address_id NULL
latitude NULL
longitude NULL
gps_accuracy NULL
location_timestamp NULL

observed_at
origin_channel
detail_state

client_created_at NULL
server_received_at
updated_at
```

`detail_state`:

```text
MINIMAL
FULL
```

---

# 55. Incident vs Report vs Transport

Keep these separate.

```text
REPORT
What a Citizen submitted.

INCIDENT
The canonical real-world emergency.

DISPATCH
Which response resources were assigned.

TRANSPORT
How the emergency moved through Internet/BLE.
```

Relationship:

```text
Citizen Report
      ↓
Canonical Incident
      ↓
Agency Routing
      ↓
Dispatch

Citizen Report
      ↓
BLE Packet
      ↓
Relay Events
```

---

# 56. Offline Emergency Decode Pipeline

```text
BLE advertisement
      ↓
AGAP service identifier matched
      ↓
decode packet
      ↓
CRC valid?
      ↓
terminal tombstone check
      ↓
token deduplication / refinement check
      ↓
creation time valid?
      ↓
protocol emergency code
      ↓
cached_emergency_types
      ↓
human-readable emergency type
      ↓
location source
      ├── GPS
      │      └── precise marker + accuracy
      ├── MAP_PIN
      │      └── Citizen-selected marker
      ├── BARANGAY_REFERENCE
      │      └── resolve/highlight cached Barangay
      └── UNKNOWN
             └── no map marker
```

Before showing an emergency to Responder/Admin:

```text
token terminal?
      ↓ yes
suppress stale SOS/report
```

Citizen relay-only visibility rules still apply.

No Internet is required.

---


# 57. Responder/Admin Offline Alert Example

```text
┌──────────────────────────────────┐
│ ⚠ ACCIDENT                       │
│                                  │
│ Another person needs help        │
│ Immediate danger                 │
│                                  │
│ Barangay Example                 │
│ Legazpi City                     │
│ 13.xxxxxx, 123.xxxxxx            │
│                                  │
│ Reported: 10:05 AM               │
│ Relay hops: 2                    │
│                                  │
│ [ VIEW OFFLINE MAP ]             │
│ [ ACKNOWLEDGE ]                  │
└──────────────────────────────────┘
```

All displayed data can come from:

```text
BLE
+
cached catalogs
+
cached geography
+
offline map
```

---

# 58. Local Offline Agency Interpretation

Responder phone may perform:

```text
Emergency type FIRE
      ↓
cached emergency routing
      ↓
agency type FIRE
```

Coordinates:

```text
13.x, 123.x
      ↓
cached barangay polygon
      ↓
Barangay Y
```

Then:

```text
FIRE
+
Barangay Y
      ↓
cached agency service areas
      ↓
matching local fire agency
```

Use this only as offline guidance.

The server re-evaluates routing when connectivity returns.

---

# 59. Android Components to Add

Recommended:

```text
core/geo/GeoResolver.kt
core/map/OfflineMapManager.kt
core/map/MapPackageManager.kt

data/local/dao/CatalogDao.kt
data/local/dao/GeographyDao.kt
data/local/dao/RoutingDao.kt
data/local/entity/CatalogEntities.kt
data/local/entity/GeographyEntities.kt

data/sync/CatalogSyncManager.kt
data/sync/MapSyncManager.kt

domain/usecase/ResolveEmergencyLocationUseCase.kt
domain/usecase/GetOfflineRoutingUseCase.kt
domain/usecase/RefreshOfflineDataUseCase.kt
```

---

# 60. Existing Android Files to Refactor

Inspect/change:

```text
core/bluetooth/BleMeshPacketCodec.kt
core/bluetooth/BleMeshManager.kt
core/bluetooth/BleMeshService.kt

domain/usecase/SendSosUseCase.kt
domain/usecase/FlushPendingPacketsUseCase.kt

core/util/TargetIdEncoder.kt

data/local/entity/*
data/local/dao/*
data/remote/dto/TelemetryDtos.kt
data/repository/TelemetryRepository.kt
```

Deprecate `TargetIdEncoder` as an emergency routing primitive.

---

# 61. Backend Components to Add

Recommended:

```text
internal/service/catalog_service.go
internal/service/routing_service.go
internal/service/geography_service.go

internal/handler/catalog_handler.go
internal/handler/map_handler.go

internal/repository/catalog.go
internal/repository/routing.go
```

Also maintain canonical protocol code mappings.

---

# 62. Bootstrap API

Recommended endpoint:

```http
GET /api/v1/sync/bootstrap
```

Response contains:

```text
version metadata
emergency types
subtypes
predefined messages
agency types
agencies
agency service areas
barangay metadata
```

Large map package is not included directly in JSON.

Return separate map metadata:

```json
{
  "map": {
    "version": 3,
    "size_bytes": 12345678,
    "sha256": "...",
    "download_required": false
  }
}
```

---

# 63. First-Install Bootstrap

Preferred:

```text
APK bundled initial catalogs/map
      ↓
first run imports local seed
      ↓
app immediately offline-capable
      ↓
if Internet available:
check for newer versions
```

Do not require server bootstrap before the first SOS.

---

# 64. Background Catalog Synchronization

Use WorkManager.

Conditions:

```text
network available
battery reasonable
```

Suggested:

```text
on login
on app startup if stale
periodically
manual admin refresh
```

Do not run aggressive recurring downloads.

---

# 65. Map Package Synchronization

Only check version periodically.

Download only when:

```text
new map version
```

Use:

```text
temporary file
checksum verification
atomic rename
```

Do not replace the working map until verification succeeds.

---

# 66. Room Migration Requirement

Remove destructive migration behavior for operational emergency data.

Use explicit migrations.

Critical requirement:

```text
offline emergency/report survives app update
```

Also ensure cached system data either:

```text
migrates
or can be safely rebuilt from bundled/server data
```

Operational rows must never be destroyed.

---

# 67. Local Data Categories

Treat local data differently.

## Critical operational data

Never destroy:

```text
local incident reports
pending SOS
relay queue
seen packet state
pending sync
ACK/status state
```

## Rebuildable cached reference data

Can be refreshed if necessary:

```text
emergency catalog
agency catalog
routing catalog
geography metadata
map package
```

Even rebuildable data should have safe fallback from bundled defaults.

---

# 68. Protocol Compatibility

Migration period:

```text
decoder supports v1
decoder supports v2
encoder sends v2
```

For v1 packet fields that do not exist:

```text
subject_relation = UNKNOWN
original_time = approximate / receive time
```

Mark lower confidence internally.

Do not invent exact data.

---

# 69. Canonical BLE Specification

Create:

```text
docs/ble-protocol-v2.md
```

It must define:

```text
service UUID
packet size
byte order
bit layout
token generation
coordinate encoding
time-slot encoding
CRC algorithm
packet types
protocol codes
expiration
hop semantics
ACK behavior
STATUS behavior
v1 compatibility
```

No other file should independently redefine the protocol.

---

# 70. Shared Protocol Test Vectors

Create:

```text
docs/ble-protocol-v2-test-vectors.json
```

Example logical vector:

```json
{
  "name": "incident-report-other-accident",
  "packet_type": "INCIDENT_REPORT",
  "emergency_type": "ACCIDENT",
  "subject_relation": "OTHER_PERSON",
  "immediate_danger": true,
  "hop": 0,
  "transport_token_hex": "A73C91F2084D",
  "latitude": 13.1391,
  "longitude": 123.7438,
  "created_at": "...",
  "expected_hex": "..."
}
```

Android and Go must produce the same bytes.

---

# 71. Required Offline Map Tests

Test:

```text
Internet off
map package available
BLE coordinates received
marker appears at decoded location
```

Test:

```text
Internet off
map package missing
BLE coordinates received
coordinates still displayed
ACK still works
```

Test:

```text
coordinates inside known barangay
GeoResolver returns correct barangay
```

Test boundary points.

Test cached Barangay fallback:

```text
GPS unavailable
      ↓
Citizen selects Barangay
      ↓
packet uses BARANGAY_REFERENCE
      ↓
receiver resolves/highlights same Barangay offline
```

Verify the UI labels the location as approximate rather than exact.

Test UNKNOWN location:

```text
location_source = UNKNOWN
coordinates = 0,0
```

Verify `(0,0)` is never plotted.

---

# 72. Required Cached Catalog Tests

Test:

```text
protocol emergency code 2
      ↓
local cache
      ↓
ACCIDENT
```

Test:

```text
ACK message code 2
      ↓
local cache
      ↓
HELP_EN_ROUTE
```

Test stale version refresh.

Test no-Internet first launch with bundled seed.

---

# 73. Required Protocol Tests

Test:

```text
SOS SELF
SOS OTHER_PERSON
INCIDENT_REPORT OTHER_PERSON
INCIDENT_REPORT PUBLIC_OR_UNKNOWN
urgent true/false
hop 0
hop 15
coordinate round-trip
bad CRC
unknown emergency code
unsupported protocol
expired creation time
token preservation
```

Unknown emergency code must become:

```text
UNKNOWN
```

not MEDICAL.

---

# 74. Required Relay Tests

Test:

```text
A → B
A → B → C

duplicate packet
duplicate after app restart

multiple pending packets
fair scheduling

ACK:
C → B → A

ACK stops A's original SOS relay

STATUS RESOLVED stops original relay

Responder/Admin receives RESOLVED for active token
→ local incident automatically becomes RESOLVED
→ disappears from active list
→ remains in history

Citizen relay phone receives RESOLVED for another user's token
→ no UI
→ original relay removed

stale SOS for already RESOLVED token
→ no alert
→ no reactivation
→ no relay as new emergency

same token UNKNOWN location then GPS location
→ update same report, no duplicate incident

same token BARANGAY_REFERENCE then MAP_PIN
→ update same report, no duplicate incident

expired packet is not relayed

gateway upload repeated twice
→ one server report
```

---


## Precise Location Tests

- [ ] Android E7 encoder and Go E7 decoder produce identical coordinates.
- [ ] Go E7 encoder and Android E7 decoder produce identical coordinates.
- [ ] Positive latitude/longitude round-trip correctly.
- [ ] Negative latitude/longitude round-trip correctly.
- [ ] Coordinate round-trip error is no greater than `0.00000005°` from E7 quantization.
- [ ] Accuracy byte `3` displays `±3 m`.
- [ ] Accuracy byte `255` displays `Accuracy unknown`.
- [ ] Relay hop does not alter event latitude/longitude.
- [ ] Relay device location never overwrites incident coordinates.
- [ ] Offline map marker uses decoded event coordinates exactly.
- [ ] Manual map pin uses the selected coordinates and marks location source as `MAP_PIN`.


## Offline Map-Pin Selection Tests

- [ ] Disable mobile data and Wi-Fi.
- [ ] Disable/withhold usable GPS fix.
- [ ] Open `CHOOSE ON OFFLINE MAP`.
- [ ] Cached map still renders.
- [ ] Citizen can pan and zoom.
- [ ] Citizen can tap/long-press to place an incident pin.
- [ ] Selected pin produces deterministic latitude/longitude.
- [ ] Confirmed pin is stored with `location_source = MAP_PIN`.
- [ ] BLE packet carries the selected E7 coordinates.
- [ ] Receiving Responder/Admin plots the same decoded location offline.
- [ ] UI does not label MAP_PIN as a GPS measurement.
- [ ] Selected point is optionally resolved to a cached Barangay locally.
- [ ] No free-text address is required to submit the report.

# 75. Required Gateway + Offline Map Demonstration

Use 3 phones.

## Scenario 1 — Fully offline responder encounter

```text
Phone A Citizen
Internet OFF

Phone B relay
Internet OFF

Phone C Responder
Internet OFF
```

Flow:

```text
A creates ACCIDENT report
      ↓
A BLE
      ↓
B relay
      ↓
C receives
      ↓
C decodes:
ACCIDENT
coordinates
OTHER_PERSON
urgent
      ↓
C local cache:
ACCIDENT label
barangay lookup
      ↓
C offline map:
emergency marker
      ↓
C ACK
      ↓
B relay
      ↓
A receives HELP_EN_ROUTE
```

This is the strongest fully offline demonstration.

---

# 76. Scenario 2 — Gateway Uplink

```text
A Citizen offline
      ↓
BLE token X
      ↓
B relay offline
      ↓
enable Internet on B
      ↓
B uploads token X
      ↓
Backend creates incident
      ↓
Backend routes agencies
      ↓
Admin website updates
      ↓
B creates/receives ACK X
      ↓
B relays ACK
      ↓
A receives ACK
```

---

# 77. Scenario 3 — Full Detail Enrichment

```text
A originally reported offline
      ↓
server already has MINIMAL report via B
      ↓
A later gets Internet
      ↓
full description/subtype sync
      ↓
same transport_token
      ↓
existing report becomes FULL
```

Expected:

```text
1 report
1 incident
not duplicate
```

---

# 78. Codex Implementation Order

Implement in this order.

## Phase 1 — Protocol gap analysis

Before changing code, Codex must inspect:

```text
current Android packet layout
current Go packet layout
current ID generation
target-ID/address-hash usage
dedup logic
ACK relay behavior
pending packet scheduler
nearby-node count
hardcoded coordinates
Room migration behavior
map implementation
current local catalogs
```

---

## Phase 2 — Cached system catalog foundation

Add:

```text
cached_catalog_versions
cached_emergency_types
cached_predefined_messages
cached_agency_types
cached_agencies
cached_agency_service_areas
cached_barangays
```

Seed initial data from bundled assets.

Do not change BLE yet.

---

## Phase 3 — Offline geography

Implement:

```text
GeoResolver
barangay boundary storage
coordinate → barangay lookup
```

Test offline.

---

## Phase 4 — Offline map

Implement:

```text
OfflineMapManager
bundled deployment map
map version metadata
offline marker display
```

Test with Internet disabled.

---

## Phase 5 — BLE v2 codec

Implement the 21-byte emergency packet and 13-byte ACK/STATUS packet in parallel with v1 decoder support.

No Target Agency ID.

No Address ID.

No reporter ID.

No image.

---

## Phase 6 — Persistent relay engine

Implement:

```text
seen_mesh_packets
relay_queue
time expiration
hop limit
priority/fair scheduler
random backoff
multi-hop ACK
STATUS cancellation
```

---

## Phase 7 — Citizen detailed report

Implement:

```text
type
subject
location
time
details
review
```

No photo step.

Offline uses v2 packet.

---

## Phase 8 — Gateway minimal report

Backend accepts v2 decoded telemetry.

Create:

```text
MINIMAL report
canonical incident
agency routing
```

Return ACK data.

---

## Phase 9 — Full report enrichment

Origin phone synchronizes full text and authenticated ownership by transport token.

---

## Phase 10 — Versioned sync

Implement:

```text
bootstrap versions
catalog updates
routing updates
geography updates
map package metadata/update
```

---

## Phase 11 — Cleanup

Remove/deprecate:

```text
TargetIdEncoder for BLE routing
legacy competing packet definition
address hash routing
latest-packet-only scheduler
in-memory-only dedup
destructive Room migration
image/media code if any
```

---

# 79. First Codex Prompt

```text
Read AGAP Revised BLE Packet + Offline Map and Cached-System Architecture Plan completely.

Do not implement code yet.

Inspect the repository and produce an impact/gap report covering:

1. current Android BLE packet byte layout;
2. current Go BLE/compact packet layout;
3. every mismatch between them;
4. current packet/report ID generation;
5. current Target ID and address-hash usage;
6. current packet deduplication;
7. current packet expiration;
8. current multi-packet scheduling;
9. current ACK and STATUS relay behavior;
10. current nearby-device counting logic;
11. current hardcoded/fallback coordinates;
12. current Room schema/version/migration behavior;
13. current offline map implementation, if any;
14. current cached catalogs/reference data;
15. current agency/service-area routing;
16. current Android emergency decode/display path;
17. every affected test.

Also identify whether any existing implementation already supports:
- offline map tiles;
- cached barangay polygons;
- coordinate-to-barangay lookup;
- catalog versioning;
- bundled initial emergency/reference data.

Do not modify code until this report is complete.
```

---

# 80. Second Codex Prompt

```text
Implement the offline cached-system foundation without modifying the BLE protocol yet.

Requirements:
- add cached_catalog_versions
- add cached_emergency_types using stable protocol_code
- add cached_predefined_messages using stable protocol_code
- add cached_agency_types
- add cached_agencies
- add cached_agency_service_areas
- add cached_barangays with:
  - barangay_code
  - name
  - municipality/province fields
  - reference_latitude_e7
  - reference_longitude_e7
  - boundary data
- representative point must be inside its Barangay polygon
- bundle initial seed data with the Android app
- first launch must import bundled seed data
- app must remain capable of decoding protocol codes without Internet
- add explicit Room migrations
- do not use fallbackToDestructiveMigration for critical operational data
- add tests for fresh install with zero Internet
```

---

# 81. Third Codex Prompt

```text
Implement offline geographic, map display, and map-pin selection support.

Requirements:
- add GeoResolver for coordinate-to-Barangay lookup
- add offline BarangaySelector
- add OfflineLocationPicker for Citizen manual incident-point selection
- OfflineLocationPicker must work with zero Internet and without a GPS fix
- tapping/long-pressing the cached map returns precise latitude/longitude
- confirmed manual location uses location_source = MAP_PIN and accuracy = 255
- no required free-text address field
- use locally stored Barangay boundaries and representative coordinates
- add OfflineMapManager
- package an initial deployment-area offline map for Legazpi City plus a small buffer
- emergency coordinates must plot on the local map without Internet
- map failure must not prevent coordinates or ACK actions from displaying
- add local map version metadata
- add checksum verification for future map package updates
- add tests with Internet disabled
```

---

# 82. Fourth Codex Prompt

```text
Implement BLE protocol v2 as a parallel codec.

Emergency packet = 21 bytes:

byte 0:
  version + packet type + emergency type + immediate-danger flag

byte 1:
  bits 7-6 subject relation
  bits 5-2 4-bit hop count
  bits 1-0 location source:
    00 GPS
    01 MAP_PIN
    10 BARANGAY_REFERENCE
    11 UNKNOWN

bytes 2-7:
  6-byte binary report transport token

bytes 8-11:
  signed int32 latitude E7

bytes 12-15:
  signed int32 longitude E7

byte 16:
  horizontal accuracy in meters
  0-254 = meters
  255 = unknown/manual/Barangay-reference

bytes 17-18:
  16-bit original creation-time slot

bytes 19-20:
  CRC-16/CCITT-FALSE

Requirements:
- no Reporter User ID
- no Target Agency ID
- no Address ID/hash
- no image/media
- no description
- no database primary keys
- GPS uses precise E7 coordinates
- MAP_PIN uses selected E7 coordinates and accuracy 255
- BARANGAY_REFERENCE uses cached representative E7 coordinates and accuracy 255
- UNKNOWN uses zeroed coordinates + source UNKNOWN; receiver must never plot 0,0
- unknown emergency code decodes to UNKNOWN
- use SecureRandom for the 6-byte token
- preserve original coordinates/time across relays
- v1 decoder remains supported during migration
- write Android and Go interoperability test vectors
- create docs/ble-protocol-v2.md
```

---

# 83. Fifth Codex Prompt

```text
Implement 13-byte ACK/STATUS packets.

Layout:
byte 0      version + packet type
byte 1      hop / flags
bytes 2-7   original report transport token
byte 8      predefined message/status protocol code
bytes 9-10  creation-time slot
bytes 11-12 CRC-16

Requirements:
- resolve message code using cached_predefined_messages
- ACK/STATUS must relay multi-hop
- original Citizen recognizes ACK by transport token
- intermediate Citizen phones do not display other users' ACKs
- ACK token X stops active SOS/REPORT relay for token X
- RESOLVED/CANCELLED status stops active relay for X
- no receiver user ID in BLE
```

---

# 84. Sixth Codex Prompt

```text
Refactor the BLE relay engine.

Requirements:
- persistent seen_mesh_packets
- separate relay_queue
- maximum hop count 15
- time-based expiry
- randomized relay backoff
- fair priority + round-robin scheduler
- never advertise only the latest packet indefinitely
- token identity must not be counted as nearby node identity
- retain dedup history after successful uplink
- preserve state across app restart
- add starvation, restart, multi-hop ACK, expiry, and cancellation tests
```

---

# 85. Seventh Codex Prompt

```text
Implement the Citizen REPORT AN INCIDENT feature without images.

Required workflow:
1. emergency type
2. optional subtype
3. SELF / OTHER_PERSON / PUBLIC_OR_UNKNOWN
4. current GPS, offline map pin, or cached Barangay
5. observed time
6. immediate danger
7. people affected estimate
8. optional short description
9. review
10. submit

Requirements:
- save locally before network
- online sends full HTTPS report
- offline sends only compact BLE v2 packet
- description/subtype remain local offline
- later full sync enriches the same server report by transport_token
- do not call the existing triggerSos() as the detailed-report implementation
```

---

# 86. Eighth Codex Prompt

```text
Implement versioned catalog/geography/map synchronization.

Requirements:
- /api/v1/sync/bootstrap returns version metadata
- Android compares local/server versions
- changed catalogs refresh independently
- map is a separate downloadable package
- verify map SHA-256 before activation
- atomic replacement of working map
- bundled initial data remains fallback
- server routing remains authoritative when online
- cached routing is best-effort only when offline
```

---


# 86A. Codex Prompt — Background Relay + Role Visibility

```text
Implement role-based BLE visibility and background relaying.

Requirements:

CITIZEN:
- automatically receive, validate, deduplicate, queue, and relay other users' SOS/REPORT/ACK/STATUS packets in the background
- do not show another user's emergency type, coordinates, map marker, report details, or notification
- only show ACK/STATUS if transport_token belongs to one of the Citizen's own local reports
- relay unrelated ACK/STATUS silently
- store only transport-level relay data for other users

RESPONDER:
- show SOS/REPORT packets only when operationally relevant according to cached responder/agency capability
- silently relay unrelated packets
- allow ACK/status creation for relevant incidents
- continue background relay while UI is closed, within Android OS limits

ADMIN:
- show all valid operational emergency packets
- continue background relay
- act as gateway when Internet is available

ARCHITECTURE:
- BLE reception/relay logic must not live inside UI composables
- create a role-policy layer between packet processing and UI notification
- receiving a packet must not automatically create a user-visible notification
- use foreground/background Android service mechanisms required by the OS
- persist relay queue and seen-token state
- add tests proving Citizens cannot see other users' emergency data while still relaying it

Required tests:
1. Citizen B receives Citizen A SOS -> no UI alert -> packet is queued for relay
2. Citizen B receives ACK for Citizen A -> no UI alert -> ACK is relayed
3. Citizen A receives ACK for Citizen A token -> own status is shown
4. Responder receives relevant FIRE packet -> alert shown
5. Unrelated responder receives FIRE packet -> no alert -> packet relayed
6. Admin receives packet -> alert shown
7. app UI closes -> relay service retains pending queue
8. app restarts -> persistent seen/relay state prevents duplicate processing
```

# 86B. Codex Prompt — Location Fallback + Terminal Resolution Propagation

```text
Implement the final location-fallback and terminal-status rules.

LOCATION SOURCES:
- encode location source in byte 1 bits 1-0:
  00 GPS
  01 MAP_PIN
  10 BARANGAY_REFERENCE
  11 UNKNOWN
- GPS uses signed E7 coordinates + measured accuracy
- MAP_PIN uses selected E7 coordinates + accuracy 255
- BARANGAY_REFERENCE uses cached representative E7 coordinates + accuracy 255
- UNKNOWN uses coordinates 0,0 + accuracy 255, but UI must never plot 0,0

CACHED BARANGAYS:
- add fixed representative coordinates to cached Barangay records
- representative point must be inside the Barangay polygon
- add offline searchable Barangay selector
- selecting a Barangay requires no Internet
- Responder/Admin must display Barangay-reference locations as approximate and highlight the polygon when available

SOS:
- save token/report locally before waiting for GPS
- try recent high-quality GPS first
- wait only a short bounded period for a fresh fix
- if GPS fails, allow offline map pin, cached Barangay, or UNKNOWN
- never auto-assume home Barangay without explicit Citizen confirmation

LOCATION REFINEMENT:
- same transport_token may carry improved location
- UNKNOWN -> BARANGAY/GPS/MAP_PIN updates same report
- BARANGAY_REFERENCE -> GPS/MAP_PIN updates same report
- GPS -> materially better GPS may update same report
- MAP_PIN changes only on explicit Citizen correction
- refinement must not create a second incident

TERMINAL STATUS:
- RESOLVED and CANCELLED are terminal for the report token
- when Responder/Admin resolves token X:
  - local status immediately becomes terminal
  - remove from active list
  - keep in history
  - remove SOS/REPORT X from relay queue
  - create/relay STATUS terminal packet for X
- when any phone receives terminal status X:
  - persist terminal tombstone
  - remove active relay of X
- Citizen relay-only phone must not show another user's terminal update
- Citizen origin phone updates own report status
- stale SOS/REPORT X arriving after terminal status must not:
  - alert Admin/Responder
  - reactivate the incident
  - re-enter the relay queue as a new emergency
- optionally rebroadcast known terminal status to quench stale copies
- keep terminal tombstone longer than normal SOS TTL
- add tests across app restart and delayed stale packet arrival
```

---

# 87. Revised Definition of Done

## BLE

- [ ] One canonical protocol v2.
- [ ] Emergency packet is 21 bytes.
- [ ] ACK/STATUS packet is 13 bytes.
- [ ] 6-byte random report token.
- [ ] Coordinates carried in every emergency packet as signed 32-bit E7 latitude/longitude.
- [ ] BLE coordinate encoding preserves approximately 1e-7-degree resolution.
- [ ] Horizontal location accuracy is carried in the packet.
- [ ] Responder UI displays both coordinates and accuracy estimate.
- [ ] Packet carries a 2-bit location-source code.
- [ ] GPS location source supported.
- [ ] Offline map-pin location source supported.
- [ ] Cached Barangay-reference location source supported.
- [ ] Unknown-location SOS is supported without plotting fake coordinates.
- [ ] Same-token location refinement updates the existing emergency rather than creating a duplicate.
- [ ] Creation time carried.
- [ ] Hop count carried.
- [ ] Subject relation carried.
- [ ] Immediate-danger flag carried.
- [ ] CRC-16 carried.
- [ ] No image/media.
- [ ] No full text.
- [ ] No reporter database ID.
- [ ] No target agency database ID.
- [ ] No address hash used for routing.
- [ ] v1 decode compatibility during migration.

## Offline System Data

- [ ] Emergency protocol codes available offline.
- [ ] Predefined ACK/status messages available offline.
- [ ] Agencies cached.
- [ ] Agency service areas cached.
- [ ] Barangays cached.
- [ ] Barangay boundaries available offline.
- [ ] Each cached Barangay has a stable representative point inside its polygon.
- [ ] Citizen can select a cached Barangay with zero Internet.
- [ ] Barangay-reference location is clearly labeled approximate.
- [ ] Bundled initial data works without first Internet connection.
- [ ] Catalog versions tracked.
- [ ] Server updates cached data when online.

## Offline Map

- [ ] Deployment-area map bundled/cached.
- [ ] BLE coordinates plot offline.
- [ ] Coordinate-to-barangay lookup works offline.
- [ ] Map missing/corrupt does not break emergency handling.
- [ ] Map version tracked.
- [ ] Map updates only through Internet.
- [ ] Map update verified before replacement.

## Relay

- [ ] Persistent deduplication.
- [ ] Separate relay queue.
- [ ] Time expiration.
- [ ] Hop limit.
- [ ] Random backoff.
- [ ] Fair multi-packet scheduling.
- [ ] ACK multi-hop.
- [ ] ACK stops original relay.
- [ ] STATUS RESOLVED/CANCELLED stops relay.
- [ ] Terminal status automatically updates matching Responder/Admin local incident state.
- [ ] Resolved/cancelled incidents are removed from active lists and retained in history.
- [ ] Terminal tombstone suppresses stale copies of the same report token.
- [ ] Relay-only Citizen devices process terminal status silently.
- [ ] Relay state survives restart.

## Citizen

- [ ] SEND SOS online.
- [ ] SEND SOS offline.
- [ ] REPORT AN INCIDENT online.
- [ ] REPORT AN INCIDENT offline.
- [ ] Can report another person.
- [ ] Can report public/unknown hazard.
- [ ] Can choose event map pin.
- [ ] Can select a cached Barangay when exact GPS is unavailable.
- [ ] Can send an urgent SOS with UNKNOWN location if no fallback is available.
- [ ] No image feature in v1.
- [ ] Short description is Internet/full-sync data only.
- [ ] Offline full details persist locally.
- [ ] ACK/status displays using cached message catalog.

## Responder

- [ ] Receives offline emergency.
- [ ] Decodes emergency type locally.
- [ ] Decodes subject relation locally.
- [ ] Resolves Barangay locally.
- [ ] Shows precise coordinates for GPS/MAP_PIN.
- [ ] Shows Barangay boundary/approximate area for BARANGAY_REFERENCE.
- [ ] Never plots UNKNOWN as `(0,0)`.
- [ ] Shows emergency on offline map.
- [ ] Locally determines relevant responder type where possible.
- [ ] Sends ACK offline.

## Backend

- [ ] Gateway does not become reporter.
- [ ] Minimal BLE report creates operational incident.
- [ ] Full sync enriches same report.
- [ ] Routing uses coordinates + emergency type + service areas.
- [ ] One incident can route to multiple agencies.
- [ ] Server remains authoritative when online.
- [ ] Exact retransmission dedup uses transport token.
- [ ] Terminal RESOLVED/CANCELLED state is keyed by transport token.
- [ ] Stale gateway uploads cannot reactivate a terminal incident for the same token.
- [ ] Later valid location refinement can update a non-terminal incident without duplication.

---


## Role-Based Visibility

- [ ] Citizen does not see emergency details for packets owned by other users.
- [ ] Citizen does not receive a notification for another user's SOS/report.
- [ ] Citizen silently stores only transport-level relay data.
- [ ] Citizen automatically relays eligible packets in the background.
- [ ] Citizen can see ACK/STATUS only when the token matches one of their own reports.
- [ ] Responder sees only operationally relevant alerts by default.
- [ ] Responder silently relays unrelated emergency traffic.
- [ ] Admin can view all valid operational emergency alerts.
- [ ] Receiving a packet and displaying a packet are separate code paths.
- [ ] Background relay survives UI closure as far as Android platform rules permit.
- [ ] Relay status is visible in the app settings/status screen.

# 88. Final Architecture

```text
                    INTERNET AVAILABLE
                           │
                           ▼
                 ┌────────────────────┐
                 │    AGAP BACKEND    │
                 │                    │
                 │ Catalogs           │
                 │ Geography          │
                 │ Routing            │
                 │ Incidents          │
                 │ Dispatch           │
                 └─────────┬──────────┘
                           │
                   sync/update when online
                           │
                           ▼
                ┌─────────────────────┐
                │   ANDROID DEVICE    │
                │                     │
                │ Room cached data    │
                │ Barangay boundaries │
                │ Offline map         │
                │ Local reports       │
                │ Relay queue         │
                │ Seen packets        │
                └──────────┬──────────┘
                           │
                      Internet gone
                           │
                           ▼
                    21-byte BLE
           (GPS / MAP_PIN / BARANGAY / UNKNOWN)
                           │
                           ▼
                    Nearby AGAP Phone
                           │
                  ┌────────┴────────┐
                  │                 │
                  ▼                 ▼
             Decode packet      Relay packet
                  │
                  ▼
          cached local catalogs
                  │
          ┌───────┴───────────┐
          ▼                   ▼
     GeoResolver         OfflineMapManager
          │                   │
          ▼                   ▼
      Barangay            Map marker
          │                   │
          └─────────┬─────────┘
                    ▼
              Human-readable
             offline emergency
```

The final rule is:

> **BLE carries the dynamic emergency. The phone already carries the knowledge needed to understand it.**

This keeps the BLE message short while preserving an actually useful offline emergency-response experience.


---

# 89. Final Non-Conflicting Rules

These rules override any older AGAP draft that conflicts with them:

```text
1. No image/media transmission in AGAP v1.

2. Citizen phones silently relay other users' emergency traffic.
   They do not display other Citizens' emergency details.

3. Responder/Admin are the roles that may see operational emergency messages.
   Responders may silently relay unrelated packets.

4. The emergency BLE packet is 21 bytes.
   ACK/STATUS is 13 bytes.

5. Emergency location uses one explicit source:
   GPS, MAP_PIN, BARANGAY_REFERENCE, or UNKNOWN.

6. GPS uses precise signed E7 coordinates and reported horizontal accuracy.

7. If GPS is unavailable, the Citizen can choose an offline map pin
   or a cached Barangay whose record contains a fixed representative point.

8. A Barangay representative point is approximate.
   It must never be shown as the exact incident location.

9. If no usable location exists, send the SOS with UNKNOWN location.
   Never substitute fake coordinates.

10. The same report token may receive a better location later.
    A location refinement updates the same emergency; it does not create a duplicate.

11. BLE packets do not contain Reporter database ID, Target Agency database ID,
    full address, description, image, or SQL primary keys.

12. Agency routing is authoritative on the backend when online.
    Cached routing is only an offline fallback.

13. RESOLVED and CANCELLED are terminal states for the same report token.

14. Once terminal status for token X is known:
    - stop relaying the original SOS/REPORT X;
    - update Responder/Admin local incident status automatically;
    - remove it from active lists;
    - retain it in history;
    - suppress delayed stale SOS/REPORT copies for X.

15. Terminal tombstones are retained for a bounded period longer than the
    normal emergency relay lifetime so old store-carried packets cannot
    immediately resurrect a resolved incident.

16. A genuinely new emergency must always generate a new report token,
    even if it occurs at the same location and has the same emergency type.

17. The Citizen does not need to type a street address.
    Exact manual location selection is done by dropping a pin on the cached offline map.

18. Offline map pinning works without GPS and without Internet once the map package exists locally.

19. Free-text landmark/description is optional full-report data only and is never required
    to construct the BLE emergency packet.
```
