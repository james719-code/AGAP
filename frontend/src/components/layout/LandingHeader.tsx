import { ArrowRight, Download } from "lucide-react";
import { Link } from "react-router-dom";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { Button } from "@/components/ui/button";
import agapLogo from "@/assets/logos/logo-text-v3.svg";

interface LandingHeaderProps {
  onDownload: () => void;
}

export function LandingHeader({ onDownload }: LandingHeaderProps) {
  return (
    <header className="sticky top-0 z-40 w-full bg-white/95 dark:bg-[#0F1214]/95 backdrop-blur border-b border-[#E7E3DC] dark:border-[#26292C] px-6 lg:px-12 h-16 flex items-center justify-between">
      <div className="h-1 top-0 left-0 w-full bg-[#D15033] absolute -z-1" />
      <div className="flex items-center gap-3">
        <img src={agapLogo} alt="AGAP" className="h-10 sm:h-12 w-auto object-contain shrink-0" />
        <p className="text-[11px] sm:text-xs text-[#6B6864] dark:text-[#9C9994] hidden sm:block tracking-wide leading-none">
          Accelerated Geolocation Assistance &amp; Protection
        </p>
      </div>
      <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-[#3A3936] dark:text-[#CCC9C3]" aria-label="Main navigation">
        <a href="#features" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">Capabilities</a>
        <a href="#how-it-works" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">How It Works</a>
        <a href="#team" className="hover:text-[#D15033] dark:hover:text-[#E9835F] transition-colors">Team</a>
      </nav>
      <div className="flex items-center gap-3">
        <ThemeToggle className="border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-transparent text-[#3A3936] dark:text-[#CCC9C3]" />
        <Button variant="outline" onClick={onDownload} className="hidden sm:flex items-center gap-2 rounded-md border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-transparent text-[#0A0A0A] dark:text-[#F2F0EC]">
          <Download className="h-4 w-4" /><span>Download APK</span>
        </Button>
        <Link to="/dashboard">
          <Button className="rounded-md bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center gap-2">
            <span>Command Center</span><ArrowRight className="h-4 w-4" />
          </Button>
        </Link>
      </div>
    </header>
  );
}
