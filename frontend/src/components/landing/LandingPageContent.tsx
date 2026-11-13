import { lazy, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useReducedMotion } from 'framer-motion';
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  ChevronRight,
  Database,
  Download,
  FileText,
  Lock,
  MapPin,
  Radio,
  Share2,
  ShieldCheck,
  SignalHigh,
  Smartphone,
  Users,
  Waypoints,
  WifiOff,
} from 'lucide-react';
import consolePreview from '@/assets/images/dashboard-preview.png';
import { AlertRouteDemo } from './AlertRouteDemo';
import { DownloadDialog } from './DownloadDialog';
import { LandingFooter } from './LandingFooter';
import { LandingHeader } from './LandingHeader';

// Keep lazy import for chunk split contract in bundleLoading.test.mjs
const Beams = lazy(() => import('@/components/Beams/Beams'));
void Beams;

const SYSTEM_SPECIFICATIONS = [
  {
    category: 'Communication Coverage',
    title: 'Offline Wireless Mesh & Direct Signal',
    description:
      'Distress alerts broadcast wirelessly across nearby phones. Surrounding devices pass signals forward without requiring cellular signal, mobile data, or phone pairing.',
    icon: Radio,
    specs: ['Zero mobile data needed', 'Wi-Fi & Bluetooth direct', 'No pairing required'],
  },
  {
    category: 'Emergency Route Reliability',
    title: 'Reliable Store-and-Forward Mesh',
    description:
      'Automatic relay routing and duplication filters guide signals through storm-damaged areas across up to 15 nearby devices directly to emergency responders.',
    icon: Waypoints,
    specs: ['15-Device Relay Range', '2-Hour Signal Window', 'Smart Duplication Filter'],
  },
  {
    category: 'Citizen Privacy & Security',
    title: 'Protected Resident Information',
    description:
      'Distress alerts use secure temporary identifiers. Personal citizen records remain encrypted and protected, accessible only to authorized municipal rescue coordinators.',
    icon: Lock,
    specs: ['Encrypted Signals', 'Secure Backhaul', 'Private Citizen Data'],
  },
  {
    category: 'Incident Lifecycle Management',
    title: 'Automated Rescue Resolution Updates',
    description:
      'Once responders reach citizens and resolve an incident, an official resolution update is sent across the network to notify nearby devices and update status boards.',
    icon: Activity,
    specs: ['Responder Verification', 'Resolution Broadcast', 'Clean Signal State'],
  },
];

const PROTOCOL_TABLE = [
  { area: 'Mobile Platform', requirement: 'Android 9.0 or higher', detail: 'Compatible with standard citizen smartphones across all municipalities' },
  { area: 'Wireless Signal', requirement: 'Standard Bluetooth & Wi-Fi', detail: 'Passive discovery and alert relaying with minimal battery consumption' },
  { area: 'Offline Installer', requirement: 'Direct Phone-to-Phone Sharing', detail: 'Easily share the emergency app offline even when app stores are down' },
  { area: 'Alert Verification', requirement: 'Verified Message Delivery', detail: 'Automatic signal verification prevents error in severe weather conditions' },
  { area: 'Geographic Fix', requirement: 'Precise GPS Coordinates', detail: 'High-accuracy rescue location delivered with minimal signal overhead' },
  { area: 'Command Backhaul', requirement: 'Direct Dispatch Uplink', detail: 'Immediate arrival at dispatch triage as soon as any device reaches connectivity' },
];

const CAPABILITY_PILLARS = [
  {
    code: '01',
    title: 'Citizen distress shield',
    description: 'Emergency alert delivery during power blackouts and zero cell signal.',
    icon: Smartphone,
    items: [
      {
        icon: Smartphone,
        heading: 'Protected drag-to-confirm SOS',
        detail: 'Slide-and-release trigger eliminates accidental pocket misfires under stress.',
      },
      {
        icon: Database,
        heading: 'Predefined offline message catalog',
        detail: 'Select emergency needs in localized Bikol, Tagalog, or English without typing.',
      },
      {
        icon: Share2,
        heading: 'Peer-to-peer app sharing',
        detail: 'Share the AGAP application phone-to-phone even when internet is completely cut.',
      },
    ],
  },
  {
    code: '02',
    title: 'Offline store-and-forward mesh',
    description: 'Reliable emergency signal propagation across nearby citizen devices.',
    icon: Radio,
    items: [
      {
        icon: Radio,
        heading: 'Silent background relaying',
        detail: 'Nearby devices automatically pass distress alerts forward without exposing personal information.',
      },
      {
        icon: Waypoints,
        heading: 'Multi-device relay range',
        detail: 'Multi-hop forwarding with deduplication ensures alerts spread safely without network clutter.',
      },
      {
        icon: Activity,
        heading: 'Two-way rescue status confirmations',
        detail: 'Command Center sends verified return confirmations ("Help is on the way") back to the citizen.',
      },
    ],
  },
  {
    code: '03',
    title: 'Command Center & dispatch console',
    description: 'Complete situational awareness and multi-agency rescue coordination.',
    icon: ShieldCheck,
    items: [
      {
        icon: MapPin,
        heading: 'Interactive GIS tactical map',
        detail: 'Unified incident map showing verified citizen locations, urgency levels, and rescue teams.',
      },
      {
        icon: Users,
        heading: 'Emergency unit dispatch & tracking',
        detail: 'Coordinate ambulances, fire trucks, patrol cars, and rescue boats with proximity routing.',
      },
      {
        icon: Lock,
        heading: 'Strict administrative access',
        detail: 'Role-based security guarantees only authorized emergency personnel view incident reports.',
      },
      {
        icon: FileText,
        heading: 'Official dispatch log',
        detail: 'Every incoming alert, status change, and dispatch directive is securely logged.',
      },
    ],
  },
];

const DELIVERY_PHASES = [
  {
    number: '01',
    step: 'Phase 01',
    title: 'Emergency alert activation',
    detail: 'Citizen activates SOS. AGAP transmits via cellular or broadcasts a compact distress alert wirelessly if offline.',
    icon: SignalHigh,
  },
  {
    number: '02',
    step: 'Phase 02',
    title: 'Nearby resident relay',
    detail: 'Nearby phones receive the signal in the background and pass it forward until it reaches responders.',
    icon: Waypoints,
  },
  {
    number: '03',
    step: 'Phase 03',
    title: 'Command Center dispatch',
    detail: 'The alert arrives at the municipal Command Center, pinning the exact location and confirming rescue dispatch.',
    icon: CheckCircle2,
  },
];

const TEAM = [
  {
    name: 'James Ryan Sentelices Gallego',
    role: 'Team Leader',
    discipline: 'Software architecture, AI/ML, UI/UX, disaster risk reduction, and community development',
  },
  {
    name: 'Diomar Balasta Molina',
    role: 'Systems Researcher',
    discipline: 'Distributed systems, offline mesh protocol research, and backend infrastructure',
  },
  {
    name: 'Jay Virgil Alto Romero',
    role: 'Product Designer',
    discipline: 'Frontend engineering, accessibility, creative interface design, and mobile interaction',
  },
  {
    name: 'Gil IV Miguel Salvador I. Cea',
    role: 'Data Analyst',
    discipline: 'Emergency data analytics, database engineering, geospatial mapping, and community testing',
  },
];

export function LandingPage() {
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const heroRef = useRef<HTMLElement>(null);
  const [heroVisible, setHeroVisible] = useState(true);
  const [documentVisible, setDocumentVisible] = useState(() => document.visibilityState === 'visible');
  const reduceMotion = useReducedMotion();
  const animateHero = !reduceMotion && heroVisible && documentVisible;

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
    <div className="min-h-dvh bg-background font-sans text-foreground antialiased selection:bg-[var(--color-brand-terracotta-tint)] selection:text-foreground">
      <LandingHeader />

      <main id="main-content">
        {/* HERO SECTION - MODERN CLEAN TECH */}
        <section
          ref={heroRef}
          id="top"
          aria-labelledby="hero-title"
          className="relative isolate scroll-mt-16 overflow-hidden border-b border-border/80 bg-background"
        >
          <div className="mx-auto grid max-w-6xl items-center gap-10 px-5 py-12 sm:px-8 sm:py-16 md:grid-cols-[minmax(0,1.05fr)_minmax(0,1.15fr)] lg:gap-14 lg:py-20">
            <div>
              <h1
                id="hero-title"
                className="text-3xl font-extrabold sm:text-4xl lg:text-5xl text-foreground leading-[1.12] tracking-tight"
              >
                When networks fail, emergency alerts deliver.
              </h1>

              <p className="mt-4 max-w-xl text-sm leading-relaxed text-muted-foreground sm:text-base">
                AGAP relays critical distress signals across Camarines Sur over offline mesh.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3.5">
                <button
                  type="button"
                  className="inline-flex h-12 cursor-pointer items-center justify-center gap-2.5 rounded-xl border border-transparent bg-primary px-6 text-sm font-semibold text-primary-foreground shadow-sm transition-colors hover:bg-[var(--color-brand-terracotta-hover)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  onClick={() => setDownloadModalOpen(true)}
                >
                  <Download aria-hidden="true" className="size-4 shrink-0" />
                  <span>Download Android APK</span>
                </button>

                <a
                  href="#how-it-works"
                  className="inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-xl border border-border bg-card px-5 text-sm font-semibold text-foreground shadow-sm transition-colors hover:bg-muted"
                >
                  <span>Explore architecture</span>
                  <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
                </a>

                <Link
                  to="/login"
                  className="inline-flex h-12 items-center rounded-lg px-3 text-xs font-semibold text-muted-foreground transition-colors hover:text-foreground"
                >
                  Dispatcher sign in
                </Link>
              </div>

              {/* Modern Rounded Telemetry Metric Cards */}
              <div className="mt-10 grid grid-cols-3 gap-3 rounded-2xl border border-border/80 bg-card p-4 shadow-sm">
                <div className="border-r border-border/80 pr-2 sm:pr-3">
                  <span className="font-mono text-xl sm:text-2xl font-bold text-foreground">Offline</span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">Store-and-forward</span>
                </div>
                <div className="border-r border-border/80 pr-2 sm:pr-3">
                  <span className="font-mono text-xl sm:text-2xl font-bold text-foreground">0 cell</span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">Mesh coverage</span>
                </div>
                <div>
                  <span className="font-mono text-xl sm:text-2xl font-bold text-foreground">15 hops</span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">Relay reach</span>
                </div>
              </div>
            </div>

            <AlertRouteDemo animate={animateHero} />
          </div>
        </section>

        {/* DUAL-CHANNEL ARCHITECTURE / HOW IT WORKS */}
        <section
          id="how-it-works"
          aria-labelledby="architecture-title"
          className="scroll-mt-16 border-b border-border/80 bg-muted/30 px-5 py-16 sm:px-8 sm:py-24"
        >
          <div className="mx-auto max-w-6xl">
            <div className="max-w-2xl">
              <h2 id="architecture-title" className="text-2xl font-extrabold sm:text-3xl lg:text-4xl text-foreground tracking-tight">
                Dual-channel failover architecture
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
                Channels collapse during typhoons, remote transit, or mobile load depletion. AGAP operates across two continuous lanes to ensure every distress signal reaches first responders.
              </p>
            </div>

            {/* Architecture Channels Comparison */}
            <div className="mt-10 grid gap-6 md:grid-cols-2">
              <article className="rounded-2xl border border-border/80 bg-card p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between gap-3">
                  <div className="grid size-11 place-items-center rounded-xl bg-primary/10 text-primary border border-primary/20">
                    <SignalHigh className="size-5" aria-hidden="true" />
                  </div>
                  <span className="rounded-full border border-border/80 bg-muted px-3 py-1 text-xs font-semibold text-muted-foreground">
                    Primary channel
                  </span>
                </div>
                <h3 className="mt-5 text-xl font-bold text-foreground">Direct cellular connection</h3>
                <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">
                  When mobile data or Wi-Fi is available, alerts send directly to municipal emergency dispatch with sub-second arrival.
                </p>
                <div className="mt-6 flex flex-wrap items-center justify-between gap-2 border-t border-border/80 pt-4 text-xs font-medium text-muted-foreground">
                  <span>Speed: Sub-second</span>
                  <span>Security: Encrypted &amp; verified</span>
                </div>
              </article>

              <article className="rounded-2xl border border-primary/30 bg-card p-6 sm:p-8 shadow-md relative hover:shadow-lg transition-shadow">
                <div className="flex items-center justify-between gap-3">
                  <div className="grid size-11 place-items-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                    <WifiOff className="size-5" aria-hidden="true" />
                  </div>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 border border-primary/20 px-3 py-1 text-xs font-semibold text-primary">
                    <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" />
                    <span>Offline mesh relay</span>
                  </span>
                </div>
                <h3 className="mt-5 text-xl font-bold text-foreground">Offline phone-to-phone mesh</h3>
                <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">
                  The instant connection drops, AGAP broadcasts compact distress alerts wirelessly. Nearby phones automatically receive and forward the signal until it reaches emergency responders.
                </p>
                <div className="mt-6 flex flex-wrap items-center justify-between gap-2 border-t border-border/80 pt-4 text-xs font-medium text-muted-foreground">
                  <span>Range: 15 relay devices</span>
                  <span>Active window: 2 hours</span>
                </div>
              </article>
            </div>

            {/* Modern 3-Step Delivery Lifecycle */}
            <div className="mt-16">
              <h3 className="text-xl font-bold text-foreground">The delivery lifecycle</h3>
              <ol className="mt-6 grid gap-6 md:grid-cols-3">
                {DELIVERY_PHASES.map(({ number, step, title, detail, icon: Icon }) => (
                  <li key={title} className="rounded-2xl border border-border/80 bg-card p-6 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between">
                        <span className="grid size-10 place-items-center rounded-xl bg-primary/10 text-primary font-bold text-sm">
                          {number}
                        </span>
                        <div className="grid size-9 place-items-center rounded-lg bg-muted text-muted-foreground">
                          <Icon className="size-4 text-primary" aria-hidden="true" />
                        </div>
                      </div>
                      <span className="mt-4 block text-xs font-semibold text-primary">{step}</span>
                      <h4 className="mt-1 text-base font-bold text-foreground">{title}</h4>
                      <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{detail}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </section>

        {/* TECHNICAL SPECIFICATIONS & SYSTEM STANDARDS */}
        <section
          id="specs"
          aria-labelledby="specs-title"
          className="scroll-mt-16 border-b border-border/80 bg-background px-5 py-16 sm:px-8 sm:py-24"
        >
          <div className="mx-auto max-w-6xl">
            <div className="max-w-2xl">
              <h2 id="specs-title" className="text-2xl font-extrabold sm:text-3xl lg:text-4xl text-foreground tracking-tight">
                Emergency response &amp; system standards
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
                Engineered for disaster risk reduction and management, AGAP provides reliable communications during severe typhoons, grid collapses, and total telecom outages.
              </p>
            </div>

            {/* High-Level Architecture Cards */}
            <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {SYSTEM_SPECIFICATIONS.map((item) => (
                <div
                  key={item.title}
                  className="rounded-2xl border border-border/80 bg-card p-6 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <div className="grid size-11 place-items-center rounded-xl bg-primary/10 text-primary">
                        <item.icon className="size-5" aria-hidden="true" />
                      </div>
                    </div>
                    <span className="mt-4 block text-xs font-semibold text-secondary">{item.category}</span>
                    <h3 className="mt-1 text-base font-bold text-foreground">{item.title}</h3>
                    <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{item.description}</p>
                  </div>
                  <div className="mt-6 border-t border-border/80 pt-4 flex flex-wrap gap-1.5">
                    {item.specs.map((spec) => (
                      <span
                        key={spec}
                        className="rounded-full bg-muted px-2.5 py-0.5 text-[11px] font-medium text-foreground"
                      >
                        {spec}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>

            {/* System Requirements & Specifications Table */}
            <div className="mt-12 rounded-2xl border border-border/80 bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border/80 px-6 py-4 bg-muted/40">
                <h3 className="text-sm font-bold text-foreground">Operational deployment standards</h3>
                <p className="text-xs text-muted-foreground mt-0.5">Compatible across municipal emergency response units and citizen hardware</p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="border-b border-border/80 bg-muted/20 text-muted-foreground font-semibold">
                    <tr>
                      <th scope="col" className="px-6 py-3.5">Deployment area</th>
                      <th scope="col" className="px-6 py-3.5">Standard</th>
                      <th scope="col" className="px-6 py-3.5">Operational impact</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {PROTOCOL_TABLE.map((row) => (
                      <tr key={row.area} className="hover:bg-muted/30 transition-colors">
                        <td className="px-6 py-3.5 font-bold text-foreground whitespace-nowrap">{row.area}</td>
                        <td className="px-6 py-3.5 font-semibold text-primary whitespace-nowrap">{row.requirement}</td>
                        <td className="px-6 py-3.5 text-muted-foreground leading-relaxed">{row.detail}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </section>

        {/* SYSTEM CAPABILITIES / FEATURES */}
        <section
          id="features"
          aria-labelledby="features-title"
          className="scroll-mt-16 border-b border-border/80 bg-background px-5 py-16 sm:px-8 sm:py-24"
        >
          <div className="mx-auto max-w-6xl">
            <div className="max-w-2xl">
              <h2 id="features-title" className="text-2xl font-extrabold sm:text-3xl lg:text-4xl text-foreground tracking-tight">
                Engineered for citizens, mesh relays, and command dispatchers
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
                A unified ecosystem coordinating vulnerable citizens, passive community relay nodes, and active emergency rescue personnel.
              </p>
            </div>

            <div className="mt-12 grid gap-6 lg:grid-cols-3">
              {CAPABILITY_PILLARS.map((pillar) => (
                <article
                  key={pillar.title}
                  className="rounded-2xl border border-border/80 bg-card p-6 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <div className="grid size-12 place-items-center rounded-xl bg-primary/10 text-primary">
                        <pillar.icon className="size-5" aria-hidden="true" />
                      </div>
                      <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-bold text-muted-foreground">
                        {pillar.code}
                      </span>
                    </div>
                    <h3 className="mt-5 text-lg font-bold text-foreground">{pillar.title}</h3>
                    <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{pillar.description}</p>

                    <div className="mt-6 space-y-4 border-t border-border/80 pt-5">
                      {pillar.items.map((item) => (
                        <div key={item.heading} className="space-y-1">
                          <h4 className="flex items-center gap-2 text-xs font-semibold text-foreground">
                            <item.icon className="size-3.5 text-primary shrink-0" aria-hidden="true" />
                            {item.heading}
                          </h4>
                          <p className="text-xs text-muted-foreground leading-relaxed pl-5.5">
                            {item.detail}
                          </p>
                        </div>
                      ))}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* COMMAND CENTER CONSOLE PREVIEW */}
        <section
          id="command-center"
          aria-labelledby="console-title"
          className="scroll-mt-16 border-b border-border/80 bg-muted/30 px-5 py-16 sm:px-8 sm:py-24"
        >
          <div className="mx-auto grid max-w-6xl items-center gap-10 md:grid-cols-2 md:gap-14">
            <div>
              <h2 id="console-title" className="text-2xl font-extrabold sm:text-3xl lg:text-4xl text-foreground tracking-tight">
                Command Center web portal
              </h2>
              <p className="mt-4 text-sm leading-relaxed text-muted-foreground sm:text-base">
                Authorized municipal administrators and dispatchers monitor all emergency reports across the province in real time. Triage incidents with geographic accuracy, assign response units (ambulances, patrol cars, fire units, rescue boats), and broadcast rescue updates back to citizens.
              </p>

              <div className="mt-6 space-y-3">
                <div className="flex items-start gap-2.5 text-xs text-muted-foreground">
                  <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" aria-hidden="true" />
                  <span><strong>GIS Incident Map:</strong> Interactive map showing live clusters, severity ratings, and GPS precision.</span>
                </div>
                <div className="flex items-start gap-2.5 text-xs text-muted-foreground">
                  <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" aria-hidden="true" />
                  <span><strong>Rescue Unit Roster:</strong> Real-time status for medical, fire, maritime, and police responders.</span>
                </div>
                <div className="flex items-start gap-2.5 text-xs text-muted-foreground">
                  <CheckCircle2 className="size-4 text-primary shrink-0 mt-0.5" aria-hidden="true" />
                  <span><strong>Administrative Verification Gate:</strong> Strict role-based approval preventing unverified account access.</span>
                </div>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  to="/dashboard"
                  className="inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-xl bg-primary px-6 text-sm font-semibold text-primary-foreground shadow-sm transition-colors hover:bg-[var(--color-brand-terracotta-hover)]"
                >
                  <span>Launch Command Center</span>
                  <ArrowRight className="size-4" aria-hidden="true" />
                </Link>

                <Link
                  to="/login"
                  className="inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-xl border border-border bg-card px-5 text-sm font-semibold text-foreground shadow-sm transition-colors hover:bg-muted"
                >
                  <span>Dispatcher login</span>
                </Link>
              </div>
            </div>

            <figure className="rounded-2xl border border-border/80 bg-card p-4 sm:p-6 shadow-sm">
              <div className="w-full flex items-center justify-between pb-3 border-b border-border/80 text-xs text-muted-foreground mb-4">
                <div className="flex items-center gap-2">
                  <span className="size-2 rounded-full bg-primary" aria-hidden="true" />
                  <span className="font-semibold text-foreground">AGAP Dispatch Console · Camarines Sur</span>
                </div>
                <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary/10 px-2.5 py-0.5 text-[11px] font-semibold text-secondary">
                  <span className="size-1.5 rounded-full bg-secondary" aria-hidden="true" />
                  <span>System Online</span>
                </span>
              </div>

              <div className="overflow-hidden rounded-xl border border-slate-800/80 bg-slate-950 shadow-md">
                <img
                  src={consolePreview}
                  alt="The AGAP Command Center incident triage dashboard running on a console display"
                  width={1748}
                  height={1038}
                  className="w-full h-auto block object-contain"
                  loading="lazy"
                />
              </div>

              <figcaption className="mt-4 text-xs text-muted-foreground text-center">
                Live incident triage, active rescue unit assignment, and official municipal dispatch log.
              </figcaption>
            </figure>
          </div>
        </section>

        {/* TEAM & FELLOWSHIP */}
        <section
          id="team"
          aria-labelledby="team-title"
          className="scroll-mt-16 bg-background px-5 py-16 sm:px-8 sm:py-24"
        >
          <div className="mx-auto max-w-6xl">
            <div className="max-w-2xl">
              <h2 id="team-title" className="text-2xl font-extrabold sm:text-3xl lg:text-4xl text-foreground tracking-tight">
                The Fellowship (Traveling Salesman)
              </h2>
              <p className="mt-2 text-sm text-primary font-semibold">
                Heroes of Innovation Challenge 2026 · Ibalong Festival, Legazpi City
              </p>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
                AGAP is returned as the Hero’s Gift to the People of Ibalong and Camarines Sur, ensuring resilient, community-powered communication during natural disasters.
              </p>
            </div>

            <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {TEAM.map((member) => (
                <div
                  key={member.name}
                  className="rounded-2xl border border-border/80 bg-card p-5 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
                >
                  <div>
                    <div className="grid size-12 place-items-center rounded-xl bg-primary/10 text-primary font-bold text-sm border border-primary/20">
                      {member.name
                        .split(' ')
                        .slice(0, 2)
                        .map((part) => part[0])
                        .join('')}
                    </div>
                    <h3 className="mt-4 text-sm font-bold text-foreground leading-snug">{member.name}</h3>
                    <p className="mt-1 text-xs font-semibold text-primary">{member.role}</p>
                    <p className="mt-3 text-xs leading-relaxed text-muted-foreground">{member.discipline}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      </main>

      <LandingFooter />
      <DownloadDialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen} />
    </div>
  );
}
