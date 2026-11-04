# AGAP Android UI review

Redesigned the home screen, protected SOS controls, sign-in, bottom navigation,
map overlay, alerts feed, profile overview, and guest presentation.

The regular and offline Android builds pass, along with the design-system guard
and all nine emulator interaction checks. These cover both themes, 150% and 200%
text, navigation, guest sign-in prompts, cancelled drags, and a single SOS
callback after a completed gesture. Review used local test fixtures.

| Screen | Preview |
| --- | --- |
| Home, light | [Open screenshot](home-light.png) |
| Home, dark | [Open screenshot](home-dark.png) |
| Sign in | [Open screenshot](login.png) |
| Map | [Open screenshot](map.png) |
| Community alerts | [Open screenshot](alerts.png) |
| Profile | [Open screenshot](profile.png) |
| Home with 200% text | [Open screenshot](component-checks/home-double-text.png) |

[Build log](build.txt) · [Emulator test results](ui-tests.txt) ·
[Change notes](../../android/docs/ui-redesign-2026-11-03.md)
