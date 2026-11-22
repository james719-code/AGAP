import { ArrowUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import agapLogo from '@/assets/logos/logo-name-nobg.png';
import travelingSalesmanLogo from '@/assets/logos/traveling-salesman.png';

const PLATFORM_LINKS = [
  { href: '#how-it-works', label: 'How it works' },
  { href: '#command-center', label: 'For responders' },
  { href: '#features', label: 'Technology' },
  { href: '#team', label: 'Team' },
];

const DISPATCH_LINKS = [
  { href: '/login', label: 'Responder sign in' },
  { href: '/dashboard', label: 'Command Center' },
];

const linkClass = 'inline-flex min-h-8 items-center rounded-md text-sm text-[#3d4d53] transition-colors hover:text-[#c8321f] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

export function LandingFooter() {
  return (
    <footer className="border-t border-[#0f1b1f12] px-5 py-14 text-[#0f1b1f] sm:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,0.7fr)_minmax(0,0.7fr)_minmax(0,1.1fr)]">
          <div className="space-y-4">
            <a href="#top" aria-label="AGAP home" className="inline-flex min-h-11 items-center rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
              <img src={agapLogo} alt="AGAP" className="h-8 w-auto object-contain" />
            </a>
            <p className="max-w-[38ch] text-sm leading-relaxed text-[#52636a]">
              Accelerated Geolocation-based Assistance and Protection — offline-first emergency alerts for Camarines Sur and Albay.
            </p>
            <p className="text-sm text-[#52636a]">In an emergency, always call <a href="tel:911" className="font-bold text-[#c8321f]">911</a>.</p>
          </div>

          <nav aria-label="Platform" className="space-y-3">
            <p className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.14em] text-[#52636a]">Platform</p>
            <ul className="flex flex-col gap-1">
              {PLATFORM_LINKS.map(({ href, label }) => (
                <li key={href}><a href={href} className={linkClass}>{label}</a></li>
              ))}
            </ul>
          </nav>

          <nav aria-label="Command Center" className="space-y-3">
            <p className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.14em] text-[#52636a]">Responders</p>
            <ul className="flex flex-col gap-1">
              {DISPATCH_LINKS.map(({ href, label }) => (
                <li key={href}><Link to={href} className={linkClass}>{label}</Link></li>
              ))}
            </ul>
          </nav>

          <div className="flex items-start gap-3.5">
            <img src={travelingSalesmanLogo} alt="Traveling Salesman team emblem" className="size-12 shrink-0 rounded-2xl bg-white object-contain p-1 ring-1 ring-[#0f1b1f12]" />
            <div className="text-sm leading-relaxed">
              <span className="block font-semibold">Heroes of Innovation Challenge 2026</span>
              <span className="block text-[#52636a]">Ibalong Festival, Legazpi City</span>
              <span className="block text-[#52636a]">Team Fellowship · Traveling Salesman</span>
            </div>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-[#0f1b1f12] pt-6 text-xs text-[#52636a] sm:flex-row sm:items-center sm:justify-between">
          <p className="max-w-[65ch]">Cartographic boundaries derived from geoBoundaries (NAMRIA, PSA, OCHA Philippines) under CC BY 3.0 IGO.</p>
          <div className="flex items-center gap-4">
            <p className="whitespace-nowrap">&copy; {new Date().getFullYear()} AGAP</p>
            <a href="#top" className="inline-flex items-center gap-1.5 rounded-full border border-[#0f1b1f1f] px-3 py-1.5 font-semibold text-[#0f1b1f] hover:bg-[#0f1b1f0d]" aria-label="Back to top">
              Top <ArrowUp className="size-3" aria-hidden="true" />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
