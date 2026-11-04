import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useReducedMotion } from 'framer-motion';
import {
  CheckCircle2,
  ChevronRight,
  Lock,
  MapPin,
  Radio,
  Share2,
  SignalHigh,
  Smartphone,
  Waypoints,
  Zap,
} from 'lucide-react';
import { Button, buttonVariants } from '@/components/ui/button';
import consolePreview from '@/assets/images/laptop-screen-cut.png';
import { AlertRouteDemo } from './AlertRouteDemo';
import { DownloadDialog } from './DownloadDialog';
import { LandingFooter } from './LandingFooter';
import { LandingHeader } from './LandingHeader';

const Beams = lazy(() => import('@/components/Beams/Beams'));

const FEATURES = [
  {
    icon: Zap,
    title: 'Protected SOS control',
    description: 'Drag and release to send a priority alert with your location.',
  },
  {
    icon: Radio,
    title: 'Bluetooth relay',
    description: 'Nearby AGAP phones can pass an alert along when mobile data drops.',
  },
  {
    icon: MapPin,
    title: 'Structured location',
    description: 'Location codes help route alerts toward the right responder area.',
  },
  {
    icon: Share2,
    title: 'Automatic forwarding',
    description: 'A connected relay device can forward a stored alert to dispatch.',
  },
  {
    icon: Lock,
    title: 'Dispatch access',
    description: 'Responders and dispatchers sign in to access incident details.',
  },
  {
    icon: Smartphone,
    title: 'Offline app sharing',
    description: 'Share the app with nearby phones over Bluetooth or Wi-Fi Direct.',
  },
];

const DELIVERY_STEPS = [
  {
    icon: SignalHigh,
    title: 'Send an alert',
    description: 'AGAP first tries to deliver the alert directly over mobile data.',
  },
  {
    icon: Waypoints,
    title: 'Relay nearby',
    description: 'Without a connection, nearby phones running AGAP can store and pass the alert along.',
  },
  {
    icon: CheckCircle2,
    title: 'Reach dispatch',
    description: 'A responder or a connected relay device forwards the alert to the Command Center.',
  },
];

const TEAM = [
  {
    name: 'James Ryan Sentelices Gallego',
    role: 'Team Leader',
    focus: 'Software development, AI/ML, UI/UX, disaster risk reduction, education, community development',
  },
  {
    name: 'Diomar Balasta Molina',
    role: 'Team Member',
    focus: 'Software development and research',
  },
  {
    name: 'Jay Virgil Alto Romero',
    role: 'Team Member',
    focus: 'Software development, UI/UX, and creative industries',
  },
  {
    name: 'Gil IV Miguel Salvador I. Cea',
    role: 'Team Member',
    focus: 'Software development, AI/ML, data analytics, community development, and UI/UX',
  },
];

const sectionLayout = 'mx-auto w-full max-w-6xl px-5 py-8 sm:px-8 sm:py-10';
const sectionHeading = 'text-2xl font-bold tracking-tight sm:text-3xl';

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
    <div className="dark min-h-dvh bg-background font-sans text-foreground antialiased">
      <LandingHeader />
      <main>
        <section
          ref={heroRef}
          id="top"
          aria-labelledby="landing-title"
          className="relative isolate scroll-mt-16 overflow-hidden border-b border-border"
        >
          {animateHero && (
            <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 hidden opacity-15 lg:block">
              <Suspense fallback={null}>
                <Beams beamWidth={1} beamHeight={30} beamNumber={10} lightColor="#E9835F" speed={3.5} noiseIntensity={1.75} scale={0.15} rotation={30} />
              </Suspense>
            </div>
          )}

          <div className="mx-auto grid max-w-6xl items-center gap-5 px-5 py-6 max-[360px]:gap-3 max-[360px]:py-3.5 sm:px-8 sm:py-9 md:grid-cols-[minmax(0,.88fr)_minmax(0,1.12fr)] md:gap-7 lg:gap-10">
            <div>
              <p className="inline-flex items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                <MapPin aria-hidden="true" className="size-3.5 text-primary" />
                Built for CamSur
              </p>
              <h1 id="landing-title" className="mt-3 text-[clamp(2.3rem,5.2vw,4.5rem)] leading-[.98] font-bold tracking-[-.05em] max-[360px]:text-[2.05rem]">
                When network fails,{' '}
                <span className="mt-2 block text-[clamp(1.55rem,3.3vw,3rem)] leading-[1.08] tracking-[-.045em] text-primary">
                  We. Legends. Prevail.
                </span>
              </h1>
              <p className="mt-4 max-w-[42ch] text-sm leading-relaxed text-muted-foreground sm:text-base">
                Share your location in an emergency across CamSur. If mobile data drops, nearby AGAP phones can relay your alert over Bluetooth.
              </p>
              <div className="mt-5 flex flex-wrap gap-2.5">
                <Button type="button" className="h-11 gap-2 font-semibold" onClick={() => setDownloadModalOpen(true)}>
                  <Smartphone aria-hidden="true" className="size-4" />
                  Android app
                </Button>
                <a href="#how-it-works" className={buttonVariants({ variant: 'ghost', className: 'h-11 gap-1.5 px-3 text-foreground' })}>
                  How it works
                  <ChevronRight aria-hidden="true" className="size-4" />
                </a>
              </div>
            </div>

            <AlertRouteDemo animate={animateHero} />
          </div>
        </section>

        <section id="features" className="light scroll-mt-16 border-b border-border bg-background text-foreground" aria-labelledby="features-title">
          <div className={sectionLayout + ' grid gap-4 lg:grid-cols-[1fr_2.2fr] lg:gap-8'}>
            <div>
              <p className="page-eyebrow">The essentials</p>
              <h2 id="features-title" className="mt-1 text-3xl font-bold tracking-tight sm:text-4xl">What AGAP does</h2>
              <p className="mt-2 max-w-[26ch] text-sm leading-relaxed text-muted-foreground">
                From the first alert to the moment it reaches dispatch.
              </p>
            </div>
            <ul className="grid gap-x-6 border-t border-border md:grid-cols-2">
              {FEATURES.map(({ icon: Icon, title, description }) => (
                <li key={title} className="flex min-w-0 items-start gap-3 border-b border-border py-3">
                  <span className="grid size-9 shrink-0 place-items-center rounded-md bg-secondary text-secondary-foreground">
                    <Icon aria-hidden="true" className="size-4" />
                  </span>
                  <div>
                    <h3 className="text-sm font-bold">{title}</h3>
                    <p className="mt-0.5 text-sm leading-snug text-muted-foreground">{description}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="how-it-works" className="scroll-mt-16 border-b border-border bg-muted" aria-labelledby="delivery-title">
          <div className={sectionLayout}>
            <p className="page-eyebrow">The delivery path</p>
            <h2 id="delivery-title" className={sectionHeading + ' mt-1'}>How an alert gets through</h2>
            <ol className="mt-5 grid gap-x-6 md:grid-cols-3">
              {DELIVERY_STEPS.map(({ icon: Icon, title, description }, index) => (
                <li key={title} className="flex items-start gap-3 border-t border-primary/30 py-3">
                  <span className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 font-mono text-xs font-semibold text-primary">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div>
                    <h3 className="flex items-center gap-1.5 text-sm font-bold">
                      {title}
                      <Icon aria-hidden="true" className="size-3.5 text-muted-foreground" />
                    </h3>
                    <p className="mt-1 text-sm leading-snug text-muted-foreground">{description}</p>
                  </div>
                </li>
              ))}
            </ol>
            <details className="mt-3 rounded-md border border-border bg-card px-3.5 py-2.5 text-sm">
              <summary className="cursor-pointer font-semibold text-foreground">Why a relay matters</summary>
              <p className="mt-2 max-w-[72ch] leading-relaxed text-muted-foreground">
                Weak signals and outages can interrupt delivery. Nearby devices provide another route, while a structured location helps dispatch identify where the alert came from.
              </p>
            </details>
          </div>
        </section>

        <section id="command-center" className="scroll-mt-16 border-b border-border bg-background" aria-labelledby="console-title">
          <div className={sectionLayout + ' grid items-center gap-5 md:grid-cols-2 md:gap-8'}>
            <div>
              <p className="page-eyebrow">For dispatchers</p>
              <h2 id="console-title" className={sectionHeading + ' mt-1'}>Coordinate response from one console</h2>
              <p className="mt-2 max-w-[46ch] text-sm leading-relaxed text-muted-foreground">
                View incidents on a live map, review relay activity, assign units, and track response times.
              </p>
              <Link to="/dashboard" className={buttonVariants({ variant: 'secondary', className: 'mt-4 gap-1.5' })}>
                Open Command Center
                <ChevronRight aria-hidden="true" className="size-4" />
              </Link>
            </div>
            <figure className="flex flex-col items-center gap-2 rounded-lg border border-border bg-card px-4 py-3">
              <img
                src={consolePreview}
                alt="The AGAP Command Center dashboard on a laptop"
                className="max-h-44 w-full object-contain sm:max-h-56"
                loading="lazy"
              />
              <figcaption className="text-xs text-muted-foreground">Command Center preview</figcaption>
            </figure>
          </div>
        </section>

        <section id="team" className="light scroll-mt-16 bg-background text-foreground" aria-labelledby="team-title">
          <div className={sectionLayout}>
            <p className="page-eyebrow">Heroes of Innovation Challenge 2026</p>
            <h2 id="team-title" className={sectionHeading + ' mt-1'}>The team behind AGAP</h2>
            <p className="mt-1 text-sm text-muted-foreground">Team Fellowship (Traveling Salesman)</p>
            <ul className="mt-4 grid gap-x-8 md:grid-cols-2">
              {TEAM.map((member) => (
                <li key={member.name} className="flex min-w-0 items-start gap-3 border-t border-border py-3">
                  <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-md bg-secondary text-xs font-bold text-secondary-foreground">
                    {member.name.split(' ').slice(0, 2).map((part) => part[0]).join('')}
                  </span>
                  <div>
                    <h3 className="text-sm font-bold">{member.name}</h3>
                    <p className="text-xs font-medium text-primary">{member.role}</p>
                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{member.focus}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>
      </main>
      <LandingFooter />
      <DownloadDialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen} />
    </div>
  );
}
