# AGAP BLE Mesh Protocol v2 Specification

## 1. Overview

AGAP (Accelerated Geolocation-based Assistance and Protection) uses a lightweight, peer-to-peer Bluetooth Low Energy (BLE) store-and-forward mesh network to relay emergency distress telemetry when cellular networks or mobile internet access are unavailable.

Protocol Version 2 replaces legacy 12-byte packets with:
1. **21-Byte Emergency Distress Packet** (`SOS` and `INCIDENT_REPORT`)
2. **13-Byte Downward Response Packet** (`ACK` and `STATUS`)

> [!IMPORTANT]
> **No Encryption Claim**: BLE v2 transport packets use **CRC-16/CCITT-FALSE** for corruption detection across broadcast hops. They are not encrypted or authenticated over the air in v2. Do not claim BLE transport encryption.

---

## 2. Emergency Packet Format (21 Bytes)

The 21-byte emergency packet fits within standard BLE advertising payload limits and carries high-precision E7 coordinates alongside horizontal accuracy metadata.

### 2.1 Field Layout

| Byte(s) | Size (Bytes) | Field Name | Description |
|---|---:|---|---|
| `0` | 1 | Header | Protocol version, packet type, emergency type, immediate danger bit |
| `1` | 1 | Metadata | Subject relation, hop count (4 bits), location source (2 bits) |
| `2–7` | 6 | Transport Token | 6 random binary bytes (48-bit unique transport identifier) |
| `8–11` | 4 | Latitude E7 | `int32` signed big-endian integer (`round(lat * 10,000,000)`) |
| `12–15` | 4 | Longitude E7 | `int32` signed big-endian integer (`round(lon * 10,000,000)`) |
| `16` | 1 | Location Accuracy | Unsigned byte (0–254 meters; 255 = unknown/unavailable) |
| `17–18` | 2 | Creation Time Slot | `uint16` big-endian minute count modulo 65536 from AGAP Epoch |
| `19–20` | 2 | CRC-16 | `uint16` big-endian CRC-16/CCITT-FALSE over bytes 0..18 |

Total Length: **21 Bytes**

---

## 3. Byte 0 & Byte 1 Bit Layouts

### 3.1 Byte 0 (Header)
```text
Bits 7..6 : Protocol Version (01 = Version 2)
Bits 5..4 : Packet Type
            00 = SOS
            01 = INCIDENT_REPORT
            10 = ACK
            11 = STATUS
Bits 3..1 : Emergency Classification Type
            000 = UNKNOWN
            001 = MEDICAL
            010 = ACCIDENT
            011 = FIRE
            100 = DISASTER
            101 = CRIME
Bit 0     : Immediate Danger Flag (1 = Immediate Life-Threatening Danger, 0 = Standard)
```

### 3.2 Byte 1 (Metadata)
```text
Bits 7..6 : Subject Relation
            00 = SELF
            01 = OTHER_PERSON
            10 = PUBLIC_OR_UNKNOWN
            11 = RESERVED
Bits 5..2 : Hop Count (0..15, incremented per hop)
Bits 1..0 : Location Source
            00 = GPS
            01 = MAP_PIN
            10 = BARANGAY_REFERENCE
            11 = UNKNOWN
```

---

## 4. Response Packet Format (13 Bytes)

`ACK` and `STATUS` response packets provide downward multi-hop acknowledgement and status propagation without resending coordinate data.

### 4.1 Field Layout

| Byte(s) | Size (Bytes) | Field Name | Description |
|---|---:|---|---|
| `0` | 1 | Header | Protocol version (01), packet type (`10`=ACK, `11`=STATUS) |
| `1` | 1 | Metadata | Hop count (4 bits), flags |
| `2–7` | 6 | Target Report Token | Matches the 6-byte transport token of the target report |
| `8` | 1 | Status Message Code | Enum status/predefined message code (`1`=SOS_RECEIVED, `2`=HELP_EN_ROUTE, `3`=RESOLVED, `4`=CANCELLED, etc.) |
| `9–10` | 2 | Creation Time Slot | `uint16` minute count slot of response creation |
| `11–12` | 2 | CRC-16 | `uint16` CRC-16/CCITT-FALSE over bytes 0..10 |

Total Length: **13 Bytes**

---

## 5. Location Source Rules

Every emergency packet explicitly specifies its `location_source`:

1. **`GPS` (`00`)**: Exact GNSS fix. `latE7` & `lonE7` contain high-precision fixed point. `accuracy` stores measured accuracy in meters (0–254).
2. **`MAP_PIN` (`01`)**: Citizen manually tapped position on offline map. `accuracy` is set to `255` (unknown/manual).
3. **`BARANGAY_REFERENCE` (`10`)**: Representative centroid coordinate of selected Barangay from offline cache. `accuracy` is set to `255`.
4. **`UNKNOWN` (`11`)**: Location unavailable. `latE7` & `lonE7` are set to `0, 0`. `accuracy` is set to `255`. Receivers **MUST NEVER** plot `(0,0)` on maps.

> [!CAUTION]
> Devices must never silently substitute sample, default home, or relay device coordinates for an emergency location.

---

## 6. Privacy & Silent Background Relay

Citizen mobile devices participate in the mesh as store-and-forward relays under strict privacy boundaries:

- **Silent Background Relay**: Relay citizen devices forward packets in the background without launching intrusive popups or displaying another citizen's emergency UI.
- **Data Minimization**: Relay citizen nodes store only raw transport bytes, transport tokens, hop counts, and deduplication timestamps.
- **No PII Exposure**: Citizen devices MUST NOT store or display another person's name, profile, phone number, text description, medical info, or images.

---

## 7. Time-Bounded Relaying & Deduplication

AGAP avoids indefinite relaying to preserve battery and prevent network congestion:

- **SOS / Incident Packet TTL**: 2 Hours (configurable)
- **ACK / STATUS Packet TTL**: 1 Hour (configurable)
- **Maximum Hops**: 15 Hops max
- **Deduplication Table**: Keyed by `(transport_token, packet_type)`. Duplicate packets within TTL are dropped.

---

## 8. Terminal Status & Stale Packet Suppression

When an incident reaches a terminal state (`RESOLVED` or `CANCELLED`):

1. A `STATUS` packet with status code `RESOLVED` (`3`) or `CANCELLED` (`4`) is broadcast across the BLE network.
2. Receiving devices record a **Terminal Tombstone** (`transport_token`, `terminal_status`, `tombstone_expires_at` = +3 hours).
3. Active relay queues purge any pending SOS packets matching the transport token.
4. If an older, delayed `SOS` packet with the same transport token arrives later, it is **suppressed** and will not recreate an active incident on Responder or Admin dashboards.

---

## 9. Error Detection (CRC-16)

All v2 packets append a 16-bit CRC-16 checksum using the **CRC-16/CCITT-FALSE** polynomial (`0x1021`, initial value `0xFFFF`, no bit reversal). Packets failing CRC validation MUST be discarded immediately.
