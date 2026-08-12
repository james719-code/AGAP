# Implementation Plan - Responder Tactical Dashboard Redesign

The user wants to redesign the Responder's home screen to be "inspired by the admin" but with contents tailored for a field responder. This involves moving from a full-screen map with overlays to a tactical "Command Dashboard" structure (using `LazyColumn` and high-fidelity cards), similar to the `AdminMobileScreen`.

## Proposed Changes

### UI Layer - Dashboard & Components

#### [MODIFY] [ResponderMapScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/responder/ResponderMapScreen.kt)
- Refactor the screen to use a `LazyColumn` as the root container.
- Implement a `ResponderHeader`: Shows unit callsign, operator name, and a "RESPONDER" pill.
- Implement a **Tactical Metrics Row**:
    - **ACTIVE TASKS**: Count of current dispatches (Red).
    - **NAV DISTANCE**: Distance to selected target (Amber).
    - **MESH PEERS**: Count of nearby relay nodes (Blue).
- Implement a `MapSection`: A mini-map showing the current unit location and the route to the target incident, with an "EXPAND" button to go to a full map view.
- Implement **Section Titles** for "Assigned Tasks" and "Field Awareness" (relayed packets/nearby incidents).
- Migrate the `DispatchQueueItem` logic into high-fidelity cards.

#### [NEW] [ResponderDetailMapScreen.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/responder/ResponderDetailMapScreen.kt)
- Create a dedicated full-screen map view for responders who need precise navigation. This allows the primary home screen to remain a high-level dashboard.

### Logic Layer - ViewModel Refinement

#### [MODIFY] [ResponderMapViewModel.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/responder/ResponderMapViewModel.kt)
- Ensure distance and ETA calculations are updated as the user moves.
- Provide a summary of "Field Awareness" (all incidents known to the device, including those not assigned to the responder).

### Navigation & Identity

#### [MODIFY] [AgapNavGraph.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/navigation/AgapNavGraph.kt)
- Add a new route `RESPONDER_DASHBOARD_ROUTE`.
- Map the Responder's home destination to this new dashboard.
- Provide a path to the full-screen map.

#### [MODIFY] [AgapBottomBar.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/components/AgapBottomBar.kt)
- Update Responder bottom tabs: **Dashboard**, **Map**, **Alerts**, **Profile**.

## Verification Plan

### Manual Verification
1. Log in as a **Responder**.
2. Verify the new **Tactical Header** displays the correct unit callsign and greeting.
3. Check that the **Metrics Row** correctly counts active assignments and mesh peers.
4. Verify the **Mini-Map** correctly displays the route to the selected assignment.
5. Tap **EXPAND** and verify navigation to the full-screen map.
6. Verify the **Bottom Bar** correctly reflects the new dashboard-first structure.

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure no UI breaks occurred during the layout refactor.
