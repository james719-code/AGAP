import { ArrowRight, Moon, Sun } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import agapLogo from '@/assets/logos/logo-name-nobg.png';

interface LandingHeaderProps {
  scrolled: boolean;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
}

export function LandingHeader({ scrolled, theme, onToggleTheme }: LandingHeaderProps) {
  return (
    <header className={`fixed z-40 left-0 right-0 flex justify-center transition-[top,padding] duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${scrolled ? 'top-3 px-4' : 'top-0 px-0'}`}>
      <div className={`w-full flex items-center justify-between transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${scrolled ? 'max-w-5xl h-16 px-4 lg:px-6 rounded-sm border border-[#E7E3DC]/80 dark:border-[#26292C] bg-white/75 dark:bg-[#0F1214]/75 backdrop-blur-xl shadow-[0_8px_30px_rgba(10,10,10,0.08)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.35)]' : 'max-w-7xl h-16 px-6 lg:px-12 rounded-none border-b border-transparent bg-transparent backdrop-blur-0 shadow-none'}`}>
        <a href="#top" className="flex items-center gap-2.5 shrink-0 min-w-0">
          <img src={agapLogo} alt="AGAP" className={`w-auto object-contain shrink-0 transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${scrolled ? 'h-7' : 'h-9'}`} />
          <span className={`hidden md:block text-[#6B6864] dark:text-[#9C9994] leading-tight border-l border-[#E7E3DC] dark:border-[#26292C] pl-2.5 transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${scrolled ? 'text-[10px]' : 'text-xs'}`}>
            Accelerated Geolocation<br />Assistance &amp; Protection
          </span>
        </a>
        <nav className="hidden lg:flex items-center gap-8 text-sm font-medium text-[#3A3936] dark:text-[#CCC9C3]">
          {[['#features', 'Features'], ['#how-it-works', 'How It Works'], ['#team', 'Team']].map(([href, label]) => (
            <a key={href} href={href} className="relative py-1 hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors group">
              {label}<span className="absolute left-0 -bottom-0.5 h-px w-0 bg-[#D15033] transition-all duration-300 ease-out group-hover:w-full" />
            </a>
          ))}
        </nav>
        <div className="flex items-center gap-2 shrink-0">
          <Tooltip content={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
            <Button onClick={onToggleTheme} variant="outline" size="sm" className="h-9 w-9 p-0 rounded-sm border-[#E7E3DC] dark:border-[#26292C] bg-white/70 dark:bg-transparent text-[#3A3936] dark:text-[#CCC9C3] transition-transform duration-200 hover:scale-105 active:scale-95">
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </Button>
          </Tooltip>
          <Link to="/dashboard">
            <Button className="h-9 px-4 rounded-sm bg-[#D15033] hover:bg-[#B5432A] text-white text-sm font-semibold flex items-center gap-1.5 transition-all duration-200 hover:shadow-[0_4px_14px_rgba(209,80,51,0.35)] active:scale-[0.98]">
              Command Center<ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        </div>
      </div>
    </header>
  );
}
