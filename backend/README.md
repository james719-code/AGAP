# AGAP Backend — Go Telemetry & Dispatch Engine

The AGAP backend is a high-performance Go ingestion engine designed to process emergency telemetry packets, perform transport token deduplication, route incidents by emergency classification type, manage rescue unit dispatching, handle gateway minimal-to-full report enrichment, and stream real-time emergency events to the GIS Command Center Dashboard over WebSocket.

---

## Technical Documentation & Protocol Specs

- [BLE Protocol v2 Specification](file:///docs/ble-protocol-v2.md)
- [BLE v2 Test Vectors JSON](file:///docs/ble-protocol-v2-test-vectors.json)
- [Offline Mapping & OSMDroid Packaging](file:///docs/offline-map.md)
- [Data Synchronization & Gateway Enrichment](file:///docs/data-sync.md)
- [Location Sources & E7 Encoding Rules](file:///docs/location-sources.md)

---

## Prerequisites & Tooling Requirements

Before installing and running the backend, ensure you have the following installed on your machine:

* **Go**: `v1.22` or higher (Go 1.25+ recommended) — [Download & Install Go](https://go.dev/dl/)
* **Git**: `v2.x+`
* **SQLite3** *(Optional)*: `v3.x+` (for inspecting local `agap_gis.db` directly via CLI)

> **Note**: No C compiler / GCC is required! The backend uses `modernc.org/sqlite` — a pure-Go, CGO-free SQLite driver.

---

## Quick Start & Installation

### Step 1: Navigate to Backend Directory
```bash
cd backend
```

### Step 2: Environment Configuration
Copy the template environment file `.env.example` to create your local `.env`:

```bash
# On Linux / macOS / Git Bash
cp .env.example .env

# On Windows PowerShell
Copy-Item .env.example .env
```

### Step 3: Install & Tidy Go Module Dependencies
Download all required third-party Go modules (`chi`, `jwt`, `websocket`, `godotenv`, `modernc.org/sqlite`, `x/crypto`, `x/time`):

```bash
go mod download
go mod tidy
```

---

## Running the Application

### Development Mode (Direct Execution)
Run the server using Go's runner:

```bash
go run main.go
# or directly from cmd entry point
go run ./cmd/server/main.go
```
*The server will initialize SQLite database migrations (`agap_gis.db`) and start on `http://localhost:8080`.*

### Building Production Binary
To compile an optimized single-file binary:

```bash
# On Windows
go build -o bin/server.exe ./cmd/server/main.go

# On Linux / macOS
go build -o bin/server ./cmd/server/main.go
```

Then run the compiled binary:
```bash
# Windows
./bin/server.exe

# Linux / macOS
./bin/server
```

---

## Running Tests

Execute the complete backend test suite (unit tests for domain parsing, TTL cache, SQLite repositories, JWT auth, HMAC verification, and HTTP handlers):

```bash
# Run all tests
go test ./...

# Run all tests with verbose output
go test -v ./...
```

---

## Environment Variables Reference

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8080` | Server listening port |
| `ENV` | `development` | Environment stage (`development` / `production`) |
| `DB_TYPE` | `sqlite` | Database engine (`sqlite` for dev, `postgres` for prod) |
| `DB_FILE_PATH` | `./agap_gis.db` | Path to SQLite database file |
| `JWT_SECRET` | `agap_backend_jwt_secret_key_2026` | Secret key for signing & verifying JWT tokens |
| `AES_SECRET_KEY` | `0123456789abcdef0123456789abcdef` | Key used for HMAC signature verification & telemetry AES decryption |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated list of allowed CORS origins |
| `DEDUP_TTL_SECONDS` | `300` | In-memory packet deduplication TTL (5 minutes) |
| `RATE_LIMIT_RPS` | `20` | Rate limiter requests per second per IP |
| `RATE_LIMIT_BURST` | `40` | Rate limiter burst capacity |

---

## API Endpoint Overview

### 1. System & Real-Time
* `GET /health` — Service readiness & database liveness probe
* `GET /api/v1/info` (or `/health/info`) — Comprehensive API configuration, version metadata, & database table statistics
* `GET /ws` — Real-time WebSocket event feed

### 2. Authentication
* `POST /api/v1/auth/register` — Register a new citizen, responder, or dispatcher user
* `POST /api/v1/auth/login` — Login & obtain JWT Bearer Token

### 3. Telemetry Ingestion & Incidents *(Requires JWT Header)*
* `POST /api/v1/telemetry/uplink` — Uplink encrypted distress packet *(Requires `X-AGAP-Signature` HMAC header)*
* `GET /api/v1/incidents` — Query active/historical incidents (supports type, status, region filtering)
* `PUT /api/v1/incidents/{id}/status` — Update incident dispatch/resolution status

### 4. Target ID Registry: Dispatch Agencies *(Requires JWT Header)*
* `GET /api/v1/agencies` — List registered dispatch agencies
* `POST /api/v1/agencies` — Create dispatch agency
* `GET /api/v1/agencies/{id}` — Get agency details
* `PUT /api/v1/agencies/{id}` — Update agency details
* `DELETE /api/v1/agencies/{id}` — Remove agency

### 5. Target ID Registry: Responder Units *(Requires JWT Header)*
* `GET /api/v1/responders` — List responder units
* `POST /api/v1/responders` — Bind responder unit & mobile device to Target ID
* `GET /api/v1/responders/{id}` — Get responder details
* `PUT /api/v1/responders/{id}` — Update responder details
* `PUT /api/v1/responders/{id}/location` — Update responder GPS telemetry & status
* `DELETE /api/v1/responders/{id}` — Remove responder

---

## Repository Structure

```
backend/
├── cmd/
│   └── server/
│       └── main.go               # Application composition root & server entry point
├── internal/
│   ├── config/                   # Config loader (.env -> typed struct)
│   ├── domain/                   # Domain models & spatial address parser
│   ├── repository/               # Storage interface contracts
│   │   ├── sqlite/               # SQLite repository implementation & migrations
│   │   └── postgres/             # Future PostgreSQL production implementation
│   ├── service/                  # Business logic (Auth, Telemetry, Agencies)
│   ├── handler/                  # Chi HTTP handlers
│   ├── middleware/               # CORS, RateLimiter, JWT, HMAC, Logger
│   ├── websocket/                # WebSocket Hub & Client pumps
│   └── platform/
│       ├── database/             # Storage container factory (SQLite/PostgreSQL)
│       └── cache/                # Thread-safe in-memory TTL dedup cache
├── go.mod                        # Go module definition
├── go.sum                        # Go module checksums
├── main.go                       # Root execution shim
└── README.md                     # Backend developer guide
```
