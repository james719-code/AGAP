# Walkthrough: Updated Alerts Icon for Citizen UI

I have updated the "Alerts" icon in the Citizen interface to use a **Megaphone (Campaign)** icon. This change better represents the page's role as a "Broadcast Feed" for official community alerts, differentiating it from the standard notification bell used for personal updates.

## UI/UX Refinements

### 1. Bottom Navigation Bar
-   **New Icon**: Changed the icon for the "Alerts" tab from `Icons.Outlined.Notifications` (Bell) to `Icons.Outlined.Campaign` (Megaphone) in [AgapBottomBar.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/components/AgapBottomBar.kt).
-   **Consistency**: This ensures that when a user looks at the bottom bar, they clearly distinguish between their home, map, and the broadcast feed.

### 2. Alerts Page Header
-   **Standardized Header Icon**: Updated the `LatestAnnouncementsHeader` in [AlertComponents.kt](file:///C:/Users/user/OneDrive/Documents/GitHub/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/components/AlertComponents.kt) to use the same Megaphone icon.
-   **Visual Identity**: This strengthens the visual link between the navigation item and the actual content of the page.

## Logic & Connectivity

-   **Unchanged Top Bar**: The **Notification Bell** in the Top Bar remains a bell, as it serves as the entry point for personal "Notifications & Telemetry" (ACKs, mesh queue, etc.).
-   **Clean Separation**: Citizens now have a bell for "My Activity" and a megaphone for "Community Alerts".

## Verification Results

### Automated Tests
-   Ran `./gradlew :app:compileDebugKotlin`: **PASSED**. All icon imports and component references are valid.

> [!TIP]
> The use of the megaphone icon aligns with the "Broadcast Feed" branding used in the screen's text, making the feature more intuitive for first-time users.
