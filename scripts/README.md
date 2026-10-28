# AGAP Automation & Developer Scripts

This directory contains convenience scripts to launch, test, and stop the AGAP stack concurrently.

## Launch Scripts

### 1. `dev.ps1` (Windows PowerShell)
Launches the Go Backend API and Vite Frontend in parallel PowerShell windows.

```powershell
.\scripts\dev.ps1
```

### 2. `dev.sh` (Linux / macOS Bash)
Launches the Go Backend API and Vite Frontend in background processes with synchronized termination traps.

```bash
chmod +x scripts/dev.sh
./scripts/dev.sh
```

---

## Stop Scripts

### 1. `stop.ps1` (Windows PowerShell)
Terminates processes listening on port 8088 (Go Backend) and port 5173 (Vite Frontend), automatically closing their CMD/PowerShell terminal windows.

```powershell
.\scripts\stop.ps1
```

### 2. `stop.sh` (Linux / macOS Bash)
Kills background server PIDs running on ports 8088 and 5173.

```bash
chmod +x scripts/stop.sh
./scripts/stop.sh
```

---

## Running Ports
- **Go Backend API**: `http://localhost:8088`
- **WebSocket Endpoint**: `ws://localhost:8088/ws`
- **GIS Command Dashboard**: `http://localhost:5173`

## Bluetooth log analysis

Capture the normal debug app's `AGAP_BLE` log, then summarize it with Python:

```powershell
python -B scripts/analyze_bluetooth_log.py artifacts/ble-session.log
python -B -m unittest discover -s scripts/tests -p test_analyze_bluetooth_log.py
```

The summary includes completed relays, failure reasons, GATT stage timings, and
receiver processing time. Missing timing samples remain unknown. Use captures
that do not overlap when passing multiple files, since events are counted as
recorded. Keep normal AGAP and Offline Test captures separate. A guest session or
the Offline Test build cannot supply a real peer-transfer speed measurement.

See [the Bluetooth review](../android/docs/bluetooth-transfer-review-2026-10-28.md)
for evidence, changes, and the remaining two-phone check.

