# AGAP PH — brand kit

Reusable independent AGAP project identity and visual assets for presentations, outreach, video, and product mockups. The production web and Android assets remain in their original locations; this kit gathers copies with descriptive filenames.

## Identity

AGAP — Accelerated Geolocation-based Assistance and Protection. The multicolor wordmark and shield are the primary marks. Keep their original colors and proportions; use the horizontal transparent wordmark on light surfaces. The shield works as the app icon or a compact emblem.

| Color | Hex | Role |
| --- | --- | --- |
| Terracotta | `#B93323` | Emergency, SOS, primary action |
| Teal | `#176E7E` | Connectivity, assistance, secondary action |
| Warm amber | `#F2A540` | Alert, attention, warmth |
| Slate | `#172C29` | Primary ink and dark backgrounds |
| Mineral paper | `#F5F6F3` | Light backgrounds |
| White | `#FFFFFF` | Elevated surfaces |
| Muted ink | `#546760` | Supporting text |
| Rule | `#DDE3DF` | Dividers and boundaries |

The original wordmark has its own multicolored artwork, including olive. Keep the logo intact rather than recoloring it with the interface palette. Color values follow `frontend/src/index.css` and Android's `ui/theme/Color.kt`.

The current website specifies Plus Jakarta Sans/Inter and Android uses native sans-serif. For offline video and media, the repository's bundled LT Superior family is provided under `fonts/`, with its OFL license.

## Asset library

| Folder | Contents |
| --- | --- |
| [logos](logos/) | Horizontal wordmark, shield, square logo variants and favicon |
| [Modern pixel art](illustrations/modern/) | Original character atlases; seven frame-by-frame story sprite sheets; city and command-room backgrounds; hand and phone inserts |
| [Legacy archive](illustrations/archive/) | Previous campaign artwork, retained for historical reference only; not part of the independent identity |
| [product](product/) | Citizen SOS screen and Command Center demo screenshots; legacy device mockups and blank frames |
| [fonts](fonts/) | LT Superior Regular, Medium, Semibold, Bold and license |
| [audio](audio/) | The current 120 BPM kinetic score and previous instrumental edition; no spoken audio |

### Illustration guidance

- Use the modern character atlases for resident, neighbor and responder concepts. For film-style motion, the `illustrations/modern/story/` sheets supply sequential run, walk, crouch, SOS, patient and aid poses with separate transparent frames.
- Use the storm-city environment as a generic modern neighborhood, with no festival iconography, folklore, landmarks or campaign affiliation.
- Preserve the pixel clusters and proportions. Do not stretch the art or treat the characters as product UI.
- The artwork is illustrative. Offline relaying depends on compatible, participating devices within range and a route to a responder.

### Motion

Use in-scene motion: animated characters, a moving SOS puck, visible packet handoffs, terracotta alert pulses, teal relay paths, amber highlights, kinetic typography, and brand-color wipes. Prioritize clear demonstrations and brief on-screen phrases, with instrumental music and no narration.

The latest product screenshots are development/demo captures from 2026-11-19; older mockups are labeled `legacy-`. The original campaign illustrations are retained in the archive from `frontend/src/assets/images/`, and logo variants from `frontend/src/assets/logos/`. The modern art was newly generated for this independent brand film.
