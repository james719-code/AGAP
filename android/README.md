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
DEFAULT_TARGET_ID=05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111
BLE_SCAN_INTERVAL_MS=30000
BLE_ADV_POWER_LEVEL=HIGH
GPS_HIGH_ACCURACY_ENABLED=true
AES_SECRET_PAYLOAD_KEY=0123456789abcdef0123456789abcdef
```

### Step 3: Open in Android Studio
1. Launch **Android Studio**.
2. Select **Open an existing project** and choose the `AGAP/android` folder.
3. Allow Gradle to sync dependencies automatically.

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
* **Bluetooth Scan & Bluetooth Advertise**: BLE 5.0 extended mesh broadcast & scanning
* **Wi-Fi & Nearby Devices**: Wi-Fi Direct P2P offline APK sharing
