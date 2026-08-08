# AGAP Location Sources & Coordinate Encoding Specification

## 1. Overview

Accurate spatial telemetry is critical for emergency response. AGAP defines four explicit **Location Sources** to ensure responders understand the exact origin and confidence level of incident coordinates.

---

## 2. Location Source Types

| Enum Code | Location Source Name | Bit Value (`Byte 1`) | Accuracy Byte (`Byte 16`) | Coordinate Value (`Bytes 8–15`) | Description |
|---|---|---|---|---|---|
| `0` | `GPS` | `00` | `0`–`254` meters | High-precision E7 coordinates | Active GNSS satellite fix from device hardware |
| `1` | `MAP_PIN` | `01` | `255` (Unavailable) | High-precision E7 coordinates | Manual pin tap by user on offline map canvas |
| `2` | `BARANGAY_REFERENCE` | `10` | `255` (Unavailable) | Centroid E7 coordinates | Representative centroid from cached local Barangay lookup |
| `3` | `UNKNOWN` | `11` | `255` (Unavailable) | `0, 0` | Location fix unavailable; SOS sent without location |

---

## 3. Fixed-Point E7 Coordinate Encoding

Coordinates are encoded as 32-bit signed integers in big-endian format using E7 precision:

$$\text{latE7} = \text{round}(\text{latitude} \times 10,000,000)$$
$$\text{lonE7} = \text{round}(\text{longitude} \times 10,000,000)$$

### Resolution & Accuracy
- **E7 Resolution**: $\approx 0.0000001^\circ \approx 1.1 \text{ cm}$.
- **Accuracy Byte**: Tells responders the radius of confidence reported by Android GNSS (e.g. `3` = $\pm 3\text{m}$, `254` = $\ge 254\text{m}$, `255` = accuracy unknown/manual pin).

---

## 4. UI & Rendering Rules

1. **`GPS` Display**: Render pin with accuracy confidence circle corresponding to accuracy meters.
2. **`MAP_PIN` Display**: Render pin with `User Selected Pin` badge.
3. **`BARANGAY_REFERENCE` Display**: Render area highlight or centroid marker with `Approximate Barangay Location` badge (do NOT display as exact GPS fix).
4. **`UNKNOWN` Display**: Display `Location Unavailable` in text feed. **MUST NEVER PLOT `(0,0)` ON MAPS**.

---

## 5. Strict Substitution Constraints

> [!CAUTION]
> **No Silent Substitution**: The system must NEVER silently substitute sample coordinates (e.g., hardcoded Legazpi City defaults), home profile addresses, or relay device positions when generating an emergency packet. If location cannot be determined, set source to `UNKNOWN` (`11`).
