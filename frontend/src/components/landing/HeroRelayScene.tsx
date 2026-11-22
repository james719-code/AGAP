import { BadgeCheck, Bluetooth, MapPin, Radio, Siren, Smartphone } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Illustrative hero scene: an SOS leaves a phone with no signal, hops across
 * nearby AGAP phones, and lands in the Command Center. Pure HTML/SVG/CSS so it
 * stays crisp, themeable, and pauses when off-screen or with reduced motion.
 */
const RELAY_PATH = 'M236 330 C 268 330, 270 252, 300 250 S 362 162, 392 160 S 424 128, 424 100';

export function HeroRelayScene({ animate }: { animate: boolean }) {
  return (
    <div className={cn('relay-scene', !animate && 'relay-scene-paused')} role="img" aria-label="Illustration: an SOS alert travels from a phone without signal through nearby phones to the Command Center.">
      <svg className="relay-scene-path" viewBox="0 0 520 560" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
        <path d={RELAY_PATH} className="relay-path-track" />
        <path d={RELAY_PATH} className="relay-path-live" />
        <circle r="7" className="relay-packet" style={{ offsetPath: `path('${RELAY_PATH}')` }} />
      </svg>

      {/* Phone */}
      <div className="relay-phone" aria-hidden="true">
        <div className="relay-phone-screen">
          <div className="flex items-center justify-between text-[9px] font-semibold text-[#52636a]">
            <span className="font-mono">21:04</span>
            <span className="inline-flex items-center gap-1 rounded-full bg-[#fde7e3] px-1.5 py-0.5 text-[#b93323]">No signal</span>
          </div>
          <p className="mt-4 text-[10px] font-bold uppercase tracking-[0.14em] text-[#b93323]">Emergency</p>
          <p className="mt-0.5 text-[15px] font-extrabold leading-tight text-[#0f1b1f]">Need help?</p>
          <div className="relay-sos">
            <span className="relay-sos-ring" />
            <span className="relay-sos-ring relay-sos-ring-2" />
            <span className="relay-sos-core">SOS</span>
          </div>
          <div className="grid grid-cols-3 gap-1.5 text-center text-[8px] font-semibold text-[#2a3a40]">
            {['Medical', 'Fire', 'Flood'].map((label) => (
              <span key={label} className="rounded-md bg-[#eef1f3] py-1.5">{label}</span>
            ))}
          </div>
          <div className="mt-2.5 flex items-center gap-1.5 rounded-lg bg-[#e4f2f4] px-2 py-1.5 text-[8.5px] font-semibold text-[#0f4752]">
            <Bluetooth className="size-3" /> Relaying via 3 nearby phones
          </div>
        </div>
      </div>

      {/* Relay nodes */}
      <span className="relay-node" style={{ left: '71.2%', top: '25%' }} aria-hidden="true"><Smartphone className="size-4" /></span>
      <span className="relay-node relay-node-delay" style={{ left: '53.8%', top: '41%' }} aria-hidden="true"><Smartphone className="size-4" /></span>

      {/* Dispatch card */}
      <div className="relay-card relay-card-dispatch" aria-hidden="true">
        <span className="grid size-9 place-items-center rounded-xl bg-[#b93323] text-white"><Siren className="size-4.5" /></span>
        <div className="min-w-0">
          <p className="text-[11px] font-bold text-[#0f1b1f]">SOS received · Medical</p>
          <p className="mt-0.5 flex items-center gap-1 text-[10px] text-[#52636a]"><MapPin className="size-3" /> Brgy. Bitano, Legazpi</p>
        </div>
      </div>

      <div className="relay-card relay-card-hop" aria-hidden="true">
        <Radio className="size-4 text-[#176e7e]" />
        <p className="font-mono text-[10.5px] font-semibold text-[#0f1b1f]">hop 3 / 15 · 21 bytes</p>
      </div>

      <div className="relay-card relay-card-ack" aria-hidden="true">
        <BadgeCheck className="size-4.5 text-[#2f7d4f]" />
        <p className="text-[11px] font-bold text-[#0f1b1f]">Help is on the way</p>
      </div>
    </div>
  );
}
