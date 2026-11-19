import {execFileSync} from 'node:child_process';
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const scratch = join(root, 'out', 'audio-work');
const sampleRate = 48000;
const duration = 60;
const cues = [
  [0.65, 3.65, 'During a typhoon, mobile data can go down.'],
  [4.0, 7.7, 'An emergency still needs a way to reach help.'],
  [8.45, 13.35, 'With AGAP, a resident can send an SOS for themselves or someone nearby.'],
  [13.65, 17.7, 'The alert includes the emergency type and location when available.'],
  [18.55, 21.95, 'With a connection, the alert goes online.'],
  [22.2, 26.65, 'When data drops, nearby participating phones can relay it over Bluetooth.'],
  [26.85, 31.7, 'It can reach a responder or a phone that can get it online.'],
  [32.55, 36.8, 'Responders can see where help is needed and coordinate who to send.'],
  [37.15, 41.7, 'Status updates can let residents know help is on the way.'],
  [42.4, 45.15, 'Residents get another way to ask for help.'],
  [45.55, 48.1, 'Neighbors can relay alerts in the background.'],
  [48.35, 51.8, 'Response teams get a clearer picture of where to act.'],
  [52.55, 55.2, 'Get set up before an emergency.'],
  [55.65, 59.6, 'AGAP. Built by the Fellowship for the Heroes of Innovation Challenge.'],
];

mkdirSync(scratch, {recursive: true});
const ffmpeg = (args, options = {}) => execFileSync('ffmpeg', [
  '-nostdin', '-y', '-hide_banner', '-loglevel', 'error', ...args,
], {cwd: scratch, ...options});

const narration = Buffer.alloc(sampleRate * duration * 2);
const captions = cues.map(([start, deadline, text], index) => {
  const line = `line-${index}`;
  writeFileSync(join(scratch, `${line}.txt`), text.replaceAll('AGAP', 'Ah gap').replaceAll('SOS', 'S O S'));
  ffmpeg(['-f', 'lavfi', '-i', `flite=textfile=${line}.txt:voice=slt`, '-ar', String(sampleRate), '-f', 's16le', `${line}.raw`]);
  const original = readFileSync(join(scratch, `${line}.raw`));
  const sourceSeconds = original.length / (sampleRate * 2);
  const tempo = Math.max(1.03, sourceSeconds / (deadline - start - 0.1));

  const pcm = ffmpeg(['-f', 's16le', '-ar', String(sampleRate), '-ac', '1', '-i', `${line}.raw`,
    '-af', `atempo=${tempo},highpass=f=85,lowpass=f=6800,acompressor=threshold=-20dB:ratio=2:makeup=1.5`,
    '-f', 's16le', '-'], {maxBuffer: 4 * 1024 * 1024});
  const silentTail = Math.min(0.18, (deadline - start) * 0.1);
  const end = Math.min(deadline, start + pcm.length / (sampleRate * 2) + silentTail);
  if (pcm.length / (sampleRate * 2) > deadline - start) {
    throw new Error(`Narration cue ${index + 1} is longer than its allotted time`);
  }

  pcm.copy(narration, Math.round(start * sampleRate) * 2);
  return {start, end, text};
});

writeFileSync(join(scratch, 'narration.raw'), narration);
ffmpeg(['-f', 's16le', '-ar', String(sampleRate), '-ac', '1', '-i', 'narration.raw',
  '-af', 'loudnorm=I=-16:TP=-1.5:LRA=7', '-ar', String(sampleRate), '-b:a', '96k', join(root, 'public', 'narration.mp3')]);
writeFileSync(join(root, 'src', 'captions.json'), `${JSON.stringify(captions, null, 2)}\n`);

// A soft original score built from slowly changing chords and a light plucked pulse.
const chords = [[50, 57, 60, 64], [46, 53, 57, 60], [53, 60, 64, 67], [48, 55, 62, 64]];
const sound = Buffer.alloc(sampleRate * duration * 4);
const beat = 0.78;
const bar = beat * 4;
const chordFrequencies = chords.map((chord) => chord.map((note) => 440 * 2 ** ((note - 69) / 12)));
const tau = Math.PI * 2;

for (let sample = 0; sample < sampleRate * duration; sample++) {
  const time = sample / sampleRate;
  const barIndex = Math.floor(time / bar);
  const barTime = time - barIndex * bar;
  const current = chordFrequencies[barIndex % chords.length];
  const previous = chordFrequencies[(barIndex + chords.length - 1) % chords.length];
  const blend = Math.min(1, barTime / 0.92);
  const beatIndex = Math.floor(barTime / beat);
  const pulseTime = barTime - beatIndex * beat;
  const tone = current[[0, 2, 1, 3][beatIndex]] * 2;
  const pulse = Math.sin(tau * tone * pulseTime) * Math.min(1, pulseTime / 0.03) * Math.exp(-pulseTime * 8);
  const level = Math.min(1, time / 1.25, (duration - time) / 2.15);

  for (let channel = 0; channel < 2; channel++) {
    let chord = 0;
    for (let note = 0; note < current.length; note++) {
      const drift = (channel === 0 ? -1 : 1) * (note + 1) * 0.07;
      chord += Math.sin(tau * (current[note] + drift) * time) * blend;
      chord += Math.sin(tau * (previous[note] + drift) * time) * (1 - blend);
    }
    const value = (chord * 0.014 + pulse * (time < 18 ? 0.017 : 0.027)) * level;
    sound.writeInt16LE(Math.round(value * 32767), (sample * 2 + channel) * 2);
  }
}

writeFileSync(join(scratch, 'score.raw'), sound);
ffmpeg(['-f', 's16le', '-ar', String(sampleRate), '-ac', '2', '-i', 'score.raw', '-b:a', '96k', join(root, 'public', 'score.mp3')]);
