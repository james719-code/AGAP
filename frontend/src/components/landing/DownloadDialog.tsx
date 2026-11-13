import { ArrowUpRight, Cpu, Download, Radio, ShieldCheck, Smartphone } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import androidRelease from '@/config/android-release.json';

const RELEASES_URL = `https://github.com/${androidRelease.repository}/releases`;
const APK_URL = `${RELEASES_URL}/latest/download/${androidRelease.assetName}`;

interface DownloadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const REQUIREMENTS = [
  { label: 'Target operating system', value: 'Android 9.0+ (API 28)', icon: Smartphone },
  { label: 'Radio hardware', value: 'Bluetooth 5.0 (Extended Advertising)', icon: Radio },
  { label: 'Offline distribution', value: 'Wi-Fi Direct and BLE app sharing', icon: Cpu },
  { label: 'Security & validation', value: 'Encrypted & verified', icon: ShieldCheck },
];

export function DownloadDialog({ open, onOpenChange }: DownloadDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      contentClassName="rounded-2xl border border-border bg-card p-6 text-foreground shadow-2xl backdrop-blur-xl sm:max-w-lg"
    >
      <div className="space-y-6">
        <div className="flex items-start gap-4">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20">
            <Smartphone className="size-6" aria-hidden="true" />
          </div>
          <div className="min-w-0 pr-6">
            <div className="flex flex-wrap items-center gap-2">
              <DialogTitle className="text-lg font-bold text-foreground">
                AGAP Citizen Shield
              </DialogTitle>
              <span
                role="status"
                className="inline-flex items-center gap-1.5 rounded-full bg-secondary/10 px-2.5 py-0.5 text-xs font-semibold text-secondary ring-1 ring-secondary/20"
              >
                <span>Field testing</span>
              </span>
            </div>
            <DialogDescription className="mt-1 text-xs text-muted-foreground leading-relaxed">
              Standalone Android app with zero-load offline distress alerts and background emergency relaying.
            </DialogDescription>
          </div>
        </div>

        {/* Requirements Card */}
        <div className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-muted/30 text-xs">
          {REQUIREMENTS.map(({ label, value, icon: Icon }) => (
            <div key={label} className="flex items-center justify-between gap-3 px-4 py-3">
              <span className="flex items-center gap-2.5 text-muted-foreground">
                <Icon className="size-3.5 text-secondary" aria-hidden="true" />
                <span>{label}</span>
              </span>
              <span className="font-semibold text-foreground text-right">
                {value}
              </span>
            </div>
          ))}
        </div>

        {/* Note / instruction */}
        <div className="rounded-xl border border-border bg-card p-3.5 text-xs text-muted-foreground shadow-2xs">
          <p className="leading-relaxed">
            Download the latest available Android test build from GitHub releases, then open the APK file to install on your device.
          </p>
          <a
            href={RELEASES_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 inline-flex items-center gap-1 font-semibold text-secondary hover:underline"
          >
            <span>View release notes on GitHub</span>
            <ArrowUpRight className="size-3.5" aria-hidden="true" />
            <span className="sr-only"> (opens in a new tab)</span>
          </a>
        </div>

        {/* Actions */}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
          <Button
            type="button"
            variant="ghost"
            className="rounded-xl text-muted-foreground hover:bg-muted hover:text-foreground"
            onClick={() => onOpenChange(false)}
          >
            Close
          </Button>

          <Link
            to="/dashboard"
            onClick={() => onOpenChange(false)}
            className="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl border border-border bg-card px-4 text-xs font-semibold text-foreground shadow-2xs hover:bg-muted motion-safe:active:scale-95 transition-colors"
          >
            <span>Command Center</span>
            <ArrowUpRight className="size-3.5" aria-hidden="true" />
          </Link>

          <a
            href={APK_URL}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-xs font-semibold text-primary-foreground shadow-xs hover:opacity-90 motion-safe:active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-opacity"
          >
            <Download className="size-4" aria-hidden="true" />
            <span>Download Android APK</span>
          </a>
        </div>
      </div>
    </Dialog>
  );
}
