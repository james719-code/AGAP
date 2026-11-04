# Bluetooth processing optimization — 31 October 2026

Repeated presence beacons now refresh only their peer's timestamp, device, and
RSSI. New peers still publish counts and wake the relay immediately. Stale peers
are removed by the existing 1.5-second pulse, and counts are republished only
when membership changes. Repeated-beacon bookkeeping no longer scans the entire
peer map or allocates combined peer sets on every callback. A stale displayed
count may persist until the next pulse; transfer selection still filters stale
peers immediately.

Beacon identities, packet tokens, and relay payloads now use fixed-width hex
conversion without per-byte formatters, regular expressions, or intermediate
lists. Beacon decoding reads the identity directly from its six-byte slice.
Emergency and response encoding calculate CRC over the existing buffer instead
of allocating a payload copy. Lowercase persisted identities, strict ASCII
validation, packet bytes, CRCs, scan timing, relay priorities, hop limits,
timeouts, and acknowledgement behavior are preserved.

## Local JVM measurements

Median of five 10,000-operation samples after warmup, using the shipped Kotlin
helper and the previous implementations as the baseline:

| Payload | Encode before → after | Decode before → after |
| --- | --- | --- |
| 6 bytes (identity) | 712 → 115 ns | 579 → 158 ns |
| 21 bytes (compact emergency) | 1,590 → 32 ns | 455 → 47 ns |
| 96 bytes (conversion stress case) | 6,842 → 81 ns | 2,035 → 200 ns |

These are conversion microbenchmarks on the development computer. They do not
measure radio throughput, discovery latency, range, phone battery use, or SOS
delivery time. The 96-byte case exercises the helper; it is not a new packet size.

## Reproduction

From `android`, using the installed Android Studio JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:verifyDesignSystem --offline
$stdlib = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.1.10" -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$benchmarkClasspath = @('app/build/tmp/kotlin-classes/debug', 'app/build/tmp/kotlin-classes/debugUnitTest', $stdlib) -join ';'
& "$env:JAVA_HOME/bin/java.exe" -cp $benchmarkClasspath com.travelingsalesman.agap.core.bluetooth.MeshHexBenchmark
```

Regression coverage checks all 256 byte values, signed bytes, leading zeros,
uppercase inputs, malformed/non-ASCII inputs, slice boundaries, and fixed
emergency/acknowledgement wire bytes and CRCs. Existing Bluetooth codec, relay,
visibility, and transfer-control tests also run in the Android unit suite.

The remaining validation requires multiple phones: discovery and forwarding with
the screen off, intermittent peers, simultaneous SOS/ACK traffic, transport
fallback, and controlled battery/latency measurements.
