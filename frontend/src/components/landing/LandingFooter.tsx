import agapLogo from '@/assets/logos/logo-name-nobg.png';
import travelingSalesmanLogo from '@/assets/logos/traveling-salesman.png';

export function LandingFooter() {
  return (
    <footer className="border-t border-border bg-card px-5 py-5 sm:px-8">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        {/* AGAP Branding */}
        <div className="flex items-center gap-3">
          <img
            src={agapLogo}
            alt="AGAP"
            className="h-6 w-auto shrink-0 object-contain"
          />

          <p className="text-xs leading-tight text-muted-foreground">
            Accelerated Geolocation<br />
            Assistance &amp; Protection
          </p>
        </div>

        {/* Regional focus and team credit */}
        <div className="flex items-center gap-3">
          <div className="sm:text-right">
            <p className="text-xs font-medium text-foreground">
              Emergency preparedness in Camarines Sur
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Built by Team Fellowship (Traveling Salesman)
            </p>
          </div>

          <img
            src={travelingSalesmanLogo}
            alt="Traveling Salesman"
            className="h-9 w-auto shrink-0 object-contain"
          />
        </div>
      </div>
    </footer>
  );
}
