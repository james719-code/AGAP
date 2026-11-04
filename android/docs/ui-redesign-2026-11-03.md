# Android UI redesign — November 3, 2026

AGAP's citizen experience now uses a quieter warm canvas, clearer headings,
consistent surface boundaries, and terracotta actions. The home screen puts the
emergency interaction first, with compact connection and location indicators.

## Changes

- Home: open greeting, wrapping readiness indicators, a flat SOS card, short
  emergency labels, and an explicit drag-and-release instruction.
- Navigation: inset rounded surface with full tab touch areas. The center SOS
  destination opens home; dispatch remains a separate protected gesture.
- Sign-in: brand introduction, grouped account fields, visible password control,
  keyboard submission, and a separate guest action. Local fixture shortcuts
  remain available in the offline test variant.
- Map: readable status overlay, spacing for attribution, and map colors that
  follow the selected app theme.
- Alerts: simpler feed hierarchy and more room for advisories.
- Profile and guests: the same typography, spacing, and account presentation.
- Test support: the offline variant includes the Compose test host, allowing
  component checks to run without backend or Bluetooth permissions.

## Verification

Built the regular debug app, the isolated offline debug app, and its Android test
package. The design token guard passes.

Nine emulator checks cover light and dark home screens, 150% and 200% text,
report navigation, connection settings, category taps, the navigation SOS button,
early release, drag cancellation, one callback after a completed SOS gesture,
guest sign-in prompts, guest navigation, and theme selection.

The screens were also inspected in the Pixel 9 Pro XL emulator, including a
360 dp wide phone configuration. Screenshots and logs are saved in
[`artifacts/ui-redesign`](../../artifacts/ui-redesign/). Reviews use the separate
offline app and its local fixtures; the map in that variant has limited bundled
coverage. These checks verify the interface and callbacks, not live delivery.

Existing Bluetooth, database, web, and map-renderer edits in the workspace were
preserved.
