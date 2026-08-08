import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { X, Settings, Radio, BellRing, Check } from 'lucide-react';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const STORAGE_KEY = 'agap_dispatcher_settings';

interface DispatcherSettings {
  maxHops: string;
  packetTtl: string;
  channelMode: string;
  autoSiren: boolean;
  hubRegion: string;
}

const DEFAULT_SETTINGS: DispatcherSettings = {
  maxHops: '5',
  packetTtl: '10',
  channelMode: 'EXTENDED_ADV',
  autoSiren: true,
  hubRegion: 'Legazpi City (Region V)',
};

// These map straight to the BLE mesh knobs advertised in this modal — keep
// this in sync with whatever the mesh/relay layer actually reads at runtime
// (Android device or gateway service), otherwise the value gets persisted
// but never takes effect anywhere.
function loadSettings(): DispatcherSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export const SettingsModal: React.FC<SettingsModalProps> = ({ isOpen, onClose }) => {
  const [settings, setSettings] = useState<DispatcherSettings>(loadSettings);
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  if (!isOpen) return null;

  const updateField = <K extends keyof DispatcherSettings>(field: K, value: DispatcherSettings[K]) => {
    setSettings((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);

    const hops = Number(settings.maxHops);
    const ttl = Number(settings.packetTtl);
    if (!Number.isInteger(hops) || hops < 1 || hops > 20) {
      setSaveError('Max hop count must be a whole number between 1 and 20.');
      return;
    }
    if (!Number.isInteger(ttl) || ttl < 1 || ttl > 60) {
      setSaveError('Packet TTL must be a whole number between 1 and 60.');
      return;
    }

    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      setSaveError('Could not save settings to this browser. Storage may be full or disabled.');
      return;
    }

    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 800);
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in">
      <div className="bg-card border border-border w-full max-w-lg rounded-lg shadow-2xl overflow-hidden font-sans">
        {/* Header */}
        <div className="px-6 py-4 border-b border-border flex items-center justify-between bg-muted/40">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-secondary/10 text-[#1F91A8]">
              <Settings className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-card-foreground">Dispatcher System Settings</h2>
              <p className="text-xs text-muted-foreground">Configure P2P BLE mesh protocol & alert triggers</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground p-1.5 rounded-md hover:bg-muted"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Content Body */}
        <form onSubmit={handleSave} className="p-6 space-y-5">
          {/* Section 1: Mesh Telemetry Protocol */}
          <div className="space-y-3">
            <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <Radio className="h-3.5 w-3.5 text-[#1F91A8]" /> BLE MESH PROTOCOL CONFIG
            </label>
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="space-y-1">
                <span className="text-foreground font-semibold">Max Hop Count</span>
                <Input
                  type="number"
                  min={1}
                  max={20}
                  value={settings.maxHops}
                  onChange={(e) => updateField('maxHops', e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <span className="text-foreground font-semibold">Time To Live (TTL)</span>
                <Input
                  type="number"
                  min={1}
                  max={60}
                  value={settings.packetTtl}
                  onChange={(e) => updateField('packetTtl', e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1">
              <span className="text-xs text-foreground font-semibold">Primary Radio Advertising Layer</span>
              <Select
                value={settings.channelMode}
                onChange={(e) => updateField('channelMode', e.target.value)}
                className="h-9 text-xs"
              >
                <option value="EXTENDED_ADV">BLE 5.0 Extended Advertising (255 Bytes Payload)</option>
                <option value="LEGACY_GATT">Legacy GATT Primary Sockets (31 Bytes Fallback)</option>
              </Select>
            </div>
          </div>

          {/* Section 2: Alerting & Regional Hub */}
          <div className="space-y-3 pt-3 border-t border-border">
            <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <BellRing className="h-3.5 w-3.5 text-[#F2A540]" /> DISPATCH ALERTS & REGION
            </label>

            <div className="space-y-1">
              <span className="text-xs text-foreground font-semibold">Assigned Region Station</span>
              <Select
                value={settings.hubRegion}
                onChange={(e) => updateField('hubRegion', e.target.value)}
                className="h-9 text-xs"
              >
                <option value="Legazpi City (Region V)">Legazpi City DRRMO HQ (Region V)</option>
                <option value="Naga City (Region V)">Naga City DRRMO Sub-Station</option>
                <option value="Sorsogon (Region V)">Sorsogon Coastal Patrol Sector</option>
              </Select>
            </div>

            <div className="p-3 bg-muted rounded-lg border border-border flex items-center justify-between text-xs">
              <div>
                <div className="font-bold text-card-foreground">Auto Emergency Siren</div>
                <div className="text-[11px] text-muted-foreground">Trigger audio alert on high priority SOS</div>
              </div>
              <input
                type="checkbox"
                checked={settings.autoSiren}
                onChange={(e) => updateField('autoSiren', e.target.checked)}
                className="h-4 w-4 rounded border-border text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer"
              />
            </div>
          </div>

          {/* Footer Buttons */}
          <div className="pt-3 border-t border-border flex flex-col gap-2">
            {saveError && (
              <div className="text-[11px] text-red-500 font-semibold">{saveError}</div>
            )}
            <div className="flex items-center justify-between">
              {savedSuccess ? (
                <Badge className="bg-emerald-500 text-white border-0 text-xs px-3 py-1 flex items-center gap-1">
                  <Check className="h-3.5 w-3.5" /> Settings Saved Successfully
                </Badge>
              ) : (
                <span className="text-[11px] text-muted-foreground font-mono">v1.0 BLE-MESH SPEC</span>
              )}
              <div className="flex gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onClose} className="h-9 text-xs">
                  Cancel
                </Button>
                <Button type="submit" size="sm" className="h-9 text-xs bg-secondary hover:bg-secondary/90 text-white font-bold px-4">
                  Save Preferences
                </Button>
              </div>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};