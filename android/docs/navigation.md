# Android navigation and back behavior

## Rule for local sub-pages

When a composable uses local state to display a view that behaves like a
sub-page (for example, a detail panel or legal document), its top-app-bar
back action and Android system back must use the same handler.

- Enable `BackHandler` only while that local sub-page is visible.
- Clear the local state before allowing the navigation graph to pop the
  destination.
- Disable the screen-level handler while a dialog is open so Android back
  dismisses the dialog first.

## Report flow

Leaving an edited or in-progress report requires confirmation. Automatic GPS
updates do not mark a report as edited; only a location explicitly chosen by
the citizen does.

## Regression checks

Before shipping navigation changes, verify the in-app arrow, three-button
system back, and gesture back have the same result for:

- Report Incident and Offline Map Picker
- Profile legal/about pages
- System Management detail panels

Run `./gradlew :app:compileDebugKotlin` and
`./gradlew :app:testDebugUnitTest` after changes.
