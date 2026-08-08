# AGAP Data Synchronization & Gateway Enrichment Guide

## 1. Overview

AGAP seamlessly bridges offline Bluetooth Low Energy (BLE) store-and-forward telemetry with the online backend server using an asynchronous **Gateway Shortcut & Minimal-to-Full Enrichment** pipeline.

---

## 2. Gateway Shortcut Flow

When an emergency occurs offline:

```text
Citizen (Offline)
    │  [21-Byte BLE SOS Packet]
    ▼
Peer Relay Node (Offline)
    │  [Moves into cellular coverage / Wi-Fi]
    ▼
Gateway Node (Online)
    │  [POST /api/v1/telemetry/uplink]
    ▼
AGAP Backend Server
    │  [Stores MINIMAL Report, triggers WebSocket alert]
    ▼
Admin / Dispatcher Dashboard
```

1. **BLE Mesh Transport**: The 21-byte BLE packet containing the `transport_token`, E7 coordinates, emergency classification, and location source propagates hop-by-phone.
2. **Gateway Ingestion**: Any AGAP node (Citizen, Responder, or Admin device) that receives the BLE packet and possesses an active internet connection acts as an automatic **Gateway Ingestion Node**.
3. **Minimal Uplink**: The gateway posts the raw packet metadata to `POST /api/v1/telemetry/uplink`.
4. **Minimal Incident Creation**: The backend creates an incident with `data_level = MINIMAL`, linking it to the 6-byte `transport_token`.

---

## 3. Full-Detail Enrichment Sync

When the originating Citizen device regains cellular data or Wi-Fi connectivity:

```text
Citizen Device (Now Online)
    │  [Syncs pending local reports]
    │  [POST /api/v1/incidents/enrich]
    ▼
Backend Server
    │  [Matches transport_token]
    │  [Upgrades MINIMAL -> FULL report]
    ▼
Updated Dashboard (Shows Citizen Name, Phone, Address, Full Notes)
```

1. **Token Matching**: The Citizen device sends its full local incident payload (including citizen identity, phone number, description notes, detailed address fields) along with the original `transport_token`.
2. **In-Place Upgrade**: The backend matches the existing `MINIMAL` record by `transport_token`, updates its fields to `data_level = FULL`, and broadcasts an `INCIDENT_UPDATED` WebSocket event.
3. **No Duplication**: Strict database uniqueness on `transport_token` prevents duplicate incident creation.

---

## 4. Downward Resolution Propagation

When an Admin or Responder resolves an incident on the online dashboard:

1. **Backend Status Update**: The backend updates the incident status to `RESOLVED` in SQLite/PostgreSQL.
2. **WebSocket Push**: Active online Responder devices receive an instant status push notification.
3. **Offline BLE Quench**: Online Responder Android devices emit a 13-byte `STATUS RESOLVED` BLE broadcast packet.
4. **Terminal Suppression**: Nearby offline mesh nodes receive the `STATUS RESOLVED` packet, update local tombstones, and quench active SOS forwarding for that transport token.
