import React, { useState, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Radio,
  MapPin,
  Smartphone,
  Lock,
  Share2,
  Zap,
  ArrowRight,
  ArrowUpRight,
  Download,
  ChevronRight,
  Sun,
  Moon
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog } from '@/components/ui/dialog';
import { Tooltip } from '@/components/ui/tooltip';
import { useTheme } from '@/context/ThemeContext';
import agapLogo from '../assets/logos/logo-name-nobg.png';
import process1 from "../assets/images/1.png";
import process2 from "../assets/images/2.png";
import process3 from "../assets/images/3.png";
import iphoneImg from '@/assets/images/tatlo.png';
import ShapeGrid, { type ShapeGridHandle } from './ShapeGrid';

// Scroll-reveal variants — a small, single, consistent motion vocabulary
// used everywhere on the page so movement reads as one system, not several.
const EASE: [number, number, number, number] = [0.22, 1, 0.36, 1];

const fadeUp = {
  hidden: { opacity: 0, y: 18 },
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
 */

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
    number: "01",
    title: "Alert sent, connection checked",
    image: process1,
    description:
      "The app attempts a direct cellular send. If there is no signal, it automatically switches to local Bluetooth broadcasting."
  },
  {
    number: "02",
    title: "Nearby phones relay the alert",
    image: process2,
    description:
      "Devices running AGAP within range receive the alert, store it locally, and rebroadcast it to nearby devices."
  },
  {
    number: "03",
    title: "Alert reaches a responder",
    image: process3,
    description:
      "The relay ends when a responder or the first phone with connectivity uploads the alert to the command center."
  }
];

const TEAM = [
  {
    name: 'James Ryan Sentelices Gallego',
    role: 'Team Leader',
    focus: 'Software Development, AI/ML, UI/UX, Disaster Risk Reduction, Education, Community Development'
  },
  {
    name: 'Diomar Balasta Molina',
    role: 'Team Member',
    focus: 'Software Development, Research'
  },
  {
    name: 'Jay Virgil Alto Romero',
    role: 'Team Member',
    focus: 'Software Development, UI/UX, Creative Industries'
  },
  {
    name: 'Gil IV Miguel Salvador I. Cea',
    role: 'Team Member',
    focus: 'Software Development, AI/ML, Data Analytics, Community Development, UI/UX'
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

export const LandingPage: React.FC = () => {
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const { theme, toggleTheme } = useTheme();

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
    <div className="min-h-screen bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] font-sans">
      {/* Letterhead accent rule */}
      <div className="h-1 w-full bg-[#D15033]" />

      {/* Header / Navigation */}
<header className="sticky top-0 z-40 w-full bg-white/95 dark:bg-[#0F1214]/95 backdrop-blur border-b border-[#E7E3DC] dark:border-[#26292C] px-6 lg:px-12 h-16 flex items-center justify-between">
  <div className="flex items-center gap-3">
    <img
      src={agapLogo}
      alt="AGAP"
      className="h-20 sm:h-26 w-auto object-contain shrink-0"
    />
    <p className="text-[11px] sm:text-xs text-[#6B6864] dark:text-[#9C9994] hidden sm:block tracking-wide leading-none">
      Accelerated Geolocation Assistance &amp; Protection
    </p>
  </div>

  <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-[#3A3936] dark:text-[#CCC9C3]">
    <a href="#features" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">Capabilities</a>
    <a href="#how-it-works" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">How It Works</a>
    <a href="#team" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">Team</a>
  </nav>

  <div className="flex items-center gap-3">
    <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
      <Button
        onClick={toggleTheme}
        variant="outline"
        size="sm"
        className="h-9 w-9 p-0 rounded-md border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-transparent text-[#3A3936] dark:text-[#CCC9C3]"
      >
        {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      </Button>
    </Tooltip>

    <Button
      variant="outline"
      onClick={() => setDownloadModalOpen(true)}
      className="hidden sm:flex items-center gap-2 rounded-md border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-transparent text-[#0A0A0A] dark:text-[#F2F0EC]"
    >
      <Download className="h-4 w-4" />
      <span>Download APK</span>
    </Button>

    <Link to="/dashboard">
      <Button className="rounded-md bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center gap-2">
        <span>Command Center</span>
        <ArrowRight className="h-4 w-4" />
      </Button>
    </Link>
  </div>
</header>

      {/* Hero Section */}
      <section
        onMouseMove={handleHeroMouseMove}
        onMouseLeave={handleHeroMouseLeave}
        className="relative overflow-hidden px-6 lg:px-12"
      >
        {/* Quiet drifting net, sitting below the content but still legible */}
        <div className="absolute inset-0 opacity-[0.55] dark:opacity-[0.4]">
          <ShapeGrid
            ref={shapeGridRef}
            speed={0.3}
            size={44}
            direction="diagonal"
            shape="square"
            borderColor={theme === 'dark' ? '#3A4145' : '#C9C4B8'}
            hoverColor="rgba(209, 80, 51, 0.22)"
            hoverTrailAmount={2}
            lineWidth={1.25}
          />
        </div>

        {/* Fade the grid out toward the content edges so it reads as texture, not noise */}
        <div className="absolute inset-0 bg-gradient-to-b from-white via-white/40 to-white dark:from-[#0F1214] dark:via-[#0F1214]/40 dark:to-[#0F1214] pointer-events-none" />

        <div className="relative max-w-7xl mx-auto py-16 lg:py-24 grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
        <div className="space-y-8">

          <h1 className="text-4xl sm:text-5xl font-bold tracking-tight leading-[1.15] text-[#0A0A0A] dark:text-white">
            Emergency alerts that reach responders, even offline.
          </h1>

          <p className="text-base text-[#4B4946] dark:text-[#B9B6B0] max-w-lg leading-relaxed">
            AGAP sends emergency location alerts over mobile networks and falls back to a phone-to-phone
            Bluetooth relay when cellular service or internet is unavailable.
          </p>

          <div className="flex flex-col sm:flex-row gap-3 pt-2">
            <Button
              size="lg"
              onClick={() => setDownloadModalOpen(true)}
              className="h-12 px-6 text-sm rounded-md bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center justify-center gap-2"
            >
              <Download className="h-4 w-4" />
              Download Android App
            </Button>

            <Link to="/dashboard">
              <Button
                size="lg"
                variant="outline"
                className="h-12 px-6 text-sm rounded-md border-[#D8D4CB] dark:border-[#26292C] bg-white dark:bg-transparent text-[#0A0A0A] dark:text-[#F2F0EC] w-full flex items-center justify-center gap-2 font-semibold"
              >
                Launch Command Center
                <ChevronRight className="h-4 w-4 text-[#6B6864]" />
              </Button>
            </Link>
          </div>

          {/* Metrics — boxed stat cards */}
          <div className="pt-4 grid grid-cols-3 gap-3 max-w-lg">
            <div className="rounded-lg border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] px-4 py-4 shadow-sm">
              <div className="text-2xl font-bold text-[#0A0A0A] dark:text-white">0.4s</div>
              <div className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-1">Dispatch latency</div>
            </div>
            <div className="rounded-lg border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] px-4 py-4 shadow-sm">
              <div className="text-2xl font-bold text-[#0A0A0A] dark:text-white">255B</div>
              <div className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-1">BLE payload size</div>
            </div>
            <div className="rounded-lg border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] px-4 py-4 shadow-sm">
              <div className="text-2xl font-bold text-[#0A0A0A] dark:text-white">100%</div>
              <div className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-1">Offline resiliency</div>
            </div>
          </div>
        </div>

        {/* Signature element: iPhone showcase */}
        <div className="w-full flex justify-center lg:scale-125 lg:origin-center">
          <img
            src={iphoneImg}
            alt="App running on iPhone"
            className="w-full max-w-5xl h-auto"
          />
        </div>
        </div>
        </section>

      {/* Capabilities — boxed, bordered card grid */}
      <section id="features" className="border-t border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#141719]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-16 lg:py-20">
          <motion.div
            className="max-w-2xl mb-12"
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: '-80px' }}
            variants={fadeUp}
          >
            <span className="text-xs font-semibold tracking-widest text-[#D15033] uppercase">Capabilities</span>
            <h2 className="text-3xl font-bold text-[#0A0A0A] dark:text-white mt-2">
              Built for typhoon blackouts and dead zones
            </h2>
            <p className="text-[#4B4946] dark:text-[#B9B6B0] mt-3 leading-relaxed">
              Designed for Legazpi City's terrain, recurring outages, and areas with limited prepaid load.
            </p>
          </motion.div>

          <motion.div
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5"
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
                  className={`group relative rounded-lg border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#181C1F] p-6 shadow-sm transition-all duration-300 ease-out hover:-translate-y-1 hover:shadow-lg ${tone.hoverBorder}`}
                >
                  {/* Soft tone wash that fades in on hover, kept subtle behind the content */}
                  <div
                    className={`pointer-events-none absolute inset-0 rounded-lg opacity-0 transition-opacity duration-300 ease-out group-hover:opacity-100 ${tone.hoverWash}`}
                  />
                  <div className="relative">
                    <div
                      className={`h-11 w-11 rounded-md ${tone.bg} ${tone.darkBg} flex items-center justify-center mb-4 transition-transform duration-300 ease-out group-hover:scale-105`}
                    >
                      <Icon className={`h-5 w-5 ${tone.text} ${tone.darkText}`} />
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
<section
  id="how-it-works"
  className="border-t border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]"
>
  <div className="max-w-7xl mx-auto px-6 lg:px-12 py-16 lg:py-20">
    <motion.div
      className="max-w-2xl mb-14"
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: "-80px" }}
      variants={fadeUp}
    >
      <span className="text-xs font-semibold tracking-widest text-[#D15033] uppercase">
        Process
      </span>

      <h2 className="mt-2 text-3xl font-bold text-[#0A0A0A] dark:text-white">
        How an alert gets delivered
      </h2>

      <p className="mt-4 text-[#5C5A56] dark:text-[#B9B6B0] leading-relaxed">
        AGAP intelligently delivers emergency alerts through cellular networks
        and seamlessly switches to Bluetooth mesh networking whenever internet
        connectivity is unavailable.
      </p>
    </motion.div>

    <motion.div
      className="grid grid-cols-1 md:grid-cols-3 gap-8"
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: "-80px" }}
      variants={staggerContainer}
    >
      {PROCESS.map((step) => (
        <motion.div
          key={step.number}
          variants={fadeUp}
          className="group"
        >
          {/* Step */}
          <p className="text-sm font-semibold text-[#D15033] mb-2">
            Step {step.number}
          </p>

          {/* Title */}
          <h3 className="text-2xl font-bold leading-tight text-[#0A0A0A] dark:text-white mb-5">
            {step.title}
          </h3>

          {/* Illustration */}
          <div className="rounded-2xl border border-[#E7E3DC] dark:border-[#26292C] bg-[#FAF8F5] dark:bg-[#181C1F] overflow-hidden transition-all duration-300 group-hover:shadow-xl group-hover:-translate-y-1">
            <img
              src={step.image}
              alt={step.title}
              className="w-full h-96 object-contain p-4"
            />
          </div>

          {/* Description */}
          <p className="mt-5 text-sm leading-7 text-[#5C5A56] dark:text-[#B9B6B0]">
            {step.description}
          </p>
        </motion.div>
      ))}
    </motion.div>
  </div>
</section>

      {/* Team — boxed member cards */}
      <section id="team" className="border-t border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#141719]">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-16 lg:py-20">
          <div className="max-w-2xl mb-12">
            <span className="text-xs font-semibold tracking-widest text-[#D15033] uppercase">
              Heroes of Innovation Challenge 2026
            </span>
            <h2 className="text-3xl font-bold text-[#0A0A0A] dark:text-white mt-2">
              The team behind AGAP
            </h2>
            <p className="text-[#4B4946] dark:text-[#B9B6B0] mt-3 leading-relaxed">
              Team Fellowship (Traveling Salesman)
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            {TEAM.map((member, i) => {
              const initials = member.name.split(' ').filter(Boolean);
              const monogram = `${initials[0]?.[0] ?? ''}${initials[1]?.[0] ?? ''}`;
              const tones = ['primary', 'secondary', 'accent', 'secondary'] as const;
              const tone = TONE_STYLES[tones[i % tones.length]];
              return (
                <div
                  key={i}
                  className="rounded-lg border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#181C1F] p-6 shadow-sm hover:shadow-md transition-shadow flex items-start gap-4"
                >
                  <div className={`h-11 w-11 rounded-md ${tone.bg} ${tone.darkBg} flex items-center justify-center text-sm font-bold ${tone.text} ${tone.darkText} shrink-0`}>
                    {monogram}
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white leading-tight">
                      {member.name}
                    </h3>
                    <p className="text-xs font-medium text-[#D15033] mt-0.5">{member.role}</p>
                    <p className="text-sm text-[#4B4946] dark:text-[#B9B6B0] mt-2 leading-relaxed">
                      {member.focus}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-[#26292C] bg-[#0F1214] px-6 lg:px-12 py-5">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <img
              src={agapLogo}
              alt="AGAP"
              className="h-20 sm:h-26 w-auto object-contain shrink-0"
            />
            <div className="hidden sm:block leading-tight">
              <p className="text-[11px] text-[#9C9994]">
                Accelerated Geolocation Assistance &amp; Protection
              </p>
            </div>
          </div>
          <div className="text-center sm:text-right">
            <p className="text-xs text-[#F2F0EC]">
              Ibalong Festival 2026 · Heroes of Innovation Challenge
            </p>
            <p className="text-[11px] text-[#9C9994] mt-1">
              Built for Legazpi City Disaster Risk Reduction and Management Office
            </p>
          </div>
        </div>
      </footer>

      {/* Download Modal */}
      <Dialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen}>
        <div className="space-y-5 py-1">
          <div className="flex items-center gap-3">
            <div className="h-11 w-11 rounded-md bg-[#D15033]/10 dark:bg-[#D15033]/15 flex items-center justify-center shrink-0">
              <Smartphone className="h-5 w-5 text-[#D15033]" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-[#0A0A0A] dark:text-white flex items-center gap-2">
                AGAP Android Release
                <Badge className="rounded-sm bg-[#F2A540] text-[#0A0A0A] border-0 text-[10px] font-semibold">
                  COMING SOON
                </Badge>
              </h3>
              <p className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-0.5">Bluetooth mesh &amp; Wi-Fi Direct APK package</p>
            </div>
          </div>

          <div className="border border-[#E7E3DC] dark:border-[#26292C] rounded-lg text-xs text-[#0A0A0A] dark:text-[#F2F0EC] overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1F]">
              <span className="text-[#6B6864] dark:text-[#9C9994]">Target OS</span>
              <span className="font-medium">Android 9.0+ (API 28)</span>
            </div>
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#E7E3DC] dark:border-[#26292C]">
              <span className="text-[#6B6864] dark:text-[#9C9994]">Required hardware</span>
              <span className="font-medium">Bluetooth 5.0 (extended advertising)</span>
            </div>
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1F]">
              <span className="text-[#6B6864] dark:text-[#9C9994]">P2P transfer</span>
              <span className="font-medium">Wi-Fi Direct + local Bluetooth</span>
            </div>
            <div className="flex items-center justify-between px-4 py-2.5">
              <span className="text-[#6B6864] dark:text-[#9C9994]">Status</span>
              <span className="font-medium text-[#D15033]">Final staging build</span>
            </div>
          </div>

          <p className="text-xs text-[#4B4946] dark:text-[#B9B6B0] leading-relaxed">
            The Android app is completing local mesh latency testing with Legazpi City responders.
            In the meantime, you can explore the live dispatch interface.
          </p>

          <div className="flex items-center justify-end gap-3 pt-1">
            <Button variant="outline" className="rounded-md" onClick={() => setDownloadModalOpen(false)}>
              Close
            </Button>
            <Link to="/dashboard" onClick={() => setDownloadModalOpen(false)}>
              <Button className="rounded-md bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center gap-2">
                Explore Command Center
                <ArrowUpRight className="h-4 w-4" />
              </Button>
            </Link>
          </div>
        </div>
      </Dialog>
    </div>
  );
};