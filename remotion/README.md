# AGAP explainer

A 60-second, 1920 × 1080 Remotion video for residents, community members, and emergency response teams. The English voiceover is captioned in the video.

Run these commands from this folder to install dependencies and open the editable preview.

```sh
npm ci
npm run dev
```

Export the `AGAPExplainer` composition to `out/agap-explainer.mp4`.

```sh
npm run render
```

The six scenes and their timings are in `src/Explainer.tsx`. The Android screen, Command Center preview, illustration, and marks come from the AGAP repo. The relay animation is an illustration of how delivery can work when compatible participating devices are within Bluetooth range.

Both audio tracks are included. To regenerate the synthetic voiceover, its caption cues, and the original backing score after changing the script, run `node scripts/build-audio.mjs` with an FFmpeg build that includes `libflite` on your PATH.
