# Bluetooth transfer review — 28 October 2026

## Evidence and limits

One phone is available: realme RMX3834, Android 15 / API 35, ADB device
`0N14406I221104BA`. There is no second AGAP phone. The normal debug installation
uses a guest session; AGAP Offline Test deliberately has no Internet or Bluetooth
permissions. Neither session supplies a live peer transfer.

The [saved startup log](../../artifacts/phone-review-2026-10-28/startup.log)
contains `SCAN_FAILED code=1 reason=ALREADY_STARTED` at 17:48:11, followed by
`SCAN_STARTED`. Android documents code 1 as an already-started application scan:
[ScanCallback reference](https://developer.android.com/reference/android/bluetooth/le/ScanCallback#SCAN_FAILED_ALREADY_STARTED).
It does not establish a failed packet delivery.

The [current BLE capture](../../artifacts/bluetooth-review-2026-10-28/device-ble-baseline.log)
contains Offline Test hardware/permission gating and signed-out worker events.
The [combined baseline summary](../../artifacts/bluetooth-review-2026-10-28/baseline-summary.json)
has **zero completed transfers** and no latency samples. It also includes the
previously observed backend socket timeout, which is separate from BLE delivery.
No measured speed improvement is claimed.

## Changes from code inspection

These were identified by following queue selection, native GATT callbacks,
Nearby application acknowledgements, and delivery persistence. They were not
reproduced as real radio failures on this single-phone setup.

| Finding | Change |
| --- | --- |
| Scan and advertising starts could overlap while native callbacks were pending. | Reserve each start before calling Android and release on stop/failure. Serialize native start/stop methods. Treat an already-started scan as active instead of starting an error/retry loop. |
| Queue ownership ended before asynchronous delivery bookkeeping finished. A new pulse could select the same row again. | Hold an attempt-specific lease through delivery-event persistence and queue removal/update. Stale callbacks and duplicate acknowledgements cannot release or commit a newer attempt. A service restart cannot bypass an in-progress commit. |
| Nearby announcement selection checked one displayed peer, while the actual send could choose a different peer that already received the announcement. | Exclude the origin and every previously served peer during actual peer selection on both transports. |
| An announcement awaiting another unique neighbor could block the next packet. | Inspect up to eight priority-ordered candidates and select the first with an eligible peer. This is a bounded window, not a guarantee against arbitrarily many blocked higher-priority packets. |
| An enabled Bluetooth adapter triggered queue reads even with no peers. | Return before queue reads/decoding when neither transport has a peer. Periodic health and maintenance checks remain. |
| Compact 13-byte response packets requested an MTU exchange. | Skip that exchange for payloads up to 20 bytes. Larger emergency and identity frames retain negotiation. |
| Failed GATT sends imposed a blanket 250 ms pause. | Release after failure bookkeeping and request another pulse immediately; the existing one-second same-peer cooldown still applies. |
| A very fast acknowledgement could finish before the sending callback updated the displayed state. | Check attempt ownership before displaying `AWAITING_ACK` or restoring Nearby transfer state. |

Receiver-possession acknowledgement semantics are preserved: GATT responds with
success only after processing/persistence or confirmation that the packet is
already held. Nearby still waits for its application acknowledgement. Existing
wire formats, timeouts, broadcast spread target, and legacy fallback policy remain.

Android 14 and later may negotiate 517 for the first MTU request regardless of
the requested value. Logs record the callback's actual MTU; the requested 64
must not be interpreted as the negotiated size. See the
[BluetoothGatt reference](https://developer.android.com/reference/android/bluetooth/BluetoothGatt#requestMtu(int)).

## Logger additions

- `attemptId` distinguishes local queue attempts within one process lifetime.
- `GATT_TRANSFER_STAGE` records monotonic total elapsed time and time spent in
  the previous stage: selecting, connecting, negotiating, discovering, sending,
  awaiting acknowledgement, and persisting.
- Relay success events record elapsed time through local delivery bookkeeping.
  `persistenceMs` records that final local step. Nearby's application ACK event
  is separate from this completion event.
- Receiver acceptance logs separate inbound queue wait from processing time.
- Transport-selection events record queue age where available. Queue age uses
  wall time and is clamped at zero; transfer timing uses a monotonic clock.
- Packet bodies and exact coordinates are not logged; tokens and peer IDs remain
  shortened by the existing logger.

Use Android Studio Logcat's `tag:AGAP_BLE`, or capture the normal debug process:

```powershell
$agapPid = (adb shell pidof com.travelingsalesman.agap.debug).Trim()
adb logcat --pid=$agapPid -v threadtime -s AGAP_BLE
```

Capture from process startup when investigating scanning. Capture a whole
transfer when investigating timing. Then summarize a saved capture from the
repository root:

```powershell
python -B scripts/analyze_bluetooth_log.py artifacts/ble-session.log
```

The script reports counts, failure reasons, and timing sample count/minimum/
median/95th percentile/maximum. Missing or invalid durations are unknown, never
zero. It does not infer a before/after speed improvement from unmatched captures.
Multiple input files must not overlap, and should come from the same app variant.

## Verification

The targeted Bluetooth suite has 40 passing JVM tests, including seven new
checks for simultaneous start requests, lease retention through persistence,
stale completion, duplicate ACKs, announcement peer exclusions/cooldowns, packet
size boundaries, and monotonic stage timing. The Python log analyzer has three
passing regression tests. Debug and Offline Debug APK builds and design-system
verification pass. See
[build/test output](../../artifacts/bluetooth-review-2026-10-28/verification-final.log).

Seven existing Offline Test account/integration tests also passed on the phone
in 6.874 seconds, including the no-Internet/no-Bluetooth permission check. See
[device test results](../../artifacts/bluetooth-review-2026-10-28/TEST-offline-accounts-phone.xml).
Both updated debug apps are installed. The normal app retained its guest session;
Offline Test is open at the Citizen / Responder / Admin chooser. The connected
test runner removed Offline Test at completion, so it was reinstalled and its
local sample data starts fresh. This did not reset the normal debug installation.

Fresh startup checks found no fatal exception in either app's captured logs.
The existing startup stutter remains: the normal debug capture logged 61 skipped
frames and a 1,198 ms frame; Offline Test logged 38 and 65 skipped frames and a
1,261 ms frame. These are individual debug startup observations, not a comparative
performance benchmark. No scan was expected in the guest or no-radio sessions,
so their absence of duplicate-start errors does not verify live scanner behavior.

- [Normal guest screen](../../artifacts/bluetooth-review-2026-10-28/normal-debug-smoke.png)
  and [startup log](../../artifacts/bluetooth-review-2026-10-28/normal-debug-smoke.log).
- [Offline account chooser](../../artifacts/bluetooth-review-2026-10-28/offline-debug-smoke.png)
  and [startup log](../../artifacts/bluetooth-review-2026-10-28/offline-debug-smoke.log).
- [APK hashes](../../artifacts/bluetooth-review-2026-10-28/apk-hashes.json)
  and [log-analyzer tests](../../artifacts/bluetooth-review-2026-10-28/log-analyzer-tests.log).

The remaining hardware check requires at least two authenticated normal AGAP
instances: test one emergency packet, a compact response, disconnect/retry, and
reconnect during delivery. Checking announcement spread to three distinct
neighbors requires additional peers. Compare matched before/after
runs with the same payload, distance, devices, and transport. The no-radio
Offline Test accounts must remain separate from that test.
