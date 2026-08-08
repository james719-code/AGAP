# Implementation Plan - Fix Refresh Functionality in Alerts & Telemetry

This plan outlines the changes required to synchronize the refresh behavior of the "Alerts & Telemetry" screen with other application screens, ensuring it updates both local outbox packets and remote incidents across all tabs.

## User Review Required

> [!IMPORTANT]
> The refresh action will now trigger both a network fetch for confirmed incidents and a background flush of pending packets. This may increase data usage slightly compared to the previous outbox-only flush.

- **Renaming**: `isFlushing` will be renamed to `isRefreshing` to better reflect the combined action.
- **Empty State**: The empty state UI will be modified to support scroll gestures, which is required for the Material 3 `PullToRefreshBox` to function.

## Proposed Changes

### UI Layer

#### [SentMessagesScreen.kt](app/src/main/java/com/travelingsalesman/agap/ui/screens/sent/SentMessagesScreen.kt)
- Rename `isFlushing` usage to `isRefreshing`.
- Refactor `EmptyStatePlaceholder` to be scrollable by wrapping its content in a `LazyColumn`.

### ViewModel Layer

#### [SentMessagesViewModel.kt](app/src/main/java/com/travelingsalesman/agap/ui/screens/sent/SentMessagesViewModel.kt)
- Update `SentMessagesUiState` data class to use `isRefreshing`.
- Update `refresh()` to perform both `flushPendingPacketsUseCase()` and `incidentRepository.fetchRemoteIncidents()`.
- Ensure `isRefreshing` state management uses `try-finally` for robustness.
- Update `flushOutboxNow()` to be consistent with the renamed state.

## Verification Plan

### Automated Tests
- Run `SentMessagesViewModelTest` to verify the state transitions during `refresh()`.
- Verify `IncidentRepository` calls are made during the refresh flow.

### Manual Verification
1. Open "Alerts & Telemetry".
2. Switch to an empty tab (e.g., "RELAYED" if no relays exist).
3. Perform a pull-to-refresh gesture.
4. Observe the refresh indicator and verify that both outbox flushing and incident fetching are initiated.
5. Confirm the snackbar displays "Data refreshed successfully" (or appropriate error) upon completion.
6. Verify the "FLUSH OUTBOX" button correctly reflects the `isRefreshing` state.
