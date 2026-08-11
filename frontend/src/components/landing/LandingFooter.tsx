import agapLogo from '@/assets/logos/logo-name-nobg.png';
import travelingSalesmanLogo from '@/assets/logos/traveling-salesman.png';

export function LandingFooter() {
  return (
    <footer className="bg-black px-6 lg:px-12 py-10">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center sm:items-end justify-between gap-8">
        {/* AGAP Branding */}
        <div className="flex flex-col items-center sm:items-start gap-3">
          <img
            src={agapLogo}
            alt="AGAP"
            className="h-10 w-auto object-contain shrink-0"
          />

          <p className="text-xs text-white leading-tight text-center sm:text-left">
            Accelerated Geolocation<br />
            Assistance &amp; Protection
          </p>
        </div>

        {/* Event Information + Traveling Salesman Logo */}
        <div className="flex items-center gap-5">
          <div className="text-center sm:text-right">
            <p className="text-xs font-medium text-white">
              Ibalong Festival 2026 · Heroes of Innovation Challenge
            </p>
            <p className="text-[11px] text-white/60 mt-1">
              Built for disaster risk reduction and emergency response
            </p>
          </div>

          <img
            src={travelingSalesmanLogo}
            alt="Traveling Salesman"
            className="h-16 w-auto object-contain opacity-90 shrink-0"
          />
        </div>
      </div>
    </footer>
  );
}
