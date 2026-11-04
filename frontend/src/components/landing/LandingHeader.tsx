import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonVariants } from '@/components/ui/button';
import agapLogo from '@/assets/logos/logo-name-nobg.png';

const sections = [
  ['#features', 'Features'],
  ['#how-it-works', 'How it works'],
  ['#team', 'Team'],
];

export function LandingHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur-sm">
      <div className="mx-auto flex min-h-15 max-w-6xl items-center justify-between gap-3 px-5 sm:px-8">
        <a href="#top" className="flex min-h-11 items-center" aria-label="AGAP home">
          <img src={agapLogo} alt="AGAP" className="h-6 w-auto object-contain" />
        </a>
        <nav aria-label="About AGAP" className="hidden items-center gap-5 text-sm font-medium text-muted-foreground sm:flex">
          {sections.map(([href, label]) => (
            <a key={href} href={href} className="py-2 hover:text-foreground">{label}</a>
          ))}
        </nav>
        <Link to="/dashboard" className={buttonVariants({ variant: 'outline', className: 'gap-1.5' })}>
          Command Center
          <ArrowRight aria-hidden="true" className="size-3.5" />
        </Link>
      </div>
    </header>
  );
}
