# Offline test accounts

Open **AGAP Offline Test** on the phone and tap an account button on the sign-in
screen. The normal AGAP app is a separate installation.

| Role | Username | Password |
| --- | --- | --- |
| Citizen | `test.citizen` | `offline123` |
| Responder | `test.responder` | `offline123` |
| Admin | `test.admin` | `offline123` |

**Profile → Logout** returns to the account chooser. Local test data and queued
reports survive sign-out and reopening. Clearing the Offline Test app's storage
resets its fixtures and removes its queued reports.

## What to try

- Citizen: select an emergency type, drag SOS, open a detailed incident report,
  use the sample location or map picker, and review notifications/profile data.
  SOS saves a real local report and relay-queue entry and confirms that no alert
  was transmitted.
- Responder: open the dashboard, inspect the sample assignment, tasks, incident
  detail, and operational messages. Status changes use local fixture responses.
- Admin: inspect incident triage, the map, user/agency/unit management, profiles,
  and advisories. Supported edits are saved in the test app's local fixture store.

All screens identify the build as **Offline test**. Connectivity remains offline.
Profile and management calls are answered inside the app, with no backend
connection. There are no real accounts or real emergency transmissions.

The location is a sample near Legazpi, not the phone's actual location. The bundled
test map contains 15 previously cached tiles at zoom 15, with limited coverage
around that sample location. It is not a complete deployment map. Normal Internet
and multi-phone BLE behavior must be tested with the normal build.

## Verification on 28 October 2026

Tested on realme RMX3834 / Android 15. All three account buttons were exercised on
the phone, including logout and switching roles. A Citizen SOS was saved locally;
the Responder dashboard showed its sample assignment; Admin loaded all three test
users and its local map and management screen.

Seven on-device regression checks passed: all role logins, rejection of an incorrect
password, persistence of profile edits, responder dispatch status changes, local
SOS queue storage, Admin management/advisories, and Android permission enforcement
with usable bundled map tiles. Each test also checked for
zero DNS lookups and socket connections. The normal debug build and release Kotlin
sources compiled successfully. The release implementation has no fixture accounts.

Evidence is in `artifacts/offline-accounts-2026-10-28/`, including screenshots,
app logs, build output, and `offline-account-tests.log`.

The later [Bluetooth review](bluetooth-transfer-review-2026-10-28.md) reran these
seven tests successfully and installed the updated APK. The connected test runner
removed this test app at completion, so it was reinstalled with fresh local sample
data and left on the account chooser. The normal debug app retained its guest
session. Offline Test still has no Internet or Bluetooth permissions.

## UI observations exposed by the sample data

- The narrow Admin dashboard wraps its OFFLINE badge into multiple lines and
  truncates its dispatch metric label. See `12-admin-offline-dashboard.png`.
- Long emergency-type names from the shipped catalog are truncated around the
  Citizen SOS control. See `03-citizen-local-sos.png`.
- A queued offline Citizen SOS is persisted, but the map says “No active report”
  until a matching incident is available in the incident cache. The queue entry
  does not constitute command receipt. See `05-citizen-offline-map.png`.

These observations are distinct from account login and network isolation, which
passed. The initial blank map and dark account-heading contrast were corrected
in the offline test build.
