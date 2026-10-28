# AGAP phone review — 28 October 2026

The current workspace build is installed on the connected phone with a working
guest workflow. The final build, design-system check, and **9 on-device tests
passed**. The app was left open on the guest home screen in dark mode.

## Device and scope

- realme RMX3834, Android 15 / API 35, arm64, 720 × 1600, density 320.
- Standard device font scale: 1.0. Home components were also tested at 1.5× text.
- Tested package: `com.travelingsalesman.agap.debug`, version `1.0-debug`.
- Existing `com.james.kaagapay` installation and its data were preserved.
- Guest home, SOS preview, map, Alerts, Profile, theme changes, sign-in transition,
  keyboard scrolling, and reopening the app were exercised. No emergency alert or
  report was transmitted, and no real account credentials were used.

## Guest workflow delivered

**Sign in → Continue as guest → Home / Map / Alerts / Profile.** Guests can explore
the SOS controls and map and change the theme. SOS release and the report button
show a sign-in dialog, with a “Keep exploring” option. Alerts and Profile show
account prompts instead of private data. Choosing sign-in returns to the real
sign-in screen, where account creation remains available.

Guest mode persists across process restarts, uses no fabricated user or token,
and never creates the authenticated screen view models. Account sync skips signed
out sessions. The realtime connection waits for authentication and closes when
the token is removed. Map tiles can still download while browsing.

## Findings

| Priority | Finding | Status and evidence |
| --- | --- | --- |
| P1 | Configured backend was unreachable from the phone. | **Open environment issue.** Initial logs contain DNS lookup failures and 30-second connection timeouts for the configured homeserver, including WebSocket failure. Successful sign-in and server sync could not be verified. See [baseline log](../../artifacts/phone-review-2026-10-28/startup.log). |
| P2 | A realtime connection started before sign-in. | **Fixed for signed-out and guest sessions.** The connection now observes authentication. A regression test verifies zero connection attempts as a guest and intercepts the first attempt after a synthetic sign-in before it can reach a network. Final guest logs show no backend HTTP requests or WebSocket attempts. |
| P2 | Cold startup and some transitions stutter on this phone. | **Open.** Final startup logs show a 1,269 ms classloader warning, a 1,572 ms reschedule-receiver warning, a 397 ms vsync delay, and 40 skipped frames. Another transition skipped 34 frames. The sampled startup rendering report recorded 8 janky frames out of 115 (6.96%), with 95th-percentile frame time 129 ms. These are debug-build observations, not a release benchmark. See [startup log](../../artifacts/phone-review-2026-10-28/final-guest-session.log), [later log](../../artifacts/phone-review-2026-10-28/final-review.log), and [rendering statistics](../../artifacts/phone-review-2026-10-28/final-gfxinfo.txt). |
| P2 | BLE scanning reported a duplicate start. | **Observed in the initial build; signed-in retest needed.** At 17:48:11, `SCAN_FAILED code=1 reason=ALREADY_STARTED` was followed by `SCAN_STARTED`. This is not evidence that BLE delivery failed. Guest mode does not start scanning, so its clean logs do not prove the authenticated scanning issue is fixed. See [baseline log](../../artifacts/phone-review-2026-10-28/startup.log). |
| P2 | Opening the keyboard pushed sign-in branding under the status bar. | **Corrected in the tested flow.** MainActivity now uses keyboard resize behavior. Final screenshots show branding below the status bar, and scrolling exposes Sign in, Create an account, and Continue as guest. Entering guest mode directly from the open keyboard worked. Compare [before](../../artifacts/phone-review-2026-10-28/02-permissions.png), [after](../../artifacts/phone-review-2026-10-28/22-final-login-keyboard.png), and [scrolled controls](../../artifacts/phone-review-2026-10-28/23-final-keyboard-scrolled.png). |
| P3 | The detailed-report action is below the first viewport. | **Usability observation.** The SOS card dominates this 360 dp-wide display, particularly at 1.5× text. The report action remains reachable by scrolling, as verified by the tests. Consider reducing the card height or placing the report action higher. |

No fatal exception or ANR was observed in the captured app logs or recorded exit
history. The exit history includes one background system kill described as
“Watermark Pressure”; the other recorded exits were test completion or explicit
force stops. Vendor GPU/property warnings also appear during startup and are not,
by themselves, evidence of an application crash.

## Verification

| Check | Result |
| --- | --- |
| Debug app and Android test APK builds | Passed |
| Design-system verification | Passed |
| Production home components: light, dark, and 1.5× text | 3 passed |
| Guest report confirmation, cancellation, and sign-in | Passed |
| Guest SOS drag opens sign-in instead of sending | Passed |
| Guest Alerts / Profile prompts and theme switching | Passed |
| Guest persistence, exit, and absence of user identity | Passed |
| Real session replaces guest and retains responder role | Passed with isolated synthetic session |
| Guest realtime gating | Passed with an interceptor preventing actual network access |
| Live map, app restart, guest exit/re-entry, keyboard-open entry | Passed manually through ADB |

Final test output: [9 passing tests](../../artifacts/phone-review-2026-10-28/instrumentation-final.log).
Build output: [final app build](../../artifacts/phone-review-2026-10-28/guest-final-build.log)
and [final test build](../../artifacts/phone-review-2026-10-28/test-build.log).

Live account authentication, responder/admin screens, server-side report delivery,
and multi-phone BLE relay remain unverified. The synthetic session tests check
local behavior and do not establish server compatibility.

## Screenshots and diagnostics

- [Final guest home](../../artifacts/phone-review-2026-10-28/25-final-guest-dark.png)
- [Guest map](../../artifacts/phone-review-2026-10-28/13-guest-map.png)
- [Guest SOS sign-in prompt](../../artifacts/phone-review-2026-10-28/19-guest-sos-prompt.png)
- [Guest Alerts](../../artifacts/phone-review-2026-10-28/14-guest-alerts.png)
- [Guest Profile](../../artifacts/phone-review-2026-10-28/16-guest-dark-profile.png)
- [Home at larger text](../../artifacts/phone-review-2026-10-28/home-component-screens/home-large-text.png)
- [Process exit history](../../artifacts/phone-review-2026-10-28/exit-info.txt)

Evidence is stored under `artifacts/phone-review-2026-10-28/`. Earlier logs and
test attempts are retained; `instrumentation-final.log` contains the final result.

Final app APK SHA-256:
`2E2EA657E58A276117177657953323EAAC7A2923773CD14F41CD6558482DA615`.
