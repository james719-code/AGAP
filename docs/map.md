# Website Map Cheat Sheet

The website map is the admin operational view of the same incidents and responder locations used by Android. Android code remains unchanged.

## Shared location resolution

The backend target ID uses this spatial shape:

```text
region-province-municipality-barangay-zone::uuid
05-05-12-01-04::example-node-id
```

Canonical JSON fields are `region`, `province`, `municipality`, `barangay`, `zone`, `uuid_hash`, and `raw`. Older website aliases `muni`, `unit`, and `uuid` are accepted only as compatibility fallbacks.

The website reuses the application's `barangays.json` as `frontend/src/assets/data/barangays.json`. Resolution order is:

1. Match the decoded barangay code against the shared catalog.
2. If the code is missing or unknown, choose the nearest representative barangay from incident GPS coordinates.
3. If neither is available, show raw location codes—never the misleading phrase `Barangay Unmapped, Municipality`.

Nearest-barangay output is explicitly labelled approximate. It is not reverse geocoding and must not be presented as a street address.

## Map workflow

- Fetch incidents and responder units.
- Merge incident/status/location and responder-location WebSocket events.
- Filter active operations and emergency categories in the browser.
- Search incident number, emergency type, resolved location, target ID, or coordinates.
- Select a result or marker to fly to it and open operational details.
- Choose an `IDLE` responder from the selected-incident card and create its dispatch; the nearest available unit is preselected when GPS is available.
- Display straight-line nearest-unit distance only; no road ETA is claimed.
- Do not draw a relay route: telemetry provides only `hop_count`, not the coordinates of each BLE hop.

## Website UI structure

- Uses the same page heading, subtitle, status badges, refresh action, error banner, theme tokens, corner radius, and shadows as the other admin sections.
- Keeps search at the upper-left of the canvas and filters at the upper-right on desktop.
- Uses a filter toggle on smaller screens so controls do not overlap.
- Keeps the selected-incident/dispatch card scrollable and inside the viewport.

## Key files

- Page and filters: `frontend/src/pages/MapPage.tsx`
- Leaflet markers/popups: `frontend/src/components/map/IncidentMap.tsx`
- Location formatter: `frontend/src/lib/targetIdFormatter.ts`
- Barangay catalog: `frontend/src/assets/data/barangays.json`
- Live events: `frontend/src/context/WebSocketContext.tsx`

## Verification

```powershell
cd frontend
npm.cmd run build
```
