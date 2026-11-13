import { ArrowUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import agapLogo from '@/assets/logos/logo-name-nobg.png';
import travelingSalesmanLogo from '@/assets/logos/traveling-salesman.png';

const PLATFORM_LINKS = [
  { href: '#how-it-works', label: 'How it works' },
  { href: '#specs', label: 'Specifications' },
  { href: '#features', label: 'Capabilities' },
  { href: '#team', label: 'The Fellowship team' },
];

const DISPATCH_LINKS = [
  { href: '/login', label: 'Dispatcher sign in' },
  { href: '/dashboard', label: 'Command Center console' },
];

export function LandingFooter() {
  return (
    <footer className="border-t border-border bg-muted/30 px-4 py-12 text-foreground transition-colors sm:px-8 sm:py-16">
      <div className="mx-auto max-w-6xl">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,0.9fr)_minmax(0,0.95fr)_minmax(0,1.3fr)]">
          <div className="space-y-4">
            <a
              href="#top"
              aria-label="AGAP home"
              className="inline-flex min-h-11 items-center rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <img src={agapLogo} alt="AGAP" className="h-7 w-auto shrink-0 object-contain" />
            </a>
            <p className="max-w-[40ch] text-xs leading-relaxed text-muted-foreground sm:text-sm">
              Accelerated Geolocation-based Assistance and Protection. Dual-channel emergency distress alert delivery for zero-connectivity disaster resilience in Camarines Sur and Albay.
            </p>
            <div className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs font-medium text-foreground shadow-2xs">
              <span className="size-2 rounded-full bg-secondary" />
              <span>Offline Bluetooth Relay Network</span>
            </div>
          </div>

          <div className="space-y-3">
            <p className="text-xs font-semibold text-foreground">
              Platform
            </p>
            <ul className="flex flex-col space-y-2">
              {PLATFORM_LINKS.map(({ href, label }) => (
                <li key={href}>
                  <a
                    href={href}
                    className="inline-flex min-h-8 items-center text-sm text-muted-foreground hover:text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-md"
                  >
                    {label}
                  </a>
                </li>
              ))}
            </ul>
          </div>

          <div className="space-y-3">
            <p className="text-xs font-semibold text-foreground">
              Command Center
            </p>
            <ul className="flex flex-col space-y-2">
              {DISPATCH_LINKS.map(({ href, label }) => (
                <li key={href}>
                  <Link
                    to={href}
                    className="inline-flex min-h-8 items-center text-sm text-muted-foreground hover:text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-md"
                  >
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <p className="text-xs font-semibold text-foreground">
                Origin
              </p>
              <a
                href="#top"
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1 text-xs font-medium text-foreground shadow-2xs hover:bg-muted motion-safe:active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary transition-colors"
                aria-label="Return to top"
              >
                <span>Back to top</span>
                <ArrowUp className="size-3 shrink-0" aria-hidden="true" />
              </a>
            </div>

            <div className="flex items-center gap-3.5 rounded-2xl border border-border bg-card p-4 shadow-xs">
              <img
                src={travelingSalesmanLogo}
                alt="Traveling Salesman team emblem"
                className="size-11 shrink-0 rounded-xl border border-border/80 object-contain p-0.5 bg-white"
              />
              <div className="text-xs leading-relaxed">
                <span className="font-semibold text-foreground block">Heroes of Innovation Challenge 2026</span>
                <span className="text-muted-foreground block text-[11px]">Ibalong Festival, Legazpi City</span>
                <span className="text-primary font-medium block text-[11px]">Team Fellowship · Traveling Salesman</span>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-border pt-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <p className="max-w-[65ch]">
            Cartographic boundaries derived from geoBoundaries (NAMRIA, PSA, OCHA Philippines) under CC BY 3.0 IGO.
          </p>
          <p className="whitespace-nowrap">
            &copy; {new Date().getFullYear()} AGAP Emergency Response Platform.
          </p>
        </div>
      </div>
    </footer>
  );
}
