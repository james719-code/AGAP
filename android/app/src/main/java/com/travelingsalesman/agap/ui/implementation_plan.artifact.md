# Implementation Plan - Remove Server URL UI Overrides and Re-stabilize Build

An external reset of the project files has reintroduced the "Server API URL" UI fields and caused compilation errors due to improper `@Composable` contexts. We will remove these fields and lock the app to the configuration provided in `local.properties`.

## Proposed Changes

### UI & ViewModel Cleanup

#### [MODIFY] [LoginScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/login/LoginScreen.kt)
- Remove the "Server API URL / IP" `OutlinedTextField`.
- Fix the `Snackbar` call: replace `uiState.errorMessage?.let { ... }` with `if (uiState.errorMessage != null) { ... }` to ensure a valid Composable context.

#### [MODIFY] [RegisterScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/register/RegisterScreen.kt)
- Remove the "Server API URL / IP" `OutlinedTextField`.
- Fix the `Snackbar` call: replace `uiState.errorMessage?.let { ... }` with `if (uiState.errorMessage != null) { ... }`.

#### [MODIFY] [ProfileScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/profile/ProfileScreen.kt)
- Remove the "Network Server Configuration" `SettingsSection`.

#### [MODIFY] [LoginViewModel.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/login/LoginViewModel.kt)
- Remove `serverUrl` from `LoginUiState`.
- Remove `onServerUrlChanged` handler.
- Remove server URL collection logic from `init`.

#### [MODIFY] [RegisterViewModel.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/register/RegisterViewModel.kt)
- Remove `serverUrl` from `RegisterUiState`.
- Remove `onServerUrlChanged` handler.
- Remove server URL collection logic from `init`.

#### [MODIFY] [ProfileViewModel.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/profile/ProfileViewModel.kt)
- Remove `serverUrl` from `ProfileUiState`.
- Remove `updateServerUrl` function.
- Remove server URL collection logic from `observeBaseData`.

### Core Network & Configuration Cleanup

#### [MODIFY] [ServerConfigManager.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/data/local/ServerConfigManager.kt)
- Remove `KEY_SERVER_URL` and `KEY_WS_URL`.
- Simplify `getServerUrl()` and `getWsUrl()` to return `BuildConfig` values exclusively.
- Remove `setServerUrl` function.
- Remove unused `Volatile` properties and flows for URLs.

#### [MODIFY] [NetworkModule.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/core/network/NetworkModule.kt)
- Remove the `dynamicUrlInterceptor`.
- Clean up unused imports (`toHttpUrlOrNull`).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the project builds and all unresolved references are gone.

### Manual Verification
- Deploy the app and confirm that the Server URL fields are gone from Login, Register, and Profile pages.
- Verify that the app connects to the endpoint defined in `local.properties`.
