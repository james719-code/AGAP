# AGAP Backend — Go Telemetry & Dispatch Engine

The AGAP backend is a high-performance Go ingestion engine designed to process emergency telemetry packets, perform transport token deduplication, route incidents by emergency classification type, manage rescue unit dispatching, handle gateway minimal-to-full report enrichment, and stream real-time emergency events to the GIS Command Center Dashboard over WebSocket.

---

## Technical Documentation & Protocol Specs

- [Deployment & API Explorer Guide](file:///docs/backend-deployment-and-api-explorer.md)
- [BLE Protocol v2 Specification](file:///docs/ble-protocol-v2.md)
- [BLE v2 Test Vectors JSON](file:///docs/ble-protocol-v2-test-vectors.json)
- [Offline Mapping & OSMDroid Packaging](file:///docs/offline-map.md)
- [Data Synchronization & Gateway Enrichment](file:///docs/data-sync.md)
- [Location Sources & E7 Encoding Rules](file:///docs/location-sources.md)

---

## Prerequisites & Tooling Requirements

Before installing and running the backend, ensure you have the following installed:

* **Go**: `v1.25` or higher (the module currently declares Go 1.25.0) — [Download & Install Go](https://go.dev/dl/)
* **Git**: `v2.x+`
* **SQLite3** *(Optional)*: `v3.x+` (for inspecting local `agap_gis.db` directly via CLI)

> [!NOTE]
> No C compiler / GCC is required! The backend uses `modernc.org/sqlite` — a pure-Go, CGO-free SQLite driver that cross-compiles cleanly to Linux.

---

## Quick Start & Local Execution

### Step 1: Environment Setup
Copy `.env.example` to create your local `.env`:

```bash
# On Linux / macOS / Git Bash
cp .env.example .env

# On Windows PowerShell
Copy-Item .env.example .env
```

### Step 2: Install Go Module Dependencies
```bash
go mod download
go mod tidy
```

### Step 3: Run Development Server
```bash
go run ./cmd/server
```
*The server initializes SQLite migrations (`agap_gis.db`) and listens on `http://localhost:8080`.*

---

## 🖥️ Interactive HTML API & Database Explorer

The Go backend embeds a self-contained, high-performance HTML/CSS/JS dashboard for system monitoring, live database browsing, and API exploration:

* **Local Web UI Dashboard**: [http://localhost:8088/info](http://localhost:8088/info) (or `http://localhost:8080/info`)
* **System & DB Info JSON**: [http://localhost:8088/api/v1/info](http://localhost:8088/api/v1/info)
* **Database Tables List API**: [http://localhost:8088/api/v1/system/db/tables](http://localhost:8088/api/v1/system/db/tables)
* **Database Table Rows Browser**: [http://localhost:8088/api/v1/system/db/tables/users](http://localhost:8088/api/v1/system/db/tables/users)

---

## 🚀 Local Windows Device Deployment Guide

### 1. Configure Local Windows Defender Firewall Rules
Run [setup-firewall.ps1](file:///deploy/setup-firewall.ps1) in PowerShell (as Administrator) to allow inbound connections:
```powershell
.\deploy\setup-firewall.ps1
```

### 2. Run Local Windows Deployment Script
Build `agap-server.exe` and stage frontend static files into `./build`:
```powershell
.\deploy\deploy.ps1
```

*Access your local on-device dashboard at `http://localhost:8088/info` or `http://localhost:8080/info`.*

---

## Backend Freeze / Security Notes

- HTTP methods and route paths are intentionally kept stable for frontend integration.
- Citizens can list/read only incidents tied to their account (direct citizen ownership or their incident report).
- Responders can mutate incident/dispatch/location state only for their assigned rescue unit; admins retain global operational access.
- Refresh tokens are random opaque values and are stored as SHA-256 digests; suspended/rejected accounts lose active server-side sessions.
- `ENV=production` rejects the shipped JWT/AES secrets and wildcard CORS, disables the DB explorer by default, hides database row-count statistics, and suspends any untouched migration demo account that still has the shipped password hash.
- Telemetry HMAC enforcement is controlled by `REQUIRE_TELEMETRY_HMAC`. It defaults to `false` to preserve current Android compatibility. Enable it only after every telemetry client sends `X-AGAP-Signature`.
- See [`../BACKEND_FREEZE.md`](../BACKEND_FREEZE.md) for the frontend contract and final verification gate.

---

## Complete API Route Registry (50+ Endpoints)

| Method | Endpoint Path | Access Level | Description |
|---|---|---|---|
| `GET` | `/health` | Public | Service health & DB liveness probe |
| `GET` | `/api/v1/info` | Public | Comprehensive system configuration & DB stats JSON |
| `GET` | `/info` | Development only | Embedded Interactive HTML API & DB Explorer Dashboard |
| `GET` | `/api/v1/system/db/tables` | Development only | List all database tables and row counts |
| `GET` | `/api/v1/system/db/tables/{name}` | Development only | Fetch columns & rows data for a specific database table |
| `GET` | `/ws` | JWT | Real-time WebSocket emergency event stream |
| `POST` | `/api/v1/auth/login` | Public | User authentication & JWT Bearer Token issuance |
| `POST` | `/api/v1/auth/register` | Public | Register citizen, responder, or admin user |
| `POST` | `/api/v1/auth/refresh` | Public | Refresh JWT access token |
| `POST` | `/api/v1/auth/logout` | JWT | User logout session invalidation |
| `GET` | `/api/v1/users/me` | JWT | Get profile of authenticated user |
| `PUT` | `/api/v1/users/me` | JWT | Update user profile |
| `GET` | `/api/v1/users/me/devices` | JWT | List user registered mobile devices |
| `POST` | `/api/v1/users/me/devices` | JWT | Link mobile device identifier |
| `DELETE` | `/api/v1/users/me/devices/{id}` | JWT | Unlink mobile device |
| `GET` | `/api/v1/users/me/contacts` | JWT | List personal emergency contacts |
| `POST` | `/api/v1/users/me/contacts` | JWT | Add emergency contact |
| `PUT` | `/api/v1/users/me/contacts/{id}` | JWT | Update emergency contact |
| `PUT` | `/api/v1/users/me/contacts/{id}/primary` | JWT | Mark primary ICE emergency contact |
| `DELETE` | `/api/v1/users/me/contacts/{id}` | JWT | Delete emergency contact |
| `GET` | `/api/v1/geo/provinces` | Public | List PSGC provinces |
| `GET` | `/api/v1/geo/municipalities` | Public | List PSGC municipalities |
| `GET` | `/api/v1/geo/barangays` | Public | List PSGC barangays |
| `GET` | `/api/v1/geo/addresses` | Public | List address registry |
| `GET` | `/api/v1/messages/catalog` | JWT | Predefined BLE message catalog |
| `GET` | `/api/v1/emergency-types` | JWT | Emergency classification types catalog |
| `POST` | `/api/v1/telemetry/uplink` | JWT (+ HMAC when enabled) | Uplink BLE v2 SOS telemetry packet |
| `POST` | `/api/v1/mesh/events/batch` | JWT | Log batch mesh hop delivery events |
| `GET` | `/api/v1/sync/bootstrap` | JWT | Initial offline database bootstrap package |
| `POST` | `/api/v1/sync/push` | JWT | Push offline mesh changes |
| `GET` | `/api/v1/sync/changes` | JWT | Query delta sync changes |
| `POST` | `/api/v1/incident-reports` | JWT | Submit minimal or full incident report |
| `GET` | `/api/v1/users/me/incident-reports` | JWT | List user submitted incident reports |
| `GET` | `/api/v1/users/me/incident-reports/{uuid}` | JWT | Get incident report by UUID |
| `GET` | `/api/v1/incidents` | JWT | Query incidents; citizens receive only incidents they own/reported |
| `GET` | `/api/v1/incidents/{id}` | JWT | Get incident details; citizen access is ownership-scoped |
| `PUT` | `/api/v1/incidents/{id}/status` | Responder/Admin | Update incident status |
| `POST` | `/api/v1/incidents/{id}/dispatch` | Responder/Admin | Dispatch rescue unit to incident |
| `GET` | `/api/v1/incidents/{id}/dispatches` | JWT | List dispatches; citizen access is ownership-scoped |
| `PUT` | `/api/v1/incidents/{id}/dispatches/{dispatchID}/status` | Responder/Admin | Update dispatch assignment status |
| `GET` | `/api/v1/incidents/{id}/status-history` | JWT | Get status history; citizen access is ownership-scoped |
| `GET` | `/api/v1/incidents/{id}/messages` | JWT | List incident messages; citizen access is ownership-scoped |
| `POST` | `/api/v1/incidents/{id}/messages` | Responder/Admin | Post incident message |
| `GET` | `/api/v1/agencies` | JWT | List dispatch agencies |
| `POST` | `/api/v1/agencies` | Admin | Create dispatch agency |
| `GET` | `/api/v1/agencies/{id}` | JWT | Get agency details |
| `PUT` | `/api/v1/agencies/{id}` | Admin | Update agency details |
| `DELETE` | `/api/v1/agencies/{id}` | Admin | Delete agency |
| `GET` | `/api/v1/responders` | JWT | List responder units |
| `GET` | `/api/v1/responders/me` | JWT | Get my assigned responder unit |
| `GET` | `/api/v1/responders/me/dispatches` | JWT | List my active dispatches |
| `GET` | `/api/v1/responders/{id}` | JWT | Get responder unit details |
| `PUT` | `/api/v1/responders/{id}/location` | Responder/Admin | Update unit live GPS & status |
| `POST` | `/api/v1/responders` | Admin | Create responder unit |
| `PUT` | `/api/v1/responders/{id}` | Admin | Update responder unit |
| `DELETE` | `/api/v1/responders/{id}` | Admin | Delete responder unit |
| `GET` | `/api/v1/rescue-units/{id}/members` | JWT | List responder members |
| `POST` | `/api/v1/rescue-units/{id}/members` | Admin | Add member to unit |
| `DELETE` | `/api/v1/rescue-units/{id}/members/{responderId}` | Admin | Remove member from unit |
| `GET` | `/api/v1/users` | Admin | List all system users |
| `PUT` | `/api/v1/admin/users/verify` | Admin | Verify user identity |
| `PUT` | `/api/v1/admin/users/{id}/status` | Admin | Update account status |
| `GET` | `/api/v1/admin/audit-logs` | Admin | View system audit logs |
| `POST` | `/api/v1/admin/emergency-types` | Admin | Create emergency classification type |

---

## Repository Structure

```
backend/
├── cmd/
│   └── server/
│       └── main.go               # Server composition root & HTTP routes
├── internal/
│   ├── config/                   # Config loader (.env -> typed struct)
│   ├── domain/                   # Domain models & spatial address parser
│   ├── repository/               # Storage interfaces & SQLite implementation
│   ├── service/                  # Business logic (Auth, Telemetry, Agencies)
│   ├── handler/                  # Chi HTTP handlers & HTML Dashboard UI
│   ├── middleware/               # CORS, RateLimiter, JWT, HMAC, Logger
│   └── websocket/                # WebSocket Hub & client connection management
├── go.mod                        # Go module definition
├── main.go                       # Root execution shim
└── README.md                     # Developer guide & API reference
```
