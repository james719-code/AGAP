# AGAP Android design-system rules

## Brand identity

- `AgapBrand` is the shared brand lockup for app headers and account screens.
  Use the cropped official `agap_logo_name` wordmark. The older square
  `logo_name_nobg` image includes large transparent margins and must not be used
  as an app-bar logo. The splash uses the same cropped asset.
- Terracotta is the action color. Use `MaterialTheme.colorScheme.primary` and
  `onPrimary` together for buttons, selection, and navigation. Do not introduce
  a separate accent for each screen or use gradients and colored outer glows.
- Teal communicates relay/information, amber communicates waiting/warnings,
  and green communicates readiness/completion. Use `AgapStatus.info`,
  `.warning`, and `.success` for status text; these follow AGAP's selected
  theme. Emergency category colors remain defined by `EmergencyType`.
- Light surfaces use warm charcoal text on a warm canvas with white cards.
  Dark surfaces use the corresponding charcoal palette. All Material container,
  inverse, and outline colors are explicitly defined in `AGAPTheme`.
- Standard content cards use `AgapShape.lg`, `outlineVariant` borders, and
  `surface` backgrounds. Use the larger shapes for dialogs and sheets.
- Use sentence case for navigation, headings, badges, and actions. Use shared
  typography; secondary labels should be at least 11 sp. Do not compress profile
  information into tiny dashboard labels. Contact actions wrap and readiness
  metrics use two columns so larger text has room.
- SOS remains a protected drag-and-release interaction. Its category colors
  convey emergency type; its foreground adapts to the selected color's
  luminance. The bottom-bar SOS destination only opens the emergency screen.

## Emergency types

Incident type labels, colors, and icons must come from `ui.theme.EmergencyType`.
Do not add per-screen `when` mappings or inline incident-type colors. Agency
directory types are a separate taxonomy and may use their own mapping.

## Layout and surfaces

- Use `AgapSpacing` for layout gaps and padding.
- Use `AgapShape` for corners.
- Use `Modifier.glassCard()` for the standard elevated-card treatment instead
  of recreating its background, border, and clipping behavior.

## Icons

Use `Icons.Outlined` by default. Filled icons are reserved for a deliberate
active or selected state, such as the active bottom-navigation item.

## Review checklist

For every new or substantially changed screen, confirm that it reuses:

1. `EmergencyType` for incident presentation;
2. `AgapSpacing`, `AgapShape`, and `glassCard()` for common layout/surfaces;
3. outlined icons unless the element is actively selected.

## Automated guard

`./gradlew :app:verifyDesignSystem` runs as part of `:app:check`. It rejects
direct ARGB literals in screen source and raw spacing/shape values that have
an `AgapSpacing` or `AgapShape` equivalent. The small legacy baseline lives in
`app/build.gradle.kts`; keep it empty whenever possible and document any
exception before adding one.

## Native UI conventions

- Use the warm canvas and white cards in light mode; use the matching dark
  surfaces through `MaterialTheme.colorScheme`. Container, outline, and error
  colors belong to the theme, not individual screens.
- Typography uses Android's built-in sans-serif family so first launch and
  offline use do not depend on a font download. Prefer the shared text styles.
- Keep home status labels readable, put the emergency selector first, and use
  sentence case for navigation and primary actions.
- Connection settings use a dismissible Material bottom sheet. Detailed relay
  information stays inside that sheet.
- Account forms are scrollable, keyboard-aware, and capped at 440 dp. Home
  content is capped at 600 dp for larger displays.
- The bottom SOS button only navigates. Tapping an emergency category only
  selects it. Dispatch still requires dragging the center control beyond the
  dispatch threshold and releasing.

### UI verification

`HomeUiTest` renders the production home components with sample data and no
network callbacks. It checks light mode, dark mode, and 150% text scaling,
including report navigation, connection settings, and non-dispatching taps.
Run it with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.travelingsalesman.agap.ui.HomeUiTest'
```

The tests save screenshots in the debug app's external files directory under
`ui-review` while installed. Gradle may remove these when it uninstalls the
app after testing. These are component checks, not live emergency delivery
or physical Bluetooth validation.
