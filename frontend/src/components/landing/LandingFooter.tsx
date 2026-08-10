import agapLogo from '@/assets/logos/logo-name-nobg.png';

export function LandingFooter() {
  return (
    <footer className="border-t border-[#D15033]/30 bg-[#0F1214] px-6 lg:px-12 py-10">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center sm:items-end justify-between gap-6">
        <div className="flex items-center gap-3">
          <img src={agapLogo} alt="AGAP" className="h-10 w-auto object-contain shrink-0" />
          <p className="hidden sm:block text-xs text-[#E8927C] leading-tight border-l border-[#D15033]/30 pl-3">Accelerated Geolocation<br />Assistance &amp; Protection</p>
        </div>
        <div className="text-center sm:text-right">
          <p className="text-xs font-medium text-[#D15033]">Ibalong Festival 2026 · Heroes of Innovation Challenge</p>
          <p className="text-[11px] text-[#E8927C] mt-1">Built for Legazpi City Disaster Risk Reduction and Management Office</p>
        </div>
      </div>
    </footer>
  );
}