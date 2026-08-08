# AGAP Offline Map Architecture & Packaging Guide

## 1. Overview

AGAP requires continuous location awareness and spatial reporting even during total cellular network blackouts and grid failure. The Android client implements offline mapping using **OSMDroid** backed by pre-packaged vector/raster map archives (`.mbtiles` or sqlite tile archives) and local SQLite spatial catalogs.

---

## 2. Map Tile Storage & Packaging

### 2.1 Bundled Offline Tiles
- **Region Coverage**: Legazpi City and surrounding Albay province municipalities (PSGC Region V).
- **Format**: `.mbtiles` (SQLite database containing OpenStreetMap tiles at zoom levels 12–17).
- **Asset Path**: `app/src/main/assets/offline_tiles_legazpi.mbtiles`
- **Device Cache Directory**: `/sdcard/Android/data/ph.gov.agap/files/osmdroid/tiles/`

### 2.2 Updating Offline Map Tiles
To update the bundled offline map tiles:
1. Export a bounding box for Albay Province (`13.0` N to `13.4` N, `123.5` E to `123.9` E) using **Mobile Atlas Creator (MOBAC)** or `tile-cooker`.
2. Format the export as an **MBTiles** archive with PNG/WebP raster tiles up to Zoom Level 17.
3. Replace `app/src/main/assets/offline_tiles_legazpi.mbtiles`.
4. Rebuild the Android package (`:app:assembleRelease`).

---

## 3. Pin Dropping & Geolocation Fallbacks

When operating fully offline, the Citizen app provides a multi-tier location fallback strategy:

1. **Exact GPS Fix (`GPS`)**: If GNSS satellites are locked, the device obtains E7 coordinates with horizontal accuracy (meters).
2. **Manual Map Pin (`MAP_PIN`)**: If GNSS is obstructed (indoor/urban canyon), the user opens the cached OSMDroid map viewer and taps the incident position.
3. **Barangay Centroid (`BARANGAY_REFERENCE`)**: If neither GPS nor map interaction is possible, the user selects their Barangay from the pre-seeded local database catalog, which provides a representative centroid coordinate.
4. **Unknown (`UNKNOWN`)**: Transmits `(0,0)` with accuracy `255`. Receivers and Responder maps **MUST NOT** plot `(0,0)`.

---

## 4. Offline Responder Map Rendering

Responder and Admin Android devices render incoming offline BLE telemetry directly on the OSMDroid map view:
- Plotted markers show incident classification (Medical, Fire, Crime, etc.).
- Location source badges (`GPS`, `MAP_PIN`, `BARANGAY_REF`) and accuracy rings are rendered around coordinates to indicate fix confidence.
- Marker colors distinguish active emergencies from acknowledged (`HELP_EN_ROUTE`) and terminal (`RESOLVED`) incidents.
