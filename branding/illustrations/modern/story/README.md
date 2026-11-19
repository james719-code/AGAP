# AGAP pixel-anime story assets

Independent contemporary emergency-assistance characters and film environment art, generated with the built-in image generator. The sheets retain transparent alpha and are played as frame-by-frame animation in Remotion; they are not a set of still slides.

| File | Drawings | Purpose |
| --- | ---: | --- |
| `resident-run.png` | 8 | Contact, compression, passing and airborne frames for the resident's run |
| `resident-sos.png` | 8 | Stop, bend, kneel, reach, retrieve phone, tap, check and look for help |
| `volunteer-walk.png` | 8 | Walk cycle, pause and notice phone activity, continue walking |
| `responder-run.png` | 8 | Leftward run cycle carrying medical bag and radio |
| `patient.png` | 4 | Seated patient holds ankle, requests help, looks at responder, relieved expression |
| `responder-aid.png` | 4 | Arrive, kneel, open medical bag and reassure |
| `resident-reaction.png` | 4 | Chest-up expression animation: alert, surprise, worry and determination |
| `command-room.png` | 1 | Cinematic operations room with a blank monitor for real UI compositing |
| `phone-hand.png` | 1 | Transparent hand and phone close-up for the app-screen insert |
| `tap-hand.png` | 1 | Transparent pointing hand with terracotta cuff, aligned to the actual drag gesture |
| `response-street.png` | 1 | Alternate rainy neighborhood with an emergency-station facade for the responder's run |
| `resident-sos-eyeline.png` | 8 | Corrected downward/right gaze in the standing, bending, reassuring and waiting poses; original silhouette retained during playback |
| `resident-reaction-eyeline.png` | 4 | Corrected close-up gaze toward the seated patient |
| `patient-continuity-v2.png` | 4 | Registered seated poses with consistent character scale and planted-foot anchor; looks to the resident, then the responder |
| `tap-finger-closeup.png` | 1 | Life-size extended index finger and cropped forearm for the contact, drag and release animation |

The four 8-frame sheets have 4 columns × 2 rows; the three 4-frame sheets use 2 × 2. Individual frame bounds are alpha-checked in `remotion/src/Story.tsx`, and frames within a sheet share scale and ground alignment.

## Generation brief / prompts

All recurring characters are referenced from the original AGAP modern character atlas, with the same facial identity, clothing, skin tone, backpack/kit, hard pixel clusters and modern palette. No festival or folklore imagery, extra lettering, borders, or watermarks.

- Resident run: eight full-body rightward run frames (alternating contact, compression, passing and airborne poses), uniform transparent grid.
- Resident SOS: eight sequential full-body poses; stop concerned, bend, kneel, reach reassuringly, retrieve phone, look, tap, and look toward help.
- Volunteer: four walking phases followed by checking/raising a phone, noticing activity, and walking on, consistent transparent sprite cells.
- Responder run: eight separate leftward run frames, exact existing EMS identity, bag and radio; dynamic arm and leg motion.
- Patient: four sequential non-graphic seated poses of a modern adult in mustard jacket, holding an ankle then seeking help and becoming reassured.
- Responder aid: four consistent modern EMS poses, standing, lowering to one knee, opening medical bag, reassuring gesture. No detailed procedures.
- Command room: full-bleed modern pixel-anime operations room at dusk with unlit rectangular front-facing display and amber practical lighting.
- Phone: isolated transparent close-up of a terracotta-jacketed hand holding a front-facing vertical phone with a blank rectangular screen for compositing the real app.
- Reaction frames: four expressions in consistent chest-up framing, from alert to surprise to worried to determined.
- Tap hand: cropped transparent index finger aimed upper-left, modern terracotta sleeve for animating the phone gesture.
- Response street: a second rain-soaked city street in the same environment and palette, with a clear foreground walk area.

Continuity refinements: preserve the original resident identity, body, outfit and poses; correct only eyes/head tilt down-right toward the lower seated patient. Preserve the patient's seated dimensions and clothing, with the resting patient facing left toward the resident before turning right to the responder. Replace the undersized pointing-hand insert with a realistic extended index finger and connected cropped forearm, same skin tone and terracotta sleeve, that follows the actual SOS control.
