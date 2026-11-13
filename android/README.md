# AGAP Android Mobile Application

The AGAP Android application is the mobile victim & field responder client built with **Kotlin** and **Jetpack Compose**. It provides one-tap emergency SOS dispatching, real-time background location updates, a zero-touch network handoff engine, and an offline BLE 5.0 store-and-forward mesh telemetry advertiser.

---

## Prerequisites & Tooling Requirements

* **Android Studio**: Ladybug / Jellyfish or newer — [Download Android Studio](https://developer.android.com/studio)
* **JDK**: `17` or higher
* **Android SDK Target**: `API 34` (Android 14)
* **Minimum SDK**: `API 26` (Android 8.0 Oreo - required for BLE Advertising & Wi-Fi Direct)
* **Physical Android Device**: Recommended for testing Bluetooth Low Energy (BLE 5.0) advertising and Wi-Fi Direct P2P app sharing (emulators have limited BLE advertising support).

---

## Environment Setup & Installation

### Step 1: Navigate to Android Directory
```bash
cd android
```

### Step 2: Environment Configuration
Copy `.env.example` to `local.properties`:

```bash
# On Linux / macOS / Git Bash
cp .env.example local.properties

# On Windows PowerShell
Copy-Item .env.example local.properties
```

`local.properties` contains required runtime keys and endpoints:
```properties
AGAP_API_URL=http://10.0.2.2:8080/api/v1
BLE_SCAN_INTERVAL_MS=30000
BLE_ADV_POWER_LEVEL=HIGH
GPS_HIGH_ACCURACY_ENABLED=true
```

### Hosted API

Deploy the API first using the [hosted functions guide](../functions/README.md). In `android/local.properties`, set `AGAP_API_PROVIDER` to `cloudflare`, `supabase`, or `firebase` and set `AGAP_API_URL` to the HTTPS function URL ending in `/api/v1`.

```properties
AGAP_API_PROVIDER=supabase
AGAP_API_URL=https://your-project.supabase.co/functions/v1/agap-api/api/v1
```

Rebuild the app and sign in with an approved AGAP account. Hosted builds use HTTP sync while the app is open. Background sync and queued uploads continue through Android's workers, and BLE-only mode blocks internet traffic. Sessions, cached records, and pending reports are stored separately for each hosted API URL. To upload reports queued against a previous endpoint, open the build configured for that endpoint.

### Step 3: Open in Android Studio
1. Launch **Android Studio**.
2. Select **Open an existing project** and choose the `AGAP/android` folder.
3. Allow Gradle to sync dependencies automatically.

---

## Core Offline & BLE Engine Features

- **BLE Protocol v2**: Encodes 21-byte binary emergency packets (`SOS` & `INCIDENT_REPORT`) and 13-byte response packets (`ACK` & `STATUS`) with CRC-16 corruption checking.
- **Location Sources**: Supports `GPS` (high-accuracy GNSS fix), `MAP_PIN` (offline interactive map tap), `BARANGAY_REFERENCE` (cached centroid lookup), and `UNKNOWN` (location unavailable; `(0,0)` is never plotted).
- **Offline Map (OSMDroid)**: Renders vector/raster MBTiles archives offline without cellular connection.
- **Silent Background Relay**: Citizen devices forward mesh packets in the background without UI popups or PII leakage.
- **Stale Packet Suppression**: Terminal states (`RESOLVED` / `CANCELLED`) propagate tombstone records to quench active SOS relaying.

---

## Technical Specifications & Guides

- [BLE Protocol v2 Specification](file:///docs/ble-protocol-v2.md)
- [BLE v2 Test Vectors JSON](file:///docs/ble-protocol-v2-test-vectors.json)
- [Offline Mapping & OSMDroid Packaging](file:///docs/offline-map.md)
- [Data Synchronization & Gateway Enrichment](file:///docs/data-sync.md)
- [Location Sources & E7 Encoding Rules](file:///docs/location-sources.md)

---

## Running & Building

### Local offline test accounts (separate debug app)

Build `offlineDebug` to install **AGAP Offline Test** beside the regular app:

```powershell
.\gradlew.bat assembleOfflineDebug
.\gradlew.bat installOfflineDebug
```

The sign-in screen has one-tap **Citizen**, **Responder**, and **Admin** buttons.
Manual credentials are `test.citizen`, `test.responder`, or `test.admin`, all with
password `offline123`. Use **Profile → Logout** to switch roles.

This variant has its own app ID and storage. Its accounts, sample incidents,
assignment, contacts, devices, messages, and advisories are local fixtures. Profile
and management edits persist locally. SOS and detailed reports use the normal
local report/relay queues and remain on this phone. The test app cannot connect
to the backend or transmit Bluetooth packets: its manifest omits Internet and
Bluetooth permissions, and the corresponding workers/services are disabled.
It uses a sample location near Legazpi and a small bundled map test area at zoom
15, copied from existing test-session map cache (© OpenStreetMap contributors).
Map coverage outside that area, live routing, and actual multi-phone delivery
require a normal build and appropriate deployment data.

The fixtures and credentials exist only in `src/offlineDebug`; normal `debug` and
`release` builds use the real backend and have no local account buttons.
To run this variant's Android tests:

```powershell
.\gradlew.bat -PAGAP_TEST_BUILD_TYPE=offlineDebug connectedOfflineDebugAndroidTest
```

### Guest browsing

Select **Continue as guest** on the sign-in screen to explore the home controls and
map without creating an account. Guest mode persists when the app reopens. The
Profile tab includes a theme setting and a sign-in action; Alerts shows an account
prompt. Trying to send an SOS or report asks the guest to sign in or create an
account. Guest mode never creates an identity, queues emergency reports, or opens
the authenticated realtime channel. Map tiles may download while browsing.

Guest startup also defers Bluetooth dependency creation and application sync
observers until sign-in. The custom splash has a 600 ms minimum display, while
still awaiting saved session/theme loading. See the
[startup optimization report](docs/startup-optimization-2026-10-28.md) for tests,
phone measurements, and the remaining first-frame stutter.

### Running from Command Line (Gradle Wrapper)

```bash
# On Windows
.\gradlew.bat assembleDebug

# On Linux / macOS
./gradlew assembleDebug
```

To install directly to a connected Android device:
```bash
# On Windows
.\gradlew.bat installDebug

# On Linux / macOS
./gradlew installDebug
```

---

## Required Device Permissions

Upon first launch, ensure the following permissions are granted to enable full dual-channel operation:
* **Fine Location & Background Location**: High-precision GPS tracking
* **Bluetooth Scan & Bluetooth Advertise**: BLE extended mesh broadcast & scanning
* **Wi-Fi & Nearby Devices**: Wi-Fi Direct P2P offline APK sharing

