# Implementation Plan - Unify Citizen Notification Page with Admin

The goal is to ensure Citizens use the same `NotificationsScreen` as Admins when clicking the Bell Icon, but with the "ALERTS" tab removed (to avoid redundancy with the bottom bar's Alerts tab). This turns the Citizen's notification center into a personal **SOS & Telemetry Center**.

## Proposed Changes

### UI Layer

#### [MODIFY] [NotificationsScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/notifications/NotificationsScreen.kt)
- Filter the `tabs` list for Citizens to exclude `NotificationTab.NOTIFICATIONS`.
- Update the screen title for Citizens to "PERSONAL ACTIVITY & TELEMETRY".
- Ensure the default selected tab for Citizens is `MY SOS` (SENT).

### Logic Layer

#### [MODIFY] [NotificationsViewModel.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/notifications/NotificationsViewModel.kt)
- Ensure the `mySosHistory` is properly observed and exposed for the `MY SOS` tab. (Re-applying this to ensure persistence).

## Verification Plan

### Manual Verification
1. Log in as a **Citizen**.
2. Tap the **Bell Icon** in the top bar.
3. Verify the screen title is "PERSONAL ACTIVITY & TELEMETRY".
4. Verify there is **no ALERTS tab**.
5. Verify you see **MY SOS**, **QUEUE**, and **RELAYED** tabs.
6. Verify the bottom tab **Alerts** still shows the community broadcast feed.

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build.
