# Implementation Plan - Redesign Profile Screen

Redesign the `ProfileScreen` to match the provided mockup. This will be a placeholder-based implementation as requested.

## User Review Required

> [!NOTE]
> All data in this redesign will be hardcoded placeholders to match the mockup exactly. Logic for updating these values (e.g., "Edit" buttons, "Switches") will be added in future tasks.

## Proposed Changes

### UI Layer

#### [MODIFY] [ProfileScreen.kt](file:///C:/Users/user/StudioProjects/AGAP/android/app/src/main/java/com/travelingsalesman/agap/ui/screens/profile/ProfileScreen.kt)
- Update the `AgapTopBar` usage to match the mockup (Title "Profile" and a Settings icon).
- Implement the following sections within a `verticalScroll` Column:
    - **User Header Card**: Avatar, Name, Citizen ID, and QR Code placeholder.
    - **Emergency Contact Card**: Contact info with phone/message actions.
    - **Health Stats Row**: Blood Type and Medical Notes cards side-by-side.
    - **Emergency Preferences Section**: List of toggleable preferences.
    - **Offline Features Section**: List of offline tools.
    - **Emergency Targets Section**: List of active response agencies.
    - **About Section**: App info and legal links.
    - **Logout Button**: Red outlined button at the bottom.
- Use `PaletteSurfaceDark` and `PaletteSurfaceVariantDark` for cards and borders to match the dark aesthetic.

## Verification Plan

### Automated Tests
- N/A (UI layout changes are best verified visually).

### Manual Verification
- Run the app and navigate to the Profile screen.
- Verify the layout matches the mockup image in terms of spacing, icons, and sections.
- Ensure the screen is fully scrollable.
- Verify the Logout button is visible and functional.
