import React, { useState, useRef, useCallback, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion, useScroll, useTransform } from 'framer-motion';
import {
  Radio,
  MapPin,
  Smartphone,
  Lock,
  Share2,
  Zap,
  Download,
  ChevronRight,
  ShieldCheck,
  HeartHandshake,
  Users,
  AlertTriangle,
  SignalHigh,
  Waypoints,
  CheckCircle2,
  GitBranch,
  Quote
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import iphoneImg from '@/assets/images/iphone.png';
import laptopImg from '@/assets/images/laptop-screen-cut.png';
import ShapeGrid, { type ShapeGridHandle } from './ShapeGrid';
import { DownloadDialog } from './DownloadDialog';
import { LandingFooter } from './LandingFooter';
import { LandingHeader } from './LandingHeader';

// Scroll-reveal variants — a small, single, consistent motion vocabulary
// used everywhere on the page so movement reads as one system, not several.
const EASE: [number, number, number, number] = [0.22, 1, 0.36, 1];

const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: EASE } }
};

const staggerContainer = {
  hidden: {},
  show: {
    transition: { staggerChildren: 0.08 }
  }
};

/**
 * Palette (solid colors only — no gradients)
 * text        #0A0A0A   primary ink
 * background  #FFFFFF   dominant background
 * surface     #F7F5F2   alternating section background
 * line        #E7E3DC   hairline dividers / borders
 * primary     #D15033   primary action, key emphasis (terracotta)
 * secondary   #1F91A8   supporting accent, links, info states (teal)
 * accent      #F2A540   status / highlight accent (amber)
 * text-soft   #5C5A56   secondary text
 *
 * Shared design tokens (kept consistent across every section):
 * radius       card/section = rounded-sm (12px), controls = rounded-sm (8px)
 * card border  border border-[#E7E3DC] dark:border-[#26292C]
 * card shadow  shadow-[0_1px_2px_rgba(10,10,10,0.04)] at rest, shadow-lg on hover
 * section pad  py-20 lg:py-28, px-6 lg:px-12
 * eyebrow      text-xs font-semibold tracking-[0.14em] uppercase text-[#D15033]
 */

// Databuddy-style card: sharp rectangular edges, hairline border, minimal
// shadow at rest, a touch more lift on hover. rounded-[2px] reads as "sharp"
// without the harshness of true 0px corners.
const CARD =
  'rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)]';

const FEATURES = [
  {
    icon: Zap,
    tone: 'primary',
    title: 'One-tap SOS dispatch',
    description:
      'A single trigger sends a priority alert for medical, crime, collision, or disaster incidents, with location captured automatically.'
  },
  {
    icon: Radio,
    tone: 'secondary',
    title: 'Bluetooth mesh fallback',
    description:
      'When cellular service drops, nearby phones relay the alert over Bluetooth Low Energy until it reaches a connected device.'
  },
  {
    icon: MapPin,
    tone: 'accent',
    title: 'Structured location codes',
    description:
      'Every alert carries a region–province–barangay–zone code, so relay devices can route it toward the correct responder area.'
  },
  {
    icon: Share2,
    tone: 'secondary',
    title: 'Automatic cloud uplink',
    description:
      'The first relay device to reach cellular or Wi-Fi coverage forwards the stored alert to the dispatch center without user action.'
  },
  {
    icon: Lock,
    tone: 'primary',
    title: 'Encrypted in transit',
    description:
      'Location and contact details are encrypted at every relay hop and only readable by authorized dispatch personnel.'
  },
  {
    icon: Smartphone,
    tone: 'accent',
    title: 'Offline app sharing',
    description:
      'Citizens can pass the app itself phone-to-phone over Bluetooth or Wi-Fi Direct in areas without any internet access.'
  }
];

const PROCESS = [
  {
    number: '01',
    icon: SignalHigh,
    tone: 'primary',
    title: 'Alert sent, connection checked',
    description:
      'The app attempts a direct cellular send. If there is no signal, it automatically switches to local Bluetooth broadcasting.'
  },
  {
    number: '02',
    icon: Waypoints,
    tone: 'secondary',
    title: 'Nearby phones relay the alert',
    description:
      'Devices running AGAP within range receive the alert, store it locally, and rebroadcast it to nearby devices.'
  },
  {
    number: '03',
    icon: CheckCircle2,
    tone: 'accent',
    title: 'Alert reaches a responder',
    description:
      'The relay ends when a responder or the first phone with connectivity uploads the alert to the command center.'
  }
];

const TEAM = [
  {
    name: 'James Ryan Sentelices Gallego',
    role: 'Team Leader',
    focus: 'Software Development, AI/ML, UI/UX, Disaster Risk Reduction, Education, Community Development',
    quote: '“Placeholder quote about building technology that keeps communities connected when every second matters.”',
    github: '@james-placeholder'
  },
  {
    name: 'Diomar Balasta Molina',
    role: 'Team Member',
    focus: 'Software Development, Research',
    quote: '“Placeholder quote about turning careful research into practical tools for emergency response.”',
    github: '@diomar-placeholder'
  },
  {
    name: 'Jay Virgil Alto Romero',
    role: 'Team Member',
    focus: 'Software Development, UI/UX, Creative Industries',
    quote: '“Placeholder quote about making critical technology clear, calm, and easy to use.”',
    github: '@jay-placeholder'
  },
  {
    name: 'Gil IV Miguel Salvador I. Cea',
    role: 'Team Member',
    focus: 'Software Development, AI/ML, Data Analytics, Community Development, UI/UX',
    quote: '“Placeholder quote about using data and thoughtful design to strengthen community resilience.”',
    github: '@gil-placeholder'
  }
];

// Maps a semantic tone to concrete Tailwind classes built from the fixed brand palette.
// hoverWash / hoverBorder are the understated accent used only on :hover, per-card.
const TONE_STYLES: Record<
  string,
  { bg: string; text: string; darkBg: string; darkText: string; hoverWash: string; hoverBorder: string }
> = {
  primary: {
    bg: 'bg-[#D15033]/10',
    text: 'text-[#D15033]',
    darkBg: 'dark:bg-[#D15033]/15',
    darkText: 'dark:text-[#E9835F]',
    hoverWash: 'bg-[#D15033]/[0.04]',
    hoverBorder: 'hover:border-[#D15033]/40'
  },
  secondary: {
    bg: 'bg-[#1F91A8]/10',
    text: 'text-[#1F91A8]',
    darkBg: 'dark:bg-[#1F91A8]/15',
    darkText: 'dark:text-[#4FB4C9]',
    hoverWash: 'bg-[#1F91A8]/[0.04]',
    hoverBorder: 'hover:border-[#1F91A8]/40'
  },
  accent: {
    bg: 'bg-[#F2A540]/15',
    text: 'text-[#B5761C]',
    darkBg: 'dark:bg-[#F2A540]/15',
    darkText: 'dark:text-[#F2A540]',
    hoverWash: 'bg-[#F2A540]/[0.06]',
    hoverBorder: 'hover:border-[#F2A540]/50'
  }
};

const VALUE_PILLARS = [
  {
    icon: HeartHandshake,
    tone: 'primary',
    title: 'Protect communities',
    description:
      'Built for barangays where a delayed alert can cost lives — every second between the call and the response matters.'
  },
  {
    icon: Radio,
    tone: 'secondary',
    title: 'Assist responders',
    description:
      'Structured location data arrives pre-formatted, so dispatchers spend their time acting, not decoding.'
  },
  {
    icon: ShieldCheck,
    tone: 'accent',
    title: 'Stay reliable offline',
    description:
      'No signal is not the same as no way out. The mesh keeps working when the towers do not.'
  }
];

// ---------------------------------------------------------------------------
// Command Center bento grid — a preview of the real dispatch dashboard.
// Content data + small presentational helpers (header row, ring stat).
// ---------------------------------------------------------------------------

const RELAYS = [
  { id: 1, zone: 'Z3', barangay: 'Brgy. Bogtong', mode: 'Bluetooth · Android', time: 'Just now' },
  { id: 2, zone: 'Z1', barangay: 'Brgy. Rawis', mode: 'Cellular · iOS', time: 'Just now' },
  { id: 3, zone: 'Z2', barangay: 'Brgy. Cabagñan', mode: 'Bluetooth · Android', time: '1m ago' },
  { id: 4, zone: 'Z4', barangay: 'Brgy. Sabang', mode: 'Wi-Fi Direct', time: '2m ago' },
  { id: 5, zone: 'Z1', barangay: 'Brgy. Tula-tula', mode: 'Cellular · Android', time: '3m ago' }
];

const FAILURE_BARS = [30, 45, 20, 65, 50, 80, 35, 55, 25, 60, 40, 70];

const PROTOCOLS = [
  { name: 'Resolved', stage: 'Successfully closed', on: true },
  { name: 'Dispatched', stage: 'Units assigned', on: true },
  { name: 'En route', stage: 'Response underway', on: true }
];

const EVENTS = [
  { name: 'Active responders', count: '18' },
  { name: 'Total units', count: '24' },
  { name: 'Available now', count: '6' }
];

// Shared header row used at the top of every bento card: icon chip, title,
// optional inline badge (e.g. "Real-time"), and a one-line subtitle.
const DashCardHeader: React.FC<{
  icon: React.ElementType;
  title: string;
  subtitle: string;
  badge?: string;
}> = ({ icon: Icon, title, subtitle, badge }) => (
  <div className="flex items-start gap-3 min-w-0">
    <div className="h-9 w-9 rounded-sm bg-[#F7F5F2] dark:bg-[#181C1F] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-center shrink-0">
      <Icon className="h-4 w-4 text-[#6B6864] dark:text-[#9C9994]" />
    </div>
    <div className="min-w-0">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white">{title}</h3>
        {badge && (
          <span className="text-[10px] font-semibold tracking-[0.08em] uppercase text-[#1F91A8] bg-[#1F91A8]/10 dark:bg-[#1F91A8]/15 rounded-sm px-1.5 py-0.5 shrink-0">
            {badge}
          </span>
        )}
      </div>
      <p className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-0.5">{subtitle}</p>
    </div>
  </div>
);

// Small progress ring with the value centered inside it — used for compact
// multi-metric readouts (mirrors a "Web Vitals" style widget).
const RingStat: React.FC<{ value: number; label: string; sub: string; color: string }> = ({
  value,
  label,
  sub,
  color
}) => {
  const radius = 22;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (value / 100) * circumference;
  return (
    <div className="flex flex-col items-center gap-1.5">
      <div className="relative h-14 w-14">
        <svg viewBox="0 0 56 56" className="h-14 w-14 -rotate-90">
          <circle cx="28" cy="28" r={radius} fill="none" stroke="currentColor" className="text-[#E7E3DC] dark:text-[#26292C]" strokeWidth="4" />
          <circle
            cx="28"
            cy="28"
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth="4"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
          />
        </svg>
        <span className="absolute inset-0 flex items-center justify-center text-[11px] font-bold text-[#0A0A0A] dark:text-white">
          {sub}
        </span>
      </div>
      <span className="text-[9px] font-semibold tracking-[0.08em] uppercase text-[#6B6864] dark:text-[#9C9994]">
        {label}
      </span>
    </div>
  );
};

const SectionEyebrow: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span className="text-xs font-semibold tracking-[0.14em] text-[#D15033] uppercase">
    {children}
  </span>
);

export const LandingPage: React.FC = () => {
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);

  // Floating header: tracks scroll position so the header can tuck inward
  // and compact itself once the page leaves the top, then relax back to
  // its natural full-width state when scrolled back up.
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Gentle parallax on the hero phone image, tied to page scroll.
  const { scrollY } = useScroll();
  const heroImageY = useTransform(scrollY, [0, 600], [0, -40]);
  const heroImageOpacity = useTransform(scrollY, [0, 500], [1, 0.6]);

  // Drives the ShapeGrid hover trail from the whole hero section (not just
  // the canvas itself), since foreground content sits on top of the canvas
  // and would otherwise swallow the mousemove events.
  const shapeGridRef = useRef<ShapeGridHandle>(null);

  const handleHeroMouseMove = useCallback((e: React.MouseEvent<HTMLElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    shapeGridRef.current?.setMouse({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top
    });
  }, []);

  const handleHeroMouseLeave = useCallback(() => {
    shapeGridRef.current?.setMouse(null);
  }, []);

  return (
    <div className="dark min-h-screen bg-[#0F1214] text-[#F2F0EC] font-sans antialiased">
      <LandingHeader scrolled={scrolled} />
      {/* Hero Section — sits directly under the transparent header; its own
          top padding accounts for header height so the headline clears it. */}
      <section
        id="top"
        onMouseMove={handleHeroMouseMove}
        onMouseLeave={handleHeroMouseLeave}
        className="relative overflow-hidden px-6 lg:px-12 border-b border-[#E7E3DC] dark:border-[#26292C]"
      >
        {/* Quiet drifting net, sitting below the content but still legible */}
        <div className="absolute inset-0 opacity-[0.5] dark:opacity-[0.35]">
          <ShapeGrid
            ref={shapeGridRef}
            speed={0.3}
            size={44}
            direction="diagonal"
            shape="square"
            borderColor="#3A4145"
            hoverColor="rgba(209, 80, 51, 0.22)"
            hoverTrailAmount={2}
            lineWidth={1.25}
          />
        </div>

        {/* Fade the grid out toward the content edges so it reads as texture, not noise */}
        <div className="absolute inset-0 bg-gradient-to-b from-white via-white/40 to-white dark:from-[#0F1214] dark:via-[#0F1214]/40 dark:to-[#0F1214] pointer-events-none" />

        <div className="relative max-w-7xl mx-auto pt-24 pb-16 lg:pt-28 lg:pb-24 grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.9fr)] gap-16 items-center">
          <motion.div
            className="space-y-8 max-w-xl"
            initial="hidden"
            animate="show"
            variants={staggerContainer}
          >
            <motion.h1
              variants={fadeUp}
              className="text-4xl sm:text-5xl lg:text-[3.25rem] font-bold tracking-tight leading-[1.1] text-[#0A0A0A] dark:text-white"
            >
              Emergency alerts that reach responders, even offline.
            </motion.h1>

            <motion.p
              variants={fadeUp}
              className="text-base sm:text-lg text-[#4B4946] dark:text-[#B9B6B0] leading-relaxed"
            >
              AGAP sends emergency location alerts over mobile networks and falls back to a phone-to-phone
              Bluetooth relay when cellular service or internet is unavailable.
            </motion.p>

            <motion.div variants={fadeUp} className="flex flex-col sm:flex-row gap-3">
              <Button
                size="lg"
                onClick={() => setDownloadModalOpen(true)}
                className="h-12 px-6 text-sm rounded-sm bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center justify-center gap-2 transition-all duration-200 hover:shadow-[0_6px_18px_rgba(209,80,51,0.32)] hover:-translate-y-0.5 active:translate-y-0"
              >
                <Download className="h-4 w-4" />
                Download Android App
              </Button>

              <Link to="/dashboard" className="w-full sm:w-auto">
                <Button
                  size="lg"
                  variant="outline"
                  className="h-12 px-6 text-sm rounded-sm border-[#D8D4CB] dark:border-[#26292C] bg-white dark:bg-transparent text-[#0A0A0A] dark:text-[#F2F0EC] w-full flex items-center justify-center gap-1.5 font-semibold transition-all duration-200 hover:-translate-y-0.5 hover:border-[#0A0A0A]/30 dark:hover:border-white/30 active:translate-y-0"
                >
                  Launch Command Center
                  <ChevronRight className="h-4 w-4 text-[#6B6864] transition-transform duration-200 group-hover:translate-x-0.5" />
                </Button>
              </Link>
            </motion.div>

          </motion.div>

          {/* Signature element: iPhone showcase, with a gentle scroll parallax */}
          <motion.div className="w-full flex justify-center" style={{ y: heroImageY, opacity: heroImageOpacity }}>
            <motion.img
              src={iphoneImg}
              alt="AGAP emergency alert screens on a phone"
              className="w-full max-w-md lg:max-w-lg h-auto"
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, ease: EASE, delay: 0.15 }}
            />
          </motion.div>
        </div>

        {/* Admin dashboard preview — laptop bleeds off the bottom of the hero,
            cropped by the section's own overflow-hidden so the cut reads as
            an intentional "scroll to see more" cue, not a layout bug. */}
        <motion.div
          className="relative max-w-7xl mx-auto px-0 -mb-30 lg:-mb-42"
          initial={{ opacity: 0, y: 32 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, ease: EASE, delay: 0.25 }}
        >
          <img
            src={laptopImg}
            alt="AGAP admin dashboard shown on a laptop screen"
            className="w-full max-w-4xl mx-auto h-auto"
          />
        </motion.div>
      </section>

      {/* Command Center — bento-style dashboard preview. Always rendered in dark
          mode regardless of the site-wide theme toggle: the literal "dark" class
          here acts as the ancestor Tailwind's dark: variants key off of, so every
          dark: utility already used by the cards below applies unconditionally. */}
      <section className="dark bg-[#141719] border-b border-[#26292C]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-16 lg:py-20">
          <motion.div
            className="max-w-2xl mb-10"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-60px' }}
            variants={fadeUp}
          >
            <SectionEyebrow>Command Center</SectionEyebrow>
            <h2 className="text-3xl sm:text-[2.25rem] font-bold tracking-tight text-white mt-3">
              Everything responders see, live
            </h2>
            <p className="mt-3 text-[#B9B6B0] leading-relaxed">
              A preview of the dispatch dashboard — Live GIS Map, active relays, and mesh health, updated in real time.
            </p>
          </motion.div>

          <motion.div
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 lg:auto-rows-[minmax(210px,auto)] gap-5 items-stretch"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={staggerContainer}
          >
            {/* Live GIS map — mirrors the primary surface on DashboardPage */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[280px] lg:min-h-0 md:col-span-2 lg:col-span-2 lg:row-span-2 p-6 flex flex-col transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
              <DashCardHeader icon={MapPin} title="Live GIS Map" subtitle="Emergency incidents and relay vectors" badge="Real-time" />
              <div className="relative mt-5 flex-1 min-h-[300px] overflow-hidden rounded-[2px] border border-[#26292C] bg-[#101416] subtle-grid">
                <svg viewBox="0 0 300 250" className="absolute inset-0 h-full w-full" aria-hidden="true">
                  <path d="M26 202 C70 154, 94 181, 132 119 S218 93, 275 38" fill="none" stroke="#1F91A8" strokeWidth="1.5" strokeDasharray="5 5" opacity="0.65" />
                  <path d="M52 52 C102 88, 132 64, 174 111 S230 172, 268 196" fill="none" stroke="#D15033" strokeWidth="1.5" opacity="0.55" />
                </svg>
                {[
                  ['18%', '72%', '#D15033'],
                  ['42%', '37%', '#1F91A8'],
                  ['67%', '52%', '#F2A540'],
                  ['82%', '19%', '#D15033']
                ].map(([left, top, color], i) => (
                  <span key={i} className="absolute h-3 w-3 rounded-full border-2 border-[#141719] shadow-[0_0_0_4px_rgba(255,255,255,0.08)]" style={{ left, top, backgroundColor: color }} />
                ))}
                <div className="absolute bottom-4 left-4 right-4 flex items-center justify-between rounded-[2px] border border-[#26292C] bg-[#141719]/90 px-3 py-2 backdrop-blur-sm">
                  <span className="text-xs text-[#B9B6B0]">Spatial P2P mesh monitoring</span>
                  <span className="font-mono text-[10px] text-[#4FB4C9]">ONLINE · BLE_MESH</span>
                </div>
              </div>
            </motion.div>

            {/* Active SOS — mirrors the dashboard stat card */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[210px] lg:col-span-2 p-6 flex flex-col justify-center transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
                <DashCardHeader icon={AlertTriangle} title="Active Distress SOS" subtitle="Incidents requiring immediate triage" badge="Live" />
                <div className="mt-4 flex items-center gap-2">
                  <span className="relative flex h-2 w-2">
                    <span className="absolute inline-flex h-full w-full rounded-full bg-[#D15033] animate-ping opacity-60" />
                    <span className="relative inline-flex h-2 w-2 rounded-full bg-[#D15033]" />
                  </span>
                  <span className="text-[11px] font-semibold tracking-[0.12em] uppercase text-[#6B6864] dark:text-[#9C9994]">
                    Active now
                  </span>
                </div>
                <div className="text-3xl font-bold text-[#0A0A0A] dark:text-white mt-1">7</div>
                <svg viewBox="0 0 240 56" className="w-full h-14 mt-3 overflow-visible">
                  <motion.path
                    d="M0,38 C15,18 30,48 45,30 C60,12 75,42 90,28 C105,14 120,40 135,26 C150,12 165,36 180,24 C195,12 210,34 240,22"
                    fill="none"
                    stroke="#D15033"
                    strokeWidth="2"
                    strokeLinecap="round"
                    initial={{ pathLength: 0, opacity: 0 }}
                    whileInView={{ pathLength: 1, opacity: 1 }}
                    viewport={{ once: true }}
                    transition={{ duration: 1.1, ease: EASE }}
                  />
                </svg>
            </motion.div>

            {/* Telemetry insights — mirrors InsightsPage */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[210px] lg:col-span-2 p-6 flex flex-col justify-center transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
                <DashCardHeader icon={Waypoints} title="Telemetry Insights" subtitle="Server-calculated mesh performance" />
                <div className="mt-5 grid grid-cols-3 gap-3">
                  <RingStat value={92} label="Delivery" sub="92%" color="#D15033" />
                  <RingStat value={64} label="Avg hops" sub="1.8" color="#1F91A8" />
                  <RingStat value={78} label="Packets" sub="248" color="#F2A540" />
                </div>
            </motion.div>

            {/* Live telemetry feed — mirrors DashboardPage */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[280px] lg:min-h-0 md:col-span-2 lg:col-span-2 lg:row-span-2 p-6 flex flex-col transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
              <DashCardHeader icon={Radio} title="Mesh Telemetry Live Feed" subtitle="Recent incident and relay activity" badge="Live" />
              <div className="mt-4 flex flex-col divide-y divide-[#E7E3DC] dark:divide-[#26292C]">
                {RELAYS.map((r) => (
                  <div key={r.id} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="h-8 w-8 rounded-sm bg-[#F7F5F2] dark:bg-[#181C1F] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-center text-[10px] font-bold text-[#6B6864] dark:text-[#9C9994] shrink-0">
                        {r.zone}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-[#0A0A0A] dark:text-white truncate">{r.barangay}</p>
                        <p className="text-xs text-[#6B6864] dark:text-[#9C9994]">{r.mode}</p>
                      </div>
                    </div>
                    <span className="flex items-center gap-1.5 text-[11px] text-[#6B6864] dark:text-[#9C9994] shrink-0">
                      <span className="h-1.5 w-1.5 rounded-full bg-[#1F91A8]" />
                      {r.time}
                    </span>
                  </div>
                ))}
              </div>
              {/* Spacer pushes content up, leaving even breathing room at the bottom
                  once this card stretches to match the tallest card in the row */}
              <div className="flex-1" />
            </motion.div>

            {/* Dispatched units */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[210px] lg:col-span-2 p-6 flex flex-col transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
              <DashCardHeader icon={ShieldCheck} title="Dispatched Units" subtitle="Active responder unit assignments" />
              <div className="mt-4 flex items-start justify-between gap-3 rounded-sm border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1F] p-3">
                <div className="flex items-start gap-2.5 min-w-0">
                  <span className="h-7 w-7 rounded-sm bg-[#D15033]/10 dark:bg-[#D15033]/15 flex items-center justify-center shrink-0 mt-0.5">
                    <AlertTriangle className="h-3.5 w-3.5 text-[#D15033]" />
                  </span>
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-[#0A0A0A] dark:text-white truncate">12 responder units assigned</p>
                    <p className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-0.5">Assigned · accepted · en route · on scene</p>
                  </div>
                </div>
                <Badge className="rounded-sm bg-[#D15033] text-white border-0 text-[10px] font-semibold shrink-0">
                  ACTIVE
                </Badge>
              </div>
              <div className="flex items-center justify-between mt-4 mb-2">
                <span className="text-[11px] font-semibold tracking-[0.1em] uppercase text-[#6B6864] dark:text-[#9C9994]">
                  Deployment activity
                </span>
                <span className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">Live assignments</span>
              </div>
              <div className="flex items-end gap-1.5 h-16">
                {FAILURE_BARS.map((h, i) => (
                  <div key={i} className="flex-1 rounded-[1px] bg-[#D8D4CB] dark:bg-[#26292C]" style={{ height: `${h}%` }} />
                ))}
              </div>
            </motion.div>

            {/* Incident status */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[210px] lg:col-span-2 p-6 flex flex-col transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
              <DashCardHeader icon={CheckCircle2} title="Incident Status" subtitle="Operational status distribution" />
              <div className="mt-4 flex flex-col divide-y divide-[#E7E3DC] dark:divide-[#26292C]">
                {PROTOCOLS.map((p) => (
                  <div key={p.name} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                    <div>
                      <p className="text-sm font-medium text-[#0A0A0A] dark:text-white">{p.name}</p>
                      <p className="text-[10px] font-semibold tracking-[0.1em] uppercase text-[#6B6864] dark:text-[#9C9994] mt-0.5">
                        {p.stage}
                      </p>
                    </div>
                    <span
                      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors shrink-0 ${
                        p.on ? 'bg-[#D15033]' : 'bg-[#D8D4CB] dark:bg-[#26292C]'
                      }`}
                    >
                      <span
                        className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${
                          p.on ? 'translate-x-[18px]' : 'translate-x-1'
                        }`}
                      />
                    </span>
                  </div>
                ))}
              </div>
              {/* Spacer pushes content up, leaving even breathing room at the bottom
                  once this card stretches to match the tallest card in the row */}
              <div className="flex-1" />
            </motion.div>

            {/* Responder fleet */}
            <motion.div variants={fadeUp} className={`${CARD} h-full min-h-[210px] lg:col-span-2 p-6 flex flex-col transition-shadow duration-300 hover:shadow-[0_4px_16px_rgba(10,10,10,0.06)]`}>
              <DashCardHeader icon={Users} title="Active Responder Fleet" subtitle="On-duty units and fleet availability" />
              <div className="mt-4 flex flex-col divide-y divide-[#E7E3DC] dark:divide-[#26292C]">
                {EVENTS.map((e) => (
                  <div key={e.name} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                    <span className="text-sm font-medium text-[#0A0A0A] dark:text-white">{e.name}</span>
                    <span className="text-[11px] font-semibold text-[#6B6864] dark:text-[#9C9994] bg-[#F7F5F2] dark:bg-[#181C1F] border border-[#E7E3DC] dark:border-[#26292C] rounded-full px-2.5 py-1">
                      {e.count}
                    </span>
                  </div>
                ))}
              </div>
              {/* Spacer pushes content up, leaving even breathing room at the bottom
                  once this card stretches to match the tallest card in the row */}
              <div className="flex-1" />
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* Value pillars — short, purposeful statements on who this protects
          and why it matters, bridging the hero and the capability grid. */}
      <section className="bg-white dark:bg-[#0F1214] border-b border-[#E7E3DC] dark:border-[#26292C]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-14 lg:py-16">
          <motion.div
            className="grid grid-cols-1 sm:grid-cols-3 divide-y sm:divide-y-0 sm:divide-x divide-[#E7E3DC] dark:divide-[#26292C]"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={staggerContainer}
          >
            {VALUE_PILLARS.map((pillar) => {
              const Icon = pillar.icon;
              const tone = TONE_STYLES[pillar.tone];
              return (
                <motion.div key={pillar.title} variants={fadeUp} className="flex items-start gap-4 px-0 sm:px-8 py-6 sm:py-0 first:pl-0 first:pt-0 last:pr-0">
                  <div className={`h-10 w-10 rounded-sm ${tone.bg} ${tone.darkBg} flex items-center justify-center shrink-0`}>
                    <Icon className={`h-5 w-5 ${tone.text} ${tone.darkText}`} />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white">{pillar.title}</h3>
                    <p className="mt-1.5 text-sm text-[#5C5A56] dark:text-[#B9B6B0] leading-relaxed">
                      {pillar.description}
                    </p>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        </div>
      </section>

      {/* Capabilities — boxed, bordered card grid */}
      <section id="features" className="scroll-mt-24 bg-[#F7F5F2] dark:bg-[#141719]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-20 lg:py-28">
          <motion.div
            className="max-w-2xl mb-14"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={fadeUp}
          >
            <SectionEyebrow>Features</SectionEyebrow>
            <h2 className="text-3xl sm:text-[2.25rem] font-bold tracking-tight text-[#0A0A0A] dark:text-white mt-3">
              Built for typhoon blackouts and dead zones
            </h2>
            <p className="text-[#4B4946] dark:text-[#B9B6B0] mt-3 leading-relaxed">
              Designed for Legazpi City's terrain, recurring outages, and areas with limited prepaid load.
            </p>
          </motion.div>

          <motion.div
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 items-stretch"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={staggerContainer}
          >
            {FEATURES.map((feature, i) => {
              const Icon = feature.icon;
              const tone = TONE_STYLES[feature.tone];
              return (
                <motion.div
                  key={i}
                  variants={fadeUp}
                  className="group relative h-full rounded-[2px] bg-white dark:bg-[#161A1D] p-6 transition-all duration-300 ease-out hover:-translate-y-1 hover:shadow-lg"
                >
                  {/* Corner brackets instead of a full border */}
                  <span className={`pointer-events-none absolute -top-px -left-px h-2 w-2 border-t-2 border-l-2 border-[#D8D4CB] dark:border-[#3A4145] transition-colors duration-300 ${tone.hoverBorder}`} />
                  <span className={`pointer-events-none absolute -top-px -right-px h-2 w-2 border-t-2 border-r-2 border-[#D8D4CB] dark:border-[#3A4145] transition-colors duration-300 ${tone.hoverBorder}`} />
                  <span className={`pointer-events-none absolute -bottom-px -left-px h-2 w-2 border-b-2 border-l-2 border-[#D8D4CB] dark:border-[#3A4145] transition-colors duration-300 ${tone.hoverBorder}`} />
                  <span className={`pointer-events-none absolute -bottom-px -right-px h-2 w-2 border-b-2 border-r-2 border-[#D8D4CB] dark:border-[#3A4145] transition-colors duration-300 ${tone.hoverBorder}`} />

                  {/* Soft tone wash that fades in on hover, kept subtle behind the content */}
                  <div
                    className={`pointer-events-none absolute inset-0 rounded-sm opacity-0 transition-opacity duration-300 ease-out group-hover:opacity-100 ${tone.hoverWash}`}
                  />
                  <div className="relative">
                    <div
                      className={`h-11 w-11 rounded-sm ${tone.bg} ${tone.darkBg} flex items-center justify-center mb-4 transition-transform duration-300 ease-out group-hover:scale-105`}
                    >
                      <Icon className={`h-4 w-4 ${tone.text} ${tone.darkText}`} />
                    </div>
                    <h3 className="text-base font-semibold text-[#0A0A0A] dark:text-white mb-2">
                      {feature.title}
                    </h3>
                    <p className="text-sm text-[#4B4946] dark:text-[#B9B6B0] leading-relaxed">
                      {feature.description}
                    </p>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        </div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="scroll-mt-24 bg-white dark:bg-[#0F1214]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-20 lg:py-28">
          <motion.div
            className="max-w-2xl mb-14"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: "-80px" }}
            variants={fadeUp}
          >
            <SectionEyebrow>How It Works</SectionEyebrow>
            <h2 className="text-3xl sm:text-[2.25rem] font-bold tracking-tight text-[#0A0A0A] dark:text-white mt-3">
              How an alert gets delivered
            </h2>
            <p className="mt-3 text-[#4B4946] dark:text-[#B9B6B0] leading-relaxed">
              AGAP intelligently delivers emergency alerts through cellular networks
              and seamlessly switches to Bluetooth mesh networking whenever internet
              connectivity is unavailable. The relay keeps moving, hop by hop, until
              the alert reaches someone who can act on it.
            </p>
          </motion.div>

          <motion.div
            className="border-t border-[#E7E3DC] dark:border-[#26292C]"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={staggerContainer}
          >
            {PROCESS.map((step) => {
              const Icon = step.icon;
              const tone = TONE_STYLES[step.tone];
              return (
                <motion.div
                  key={step.number}
                  variants={fadeUp}
                  className="flex items-start gap-5 py-7 border-b border-[#E7E3DC] dark:border-[#26292C]"
                >
                  <span className="w-6 shrink-0 text-sm font-bold tracking-[0.06em] text-[#A5A19A] dark:text-[#4D5257] tabular-nums pt-2.5">
                    {step.number}
                  </span>
                  <div className={`h-11 w-11 rounded-sm ${tone.bg} ${tone.darkBg} flex items-center justify-center shrink-0`}>
                    <Icon className={`h-5 w-5 ${tone.text} ${tone.darkText}`} />
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-base font-bold leading-snug text-[#0A0A0A] dark:text-white">
                      {step.title}
                    </h3>
                    <p className="mt-1 text-sm leading-relaxed text-[#5C5A56] dark:text-[#B9B6B0] max-w-2xl">
                      {step.description}
                    </p>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        </div>
      </section>

{/* Team — testimonial-style member cards */}
      <section id="team" className="scroll-mt-24 bg-[#F7F5F2] dark:bg-[#141719]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-20 lg:py-28">
          <motion.div
            className="max-w-2xl mb-12"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={fadeUp}
          >
            <SectionEyebrow>Heroes of Innovation Challenge 2026</SectionEyebrow>
            <h2 className="text-3xl sm:text-[2.25rem] font-bold tracking-tight text-[#0A0A0A] dark:text-white mt-3">
              The team behind AGAP
            </h2>
            <p className="text-[#4B4946] dark:text-[#B9B6B0] mt-3 leading-relaxed">
              Team Fellowship (Traveling Salesman)
            </p>
          </motion.div>

          <motion.div
            className="grid grid-cols-1 sm:grid-cols-2 gap-5 items-stretch"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={staggerContainer}
          >
            {TEAM.map((member, i) => {
              const initials = member.name.split(' ').filter(Boolean);
              const monogram = `${initials[0]?.[0] ?? ''}${initials[1]?.[0] ?? ''}`;
              const tones = ['primary', 'secondary', 'accent', 'secondary'] as const;
              const tone = TONE_STYLES[tones[i % tones.length]];
              return (
                <motion.div
                  key={i}
                  variants={fadeUp}
                  className={`${CARD} group h-full min-h-[260px] hover:-translate-y-1 hover:shadow-lg transition-all duration-300 flex flex-col overflow-hidden`}
                >
                  <div className="flex flex-1 flex-col p-6 lg:p-7">
                    <Quote className={`h-5 w-5 ${tone.text} ${tone.darkText}`} aria-hidden="true" />
                    <blockquote className="mt-4 text-base text-[#343230] dark:text-[#D8D5D0] leading-relaxed max-w-xl">
                      {member.quote}
                    </blockquote>
                    <div className="mt-auto pt-6">
                      <p className="text-[10px] font-medium tracking-[0.12em] uppercase text-[#8A8680] dark:text-[#7F8589]">
                        Focus areas
                      </p>
                      <p className="mt-1.5 text-xs text-[#5C5A56] dark:text-[#9C9994] leading-relaxed">
                        {member.focus}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between gap-4 border-t border-[#E7E3DC] dark:border-[#26292C] px-6 py-4 lg:px-7">
                    <div className="flex min-w-0 items-center gap-3">
                      <div
                        className={`h-10 w-10 rounded-full ${tone.bg} ${tone.darkBg} flex items-center justify-center text-xs font-semibold ${tone.text} ${tone.darkText} shrink-0`}
                      >
                        {monogram}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-[#0A0A0A] dark:text-white leading-tight truncate">
                          {member.name}
                        </p>
                        <p className="mt-1 text-xs text-[#6B6864] dark:text-[#9C9994] truncate">{member.role}</p>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5 text-xs text-[#6B6864] dark:text-[#9C9994] transition-colors group-hover:text-[#1F91A8] dark:group-hover:text-[#4FB4C9]">
                      <GitBranch className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>{member.github}</span>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        </div>
      </section>

      {/* Footer */}
      <LandingFooter />

      {/* Download Modal */}
      <DownloadDialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen} />
    </div>
  );
};
