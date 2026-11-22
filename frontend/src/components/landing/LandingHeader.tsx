import { useEffect, useRef, useState } from 'react';
import { ArrowRight, Download, Menu, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import agapLogo from '@/assets/logos/logo-name-nobg.png';

const NAV_ITEMS = [
  { href: '#how-it-works', id: 'how-it-works', label: 'How it works' },
  { href: '#command-center', id: 'command-center', label: 'For responders' },
  { href: '#features', id: 'features', label: 'Technology' },
  { href: '#team', id: 'team', label: 'Team' },
];

export function LandingHeader({ onDownload }: { onDownload: () => void }) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [activeSection, setActiveSection] = useState<string>('');
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!mobileMenuOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setMobileMenuOpen(false);
        menuButtonRef.current?.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [mobileMenuOpen]);

  useEffect(() => {
    const sections = NAV_ITEMS.map(({ id }) => document.getElementById(id)).filter(
      (el): el is HTMLElement => el !== null
    );
    if (!sections.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible.length > 0) {
          setActiveSection(visible[0].target.id);
        }
      },
      { rootMargin: '-20% 0px -65% 0px', threshold: 0 }
    );

    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, []);

  return (
    <>
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-xl focus:border focus:border-primary focus:bg-primary focus:px-4 focus:py-2.5 focus:text-sm focus:font-semibold focus:text-primary-foreground focus:shadow-md focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
      >
        Skip to main content
      </a>

      <header className="sticky top-0 z-40 border-b border-[#0f1b1f10] bg-[#f6f5f2]/85 backdrop-blur-xl">
        <div className="mx-auto flex min-h-[4.25rem] max-w-7xl items-center justify-between gap-3 px-4 sm:px-8">
          <div className="flex min-w-0 items-center">
            <a
              href="#top"
              className="flex min-h-11 shrink-0 items-center rounded-xl p-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary motion-safe:active:scale-95 motion-safe:transition-transform motion-reduce:transition-none"
              aria-label="AGAP emergency platform home"
            >
              <img src={agapLogo} alt="AGAP" className="h-7 w-auto object-contain sm:h-8" />
            </a>
          </div>

          <nav aria-label="Primary navigation" className="hidden items-center gap-1 lg:flex">
            {NAV_ITEMS.map(({ href, id, label }) => {
              const isActive = activeSection === id;
              return (
                <a
                  key={href}
                  href={href}
                  onClick={() => setActiveSection(id)}
                  aria-current={isActive ? 'location' : undefined}
                  className={`inline-flex min-h-9 items-center rounded-full px-4 text-sm font-medium whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary motion-reduce:transition-none ${
                    isActive
                      ? 'bg-[#0f1b1f] font-semibold text-white'
                      : 'text-[#3d4d53] hover:bg-[#0f1b1f0d] hover:text-[#0f1b1f]'
                  }`}
                >
                  {label}
                </a>
              );
            })}
          </nav>

          <div className="flex shrink-0 items-center gap-2">
            <Link
              to="/login"
              className="hidden min-h-11 items-center justify-center gap-2 rounded-full px-4 text-sm font-semibold text-[#0f1b1f] hover:bg-[#0f1b1f0d] lg:inline-flex"
            >
              <span>Responder sign in</span>
              <ArrowRight aria-hidden="true" className="size-3.5 shrink-0" />
            </Link>
            <button type="button" onClick={onDownload} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-full bg-[#0f1b1f] px-4 text-sm font-semibold text-white hover:bg-[#24333a] sm:px-5">
              <Download className="size-4" aria-hidden="true" /> Get AGAP
            </button>

            <button
              ref={menuButtonRef}
              type="button"
              onClick={() => setMobileMenuOpen((open) => !open)}
              className="inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-[#0f1b1f1f] text-[#0f1b1f] hover:bg-[#0f1b1f0d] motion-safe:active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary transition-colors lg:hidden"
              aria-expanded={mobileMenuOpen}
              aria-controls="mobile-navigation"
              aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            >
              {mobileMenuOpen ? (
                <X className="size-5" aria-hidden="true" />
              ) : (
                <Menu className="size-5" aria-hidden="true" />
              )}
            </button>
          </div>
        </div>

        {mobileMenuOpen && (
          <div
            id="mobile-navigation"
            className="border-b border-[#0f1b1f10] bg-[#f6f5f2] px-4 py-4 shadow-lg sm:px-8 lg:hidden motion-safe:animate-in motion-safe:slide-in-from-top-2 duration-150 motion-reduce:animate-none"
          >
            <nav aria-label="Mobile navigation" className="flex flex-col gap-1.5">
              {NAV_ITEMS.map(({ href, id, label }) => {
                const isActive = activeSection === id;
                return (
                  <a
                    key={href}
                    href={href}
                    onClick={() => {
                      setActiveSection(id);
                      setMobileMenuOpen(false);
                    }}
                    aria-current={isActive ? 'location' : undefined}
                    className={`flex min-h-11 items-center justify-between rounded-xl px-4 text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-[#0f1b1f] font-semibold text-white'
                        : 'text-[#0f1b1f] hover:bg-[#0f1b1f0d]'
                    }`}
                  >
                    <span>{label}</span>
                    {isActive && (
                      <span className="size-1.5 rounded-full bg-[#ff7a63]" />
                    )}
                  </a>
                );
              })}

              <div className="mt-3 border-t border-border pt-3">
                <Link
                  to="/login"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex min-h-11 items-center justify-between rounded-xl border border-border bg-muted/50 px-4 text-sm font-semibold text-foreground hover:bg-muted transition-colors"
                >
                  <span>Command center sign in</span>
                  <ArrowRight className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                </Link>
              </div>
            </nav>
          </div>
        )}
      </header>
    </>
  );
}
