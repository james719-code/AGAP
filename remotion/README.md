# AGAP visual explainer

A 48-second, 1920 × 1080 music-only pixel-anime narrative for the independent AGAP identity. It follows a resident as she runs to help a person in a storm, creates a medical SOS, the alert relays through nearby participating phones, a Command Center receives it, and a responder runs to the scene. The characters are animated using multiple sequential drawn frames, with real app screens composited into the hand-held phone and the illustrated command-room monitor. The 120 BPM instrumental is arranged around the story's cuts.

Run these commands from this folder to install dependencies and open the editable preview.

```sh
npm ci
npm run dev
```

Export the `AGAPExplainer` composition to `out/agap-explainer.mp4`.

```sh
npm run render
```

The eight story scenes, sprite crop bounds, walk/run cycles, and acting cues are in `src/Story.tsx`, with styling in `src/story.css`. The [shared branding folder](../branding/) supplies the wordmark, shield, palette, fonts, original modern pixel artwork, demo screens and music. The relay and responder sequence is illustrative, not a guarantee of delivery or response.

To regenerate the instrumental score, run `node scripts/build-audio.mjs` with FFmpeg on your PATH. The story uses only `../branding/audio/agap-story-instrumental.mp3`.

The previous motion-graphics edition remains editable as the `AGAPMotionExplainer` composition; its audio can be regenerated with `node scripts/build-audio.mjs --kinetic`. Earlier rendered editions are retained as export backups. Legacy campaign artwork is not used by the current composition.
