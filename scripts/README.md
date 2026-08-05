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
Terminates processes listening on port 8080 (Go Backend) and port 5173 (Vite Frontend).

```powershell
.\scripts\stop.ps1
```

### 2. `stop.sh` (Linux / macOS Bash)
Kills background server PIDs running on ports 8080 and 5173.

```bash
chmod +x scripts/stop.sh
./scripts/stop.sh
```

---

## Running Ports
- **Go Backend API**: `http://localhost:8080`
- **WebSocket Endpoint**: `ws://localhost:8080/ws`
- **GIS Command Dashboard**: `http://localhost:5173`
