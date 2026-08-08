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

