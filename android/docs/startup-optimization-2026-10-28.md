# Startup optimization — 28 October 2026

The updated normal debug and Offline Test builds are installed on the realme
RMX3834 / Android 15 phone. This pass removes unnecessary guest startup work and
shortens the splash sequence. It does not resolve the remaining first-frame
stutter or establish release-build performance.

## Changes

- Shortened the minimum custom splash display from 1,900 ms to 600 ms and fitted
  the logo, wave, and loading fades inside that interval. Authentication and
  theme loading still complete before navigation. Slow preference reads are
  still awaited; they are not bypassed by the shorter timer.
- Changed MainActivity's Bluetooth manager dependency to `dagger.Lazy`. Guest
  and signed-out entry no longer instantiate the Bluetooth manager, its packet
  processing dependencies, and its Room observers just to draw the screen.
  Authenticated permission/service actions still obtain the same singleton.
- Scoped application sync observers to authenticated sessions. Connectivity,
  gateway queue, report queue, and transmission-mode collectors start on sign-in
  and cancel on logout. Repeated authenticated emissions do not duplicate them.
  Each child remains supervised so one failing observer does not cancel its peers.
- Stopped Offline Test from scheduling sync/uplink work at the enqueue methods,
  as well as retaining the existing worker execution guards. Its local fixture
  logins and reports still work. It retains its no-Internet/no-Bluetooth manifest.

Already scheduled Android work is not erased by this change. Its existing
authentication checks still apply when Android runs it. Catalog import and map
cache maintenance remain separate background tasks. Queued reports are retained.

## Phone observations

Three cold-process guest launches were captured before and after the update.
Each capture waits six seconds after Android's launcher-activity measurement.
The APK update retained guest preferences and did not clear app data.

| Diagnostic | Before | After |
| --- | --- | --- |
| Median first process-log timestamp → MainActivity render request | 3,876 ms | 2,470 ms |
| Median preference-route resolution → MainActivity render request | 1,611 ms | 582 ms |
| Bluetooth hardware initialization events per guest launch | 1 in all three samples | 0 in all three samples |
| New uplink enqueue events per guest launch | 1 in all three samples | 0 in all three samples |
| Signed-out sync worker skip events per launch | 1 in all three samples | 0 in all three samples |
| Fatal exceptions in these captures | 0 | 0 |

The render-request marker occurred 1,406 ms earlier by the sample medians. This
marker is logged before Compose draws; it is not time until the app is usable.
Android's `am start -W` TotalTime measures SplashActivity rather than completion
of the whole two-activity startup. The phone auto-locked during this session;
display/power state was not controlled across timing samples. These figures are
diagnostic observations from three debug runs, not a controlled speed benchmark.
The source-level removal of 1,300 ms of enforced splash hold is independently
verifiable.

Frame stutter remains. Before captures reported 42, 37, and no skipped-frame
warning; after captures reported 40, 38, and 42. These small samples do not show
an improvement in frame smoothness. Debug class loading and initial Compose work
remain visible in the logs.

After the user manually unlocked the phone, the normal guest screen and Offline
Test account chooser were reopened and captured for visual verification. The
installed apps retain their distinct roles and the Offline Test chooser remains
available for Citizen, Responder, and Admin practice.

## Verification

- **43 JVM tests passed**: 40 Bluetooth checks plus three new authenticated-work
  checks covering no observer creation while signed out, one active session
  with cleanup/restart on logout/login, and independent failure supervision.
- **Eight phone tests passed** in 2.428 seconds: the seven offline account,
  persistence, queue, and permission tests plus a regression check that the
  Offline Test enqueue methods do not add network workers.
- Normal debug APK, Offline Test APK, Offline Test instrumentation APK, and
  design-system verification passed.
- Phone tests were invoked directly with ADB instrumentation after installing
  the APKs with `-r`; the Gradle connected-test cleanup that removed the test app
  on the previous pass was not used. App storage was not cleared on this pass.

Evidence:

- [Build and JVM results](../../artifacts/startup-optimization-2026-10-28/verification-final.log)
- [Phone test output](../../artifacts/startup-optimization-2026-10-28/offline-phone-tests.log)
- [Timing/event summary](../../artifacts/startup-optimization-2026-10-28/comparison.json)
- [Normal guest screen](../../artifacts/startup-optimization-2026-10-28/guest-final.png)
- [Offline account chooser](../../artifacts/startup-optimization-2026-10-28/offline-final.png)
- [APK hashes](../../artifacts/startup-optimization-2026-10-28/apk-hashes.json)

Raw logs, rendering dumps, launch output, and copied JUnit results are in the same
artifact directory. No real account login, backend availability, phone-to-phone
transfer, battery saving, or release-build speedup was measured by this pass.
