import { lazy, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useReducedMotion } from 'framer-motion';
import {
  ArrowRight,
  ArrowUpRight,
  BatteryWarning,
  Bluetooth,
  CheckCircle2,
  CloudLightning,
  Download,
  Languages,
  Lock,
  MapPinned,
  MessageSquareText,
  Radio,
  Share2,
  ShieldCheck,
  Siren,
  SignalZero,
  Smartphone,
  TowerControl,
  Users,
  Waypoints,
} from 'lucide-react';
import consolePreview from '@/assets/images/incident-register-preview.png';
import { AlertRouteDemo } from './AlertRouteDemo';
import { DownloadDialog } from './DownloadDialog';
import { HeroRelayScene } from './HeroRelayScene';
import { LandingFooter } from './LandingFooter';
import { LandingHeader } from './LandingHeader';

// Keep lazy import for chunk split contract in bundleLoading.test.mjs
const Beams = lazy(() => import('@/components/Beams/Beams'));
void Beams;

const CONDITIONS = [
  { icon: CloudLightning, label: 'Typhoon blackouts' },
  { icon: SignalZero, label: 'Cellular dead zones' },
  { icon: BatteryWarning, label: 'Zero prepaid load' },
  { icon: TowerControl, label: 'Damaged cell towers' },
  { icon: Users, label: 'Crowded evacuation centers' },
  { icon: MapPinned, label: 'Remote barangays' },
];

const STEPS = [
  {
    title: 'Drag SOS toward the help you need',
    detail: 'A protected drag-and-release trigger avoids pocket misfires. AGAP attaches your GPS fix and emergency type automatically.',
    icon: Siren,
  },
  {
    title: 'Nearby phones carry it forward',
    detail: 'With no signal, a compact 21-byte alert hops phone-to-phone over Bluetooth — up to 15 hops — silently and without exposing who you are.',
    icon: Waypoints,
  },
  {
    title: 'Responders see it, and reply',
    detail: 'The first phone with connectivity delivers it to the Command Center. Dispatchers assign a unit and send “Help is on the way” back down the mesh.',
    icon: CheckCircle2,
  },
];

const PACKET = [
  { label: 'HDR', bytes: 1, color: '#0f1b1f' },
  { label: 'META', bytes: 1, color: '#2a3a40' },
  { label: 'TOKEN', bytes: 6, color: '#176e7e' },
  { label: 'LAT', bytes: 4, color: '#c8321f' },
  { label: 'LON', bytes: 4, color: '#c8321f' },
  { label: 'ACC', bytes: 1, color: '#a86f00' },
  { label: 'TIME', bytes: 2, color: '#a86f00' },
  { label: 'CRC', bytes: 2, color: '#52636a' },
];

const CAPABILITIES = [
  { icon: Languages, title: 'Bikol, Tagalog & English', detail: 'Predefined messages resolve offline from a local catalog — no typing in a crisis.' },
  { icon: Share2, title: 'Share the app offline', detail: 'Pass the installer phone-to-phone when app stores are unreachable.' },
  { icon: Lock, title: 'Private by design', detail: 'Relays only see a temporary token. Personal records stay with authorized coordinators.' },
  { icon: Radio, title: 'Stale alerts expire', detail: '2-hour SOS lifetime, duplicate filtering, and resolution “tombstones” keep the mesh clean.' },
];

const STANDARDS = [
  ['Mobile platform', 'Android 9.0+'],
  ['Wireless', 'Bluetooth LE, no pairing'],
  ['Location', 'GPS, E7 coordinates'],
  ['Integrity', 'CRC-16 per packet'],
  ['Relay limit', '15 hops · 2 h TTL'],
  ['Access', 'Admin-verified accounts'],
];

const RESIDENT_FEATURES = [
  { icon: Siren, text: 'Drag-to-confirm SOS with five emergency types' },
  { icon: Bluetooth, text: 'Automatic offline relay over Bluetooth' },
  { icon: MessageSquareText, text: 'See responder status: received, en route, resolved' },
];

const TEAM = [
  { name: 'James Ryan Sentelices Gallego', role: 'Team Leader' },
  { name: 'Diomar Balasta Molina', role: 'Systems Researcher' },
  { name: 'Jay Virgil Alto Romero', role: 'Product Designer' },
  { name: 'Gil IV Miguel Salvador I. Cea', role: 'Data Analyst' },
];

function initials(name: string) {
  return name.split(' ').slice(0, 2).map((part) => part[0]).join('');
}

export function LandingPage() {
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const heroRef = useRef<HTMLElement>(null);
  const [heroVisible, setHeroVisible] = useState(true);
  const [documentVisible, setDocumentVisible] = useState(() => document.visibilityState === 'visible');
  const reduceMotion = useReducedMotion();
  const animateHero = !reduceMotion && heroVisible && documentVisible;
  const openDownload = () => setDownloadModalOpen(true);

  useEffect(() => {
    const hero = heroRef.current;
    if (!hero) return;
    const observer = new IntersectionObserver(([entry]) => setHeroVisible(entry.isIntersecting));
    observer.observe(hero);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const updateVisibility = () => setDocumentVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', updateVisibility);
    return () => document.removeEventListener('visibilitychange', updateVisibility);
  }, []);

  return (
    <div className="site min-h-dvh font-sans antialiased">
      <div className="bg-[#0b1114] text-[#e6edf0]">
        <p className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-x-2 gap-y-1 px-4 py-2 text-center text-xs sm:text-[0.8125rem]">
          <Siren className="size-4 shrink-0 text-[#ff7a63]" aria-hidden="true" />
          <span><strong className="font-semibold text-white">In immediate danger?</strong> Call <a href="tel:911" className="font-bold text-[#ffb4a6] underline underline-offset-2">911</a>. AGAP supports — it does not replace — official emergency hotlines.</span>
        </p>
      </div>
      <LandingHeader onDownload={openDownload} />

      <main id="main-content">
        {/* ── HERO ─────────────────────────────────────────── */}
        <section ref={heroRef} id="top" aria-labelledby="hero-title" className="site-hero scroll-mt-16">
          <div className="relative mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)] items-center gap-6 px-5 pb-10 pt-12 sm:px-8 sm:pt-16 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)] lg:gap-10 lg:pb-20 lg:pt-20">
            <div>
              <p className="site-eyebrow">Offline-first emergency alerts</p>
              <h1 id="hero-title" className="site-display mt-6 text-[clamp(2.75rem,6.4vw,5.5rem)]">
                No signal.<br />
                <span className="text-[#c8321f]">Still heard.</span>
              </h1>
              <p className="mt-6 max-w-[34rem] text-lg leading-relaxed text-[#3d4d53] sm:text-xl">
                When typhoons knock out the network, AGAP passes your SOS phone-to-phone until it reaches responders — then brings their reply back to you.
              </p>

              <div className="mt-9 flex flex-wrap gap-3 max-sm:[&>*]:w-full">
                <button type="button" onClick={openDownload} className="site-btn site-btn-primary">
                  <Download className="size-5" aria-hidden="true" /> Get AGAP for Android
                </button>
                <a href="#how-it-works" className="site-btn site-btn-ghost">
                  See how it works <ArrowRight className="size-4" aria-hidden="true" />
                </a>
              </div>

              <dl className="mt-12 grid max-w-lg grid-cols-3 gap-6 border-t border-[#0f1b1f14] pt-6">
                {[
                  ['15', 'phone-to-phone hops'],
                  ['21 B', 'per SOS packet'],
                  ['₱0', 'load required offline'],
                ].map(([value, label]) => (
                  <div key={label}>
                    <dt className="sr-only">{label}</dt>
                    <dd>
                      <span className="block font-mono text-[clamp(1.5rem,2.6vw,2rem)] font-bold tracking-[-0.04em]">{value}</span>
                      <span className="mt-1 block text-xs text-[#52636a] sm:text-sm">{label}</span>
                    </dd>
                  </div>
                ))}
              </dl>
            </div>

            <HeroRelayScene animate={animateHero} />
          </div>

          {/* Conditions ticker */}
          <div className={`site-ticker border-y border-[#0f1b1f12] bg-white/60 py-4 ${animateHero ? '' : 'site-ticker-paused'}`}>
            <p className="sr-only">Built for: {CONDITIONS.map(({ label }) => label).join(', ')}.</p>
            <ul className="site-ticker-track" aria-hidden="true">
              {[...CONDITIONS, ...CONDITIONS].map(({ icon: Icon, label }, index) => (
                <li key={`${label}-${index}`} className="flex shrink-0 items-center gap-2.5 text-sm font-semibold text-[#2a3a40]">
                  <Icon className="size-4 text-[#c8321f]" /> {label}
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ── HOW IT WORKS (dark) ─────────────────────────── */}
        <section id="how-it-works" aria-labelledby="how-title" className="site-dark scroll-mt-16 px-5 py-20 sm:px-8 sm:py-28">
          <div className="mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)] gap-12 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-16">
            <div>
              <p className="site-eyebrow site-eyebrow-light">How it works</p>
              <h2 id="how-title" className="site-display mt-5 text-[clamp(2.1rem,4.4vw,3.5rem)]">
                Three steps from SOS to rescue.
              </h2>
              <p className="mt-5 max-w-md text-base leading-relaxed text-[#9fb0b8]">
                When data or Wi-Fi is available, alerts go straight to dispatch. When it isn’t, AGAP switches to the offline relay — automatically.
              </p>

              <ol className="mt-10 space-y-2">
                {STEPS.map(({ title, detail, icon: Icon }, index) => (
                  <li key={title} className="grid grid-cols-[3rem_minmax(0,1fr)] gap-4 rounded-2xl p-4 transition-colors hover:bg-white/[0.04]">
                    <span className="relative grid size-12 place-items-center rounded-2xl bg-white/[0.06] text-[#ff8f7b] ring-1 ring-white/10">
                      <Icon className="size-5" aria-hidden="true" />
                      <span className="absolute -right-1.5 -top-1.5 grid size-5 place-items-center rounded-full bg-[#c8321f] font-mono text-[10px] font-bold text-white" aria-hidden="true">{index + 1}</span>
                    </span>
                    <div>
                      <h3 className="text-lg font-bold text-white">{title}</h3>
                      <p className="mt-1.5 text-sm leading-relaxed text-[#9fb0b8]">{detail}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </div>

            <div className="flex flex-col justify-center">
              <div className="rounded-[1.75rem] bg-gradient-to-b from-white/15 to-white/[0.03] p-2 shadow-[0_40px_80px_-30px_#000]">
                <AlertRouteDemo animate={!reduceMotion && documentVisible} />
              </div>
              <p className="mt-4 text-center text-xs text-[#9fb0b8]">Offline delivery depends on nearby AGAP devices and an available route to responders.</p>
            </div>
          </div>
        </section>

        {/* ── TWO AUDIENCES ──────────────────────────────── */}
        <section id="command-center" aria-labelledby="audiences-title" className="scroll-mt-16 px-5 py-20 sm:px-8 sm:py-28">
          <div className="mx-auto max-w-7xl">
            <div className="max-w-3xl">
              <p className="site-eyebrow">One system, two sides</p>
              <h2 id="audiences-title" className="site-display mt-5 text-[clamp(2.1rem,4.4vw,3.5rem)]">Built for the person in trouble — and the team coming to help.</h2>
            </div>

            <div className="mt-14 grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-2">
              <article className="site-card flex flex-col overflow-hidden p-7 sm:p-10">
                <span className="inline-flex w-fit items-center gap-2 rounded-full bg-[#fde7e3] px-3 py-1.5 text-xs font-bold text-[#a92a1a]"><Smartphone className="size-3.5" aria-hidden="true" /> For residents</span>
                <h3 className="mt-6 text-[1.75rem] font-extrabold leading-tight tracking-[-0.03em]">The AGAP Android app</h3>
                <p className="mt-3 max-w-md text-[#3d4d53]">One drag sends your location and what you need. Your phone also helps neighbors by relaying their alerts in the background.</p>
                <ul className="mt-7 space-y-3.5 text-[0.9375rem]">
                  {RESIDENT_FEATURES.map(({ icon: Icon, text }) => (
                    <li key={text} className="flex items-start gap-3">
                      <span className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full bg-[#0f1b1f] text-white"><Icon className="size-3.5" aria-hidden="true" /></span>
                      <span>{text}</span>
                    </li>
                  ))}
                </ul>
                <div className="mt-auto pt-9">
                  <button type="button" onClick={openDownload} className="site-btn site-btn-primary max-sm:w-full">
                    <Download className="size-5" aria-hidden="true" /> Download for Android
                  </button>
                </div>
              </article>

              <article className="flex flex-col overflow-hidden rounded-[1.5rem] bg-[#0b1114] text-white">
                <div className="p-7 sm:p-10">
                  <span className="inline-flex w-fit items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-bold text-[#9fe0ea]"><ShieldCheck className="size-3.5" aria-hidden="true" /> For DRRMO &amp; responders</span>
                  <h3 className="mt-6 text-[1.75rem] font-extrabold leading-tight tracking-[-0.03em]">The Command Center</h3>
                  <p className="mt-3 max-w-md text-[#9fb0b8]">A live incident queue, GIS map, and unit dispatch in one browser console — restricted to verified administrators.</p>
                  <div className="mt-7 flex flex-wrap gap-3 max-sm:[&>*]:w-full">
                    <Link to="/login" className="site-btn bg-white text-[#0b1114] hover:bg-[#e6edf0]">Sign in <ArrowRight className="size-4" aria-hidden="true" /></Link>
                    <Link to="/dashboard" className="site-btn site-btn-ghost-light">Open console <ArrowUpRight className="size-4" aria-hidden="true" /></Link>
                  </div>
                </div>
                <figure className="mt-auto pl-7 sm:pl-10">
                  <img
                    src={consolePreview}
                    alt="AGAP Command Center incident register showing simulated example records"
                    width={1440}
                    height={1000}
                    loading="lazy"
                    className="block w-full rounded-tl-2xl border-l border-t border-white/10"
                  />
                  <figcaption className="sr-only">Example data, not live emergency records.</figcaption>
                </figure>
              </article>
            </div>
          </div>
        </section>

        {/* ── CAPABILITIES / BENTO ───────────────────────── */}
        <section id="features" aria-labelledby="features-title" className="scroll-mt-16 border-t border-[#0f1b1f12] bg-white px-5 py-20 sm:px-8 sm:py-28">
          <div className="mx-auto max-w-7xl">
            <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
              <div className="max-w-2xl">
                <p className="site-eyebrow">Under the hood</p>
                <h2 id="features-title" className="site-display mt-5 text-[clamp(2.1rem,4.4vw,3.5rem)]">Engineered for the worst day.</h2>
              </div>
              <p className="max-w-md text-[#3d4d53]">Every byte is budgeted so an alert fits inside a single Bluetooth advertisement and survives many hops.</p>
            </div>

            <div className="mt-14 grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-3">
              <article className="site-card bg-[#f6f5f2] p-7 sm:p-9 lg:col-span-2">
                <div className="flex flex-wrap items-baseline justify-between gap-3">
                  <h3 className="text-xl font-bold">Anatomy of an SOS</h3>
                  <span className="font-mono text-sm font-semibold text-[#52636a]">21 bytes total</span>
                </div>
                <div className="mt-7 grid gap-1" style={{ gridTemplateColumns: 'repeat(21, minmax(0, 1fr))' }} aria-hidden="true">
                  {PACKET.map(({ label, bytes, color }) => (
                    <span key={label} className="packet-byte" style={{ gridColumn: `span ${bytes}`, background: color }}>
                      <span className={bytes < 4 ? 'max-sm:hidden' : ''}>{label}</span>
                    </span>
                  ))}
                </div>
                <dl className="mt-7 grid gap-x-8 gap-y-4 text-sm sm:grid-cols-3">
                  <div><dt className="font-semibold">Transport token</dt><dd className="mt-1 text-[#52636a]">6 bytes. Anonymous — relays never learn who sent it.</dd></div>
                  <div><dt className="font-semibold">Location</dt><dd className="mt-1 text-[#52636a]">Latitude &amp; longitude as signed E7 integers, plus accuracy.</dd></div>
                  <div><dt className="font-semibold">Integrity</dt><dd className="mt-1 text-[#52636a]">CRC-16 checksum rejects corrupted packets at every hop.</dd></div>
                </dl>
              </article>

              <article id="specs" className="scroll-mt-24 rounded-[1.5rem] bg-[#0f1b1f] p-7 text-white sm:p-9">
                <h3 className="text-xl font-bold">Deployment standards</h3>
                <dl className="mt-6 divide-y divide-white/10 text-sm">
                  {STANDARDS.map(([term, value]) => (
                    <div key={term} className="flex items-baseline justify-between gap-4 py-3">
                      <dt className="text-[#9fb0b8]">{term}</dt>
                      <dd className="text-right font-semibold">{value}</dd>
                    </div>
                  ))}
                </dl>
              </article>

              {CAPABILITIES.map(({ icon: Icon, title, detail }) => (
                <article key={title} className="site-card p-7">
                  <span className="grid size-11 place-items-center rounded-2xl bg-[#fde7e3] text-[#c8321f]"><Icon className="size-5" aria-hidden="true" /></span>
                  <h3 className="mt-5 text-lg font-bold">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-[#52636a]">{detail}</p>
                </article>
              ))}
              <article className="flex flex-col justify-between rounded-[1.5rem] bg-[#e4f2f4] p-7 lg:col-span-2">
                <div>
                  <h3 className="text-lg font-bold text-[#0f4752]">Two-way, not one-way</h3>
                  <p className="mt-2 max-w-xl text-sm leading-relaxed text-[#2c5a63]">Acknowledgements travel back across the same mesh as 13-byte status packets — so a resident in a dead zone still learns that help is coming.</p>
                </div>
                <ol className="mt-6 flex flex-wrap gap-2 text-xs font-semibold">
                  {['SOS received', 'Help en route', 'On scene', 'Resolved'].map((status, index) => (
                    <li key={status} className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1.5 text-[#0f4752]">
                      <span className="font-mono text-[10px] text-[#176e7e]">{index + 1}</span>{status}
                    </li>
                  ))}
                </ol>
              </article>
            </div>
          </div>
        </section>

        {/* ── TEAM ───────────────────────────────────────── */}
        <section id="team" aria-labelledby="team-title" className="scroll-mt-16 px-5 py-20 sm:px-8 sm:py-24">
          <div className="mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)] gap-10 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:gap-16">
            <div>
              <p className="site-eyebrow">The Fellowship</p>
              <h2 id="team-title" className="site-display mt-5 text-[clamp(1.9rem,3.6vw,2.75rem)]">Made in Bicol, for Bicol.</h2>
              <p className="mt-4 max-w-md text-[#3d4d53]">Team Traveling Salesman · Heroes of Innovation Challenge 2026, Ibalong Festival, Legazpi City.</p>
            </div>
            <ul className="grid gap-3 sm:grid-cols-2">
              {TEAM.map(({ name, role }) => (
                <li key={name} className="site-card flex items-center gap-4 p-4">
                  <span className="grid size-12 shrink-0 place-items-center rounded-full bg-[#0f1b1f] font-mono text-sm font-bold text-white" aria-hidden="true">{initials(name)}</span>
                  <div className="min-w-0">
                    <p className="font-bold leading-snug">{name}</p>
                    <p className="text-sm text-[#52636a]">{role}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ── FINAL CTA ──────────────────────────────────── */}
        <section aria-labelledby="cta-title" className="px-5 pb-20 sm:px-8 sm:pb-24">
          <div className="relative mx-auto max-w-7xl overflow-hidden rounded-[2rem] bg-[#c8321f] px-7 py-14 text-white sm:px-14 sm:py-20">
            <div className="pointer-events-none absolute -right-24 -top-24 size-[28rem] rounded-full border-[40px] border-white/10" aria-hidden="true" />
            <div className="pointer-events-none absolute -right-4 top-12 size-[16rem] rounded-full border-[24px] border-white/10" aria-hidden="true" />
            <div className="relative max-w-2xl">
              <h2 id="cta-title" className="site-display text-[clamp(2.1rem,4.6vw,3.75rem)]">Install it before the storm.</h2>
              <p className="mt-5 max-w-lg text-lg text-white/85">Every phone running AGAP makes the network stronger for everyone nearby.</p>
              <div className="mt-9 flex flex-wrap gap-3 max-sm:[&>*]:w-full">
                <button type="button" onClick={openDownload} className="site-btn bg-white text-[#a92a1a] hover:bg-[#fde7e3]">
                  <Download className="size-5" aria-hidden="true" /> Get AGAP for Android
                </button>
                <Link to="/login" className="site-btn site-btn-ghost-light">Responder sign in</Link>
              </div>
            </div>
          </div>
        </section>
      </main>

      <LandingFooter />
      <DownloadDialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen} />
    </div>
  );
}
