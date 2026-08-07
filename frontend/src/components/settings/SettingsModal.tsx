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

export const SettingsModal: React.FC<SettingsModalProps> = ({ isOpen, onClose }) => {
  const [maxHops, setMaxHops] = useState('5');
  const [packetTtl, setPacketTtl] = useState('10');
  const [channelMode, setChannelMode] = useState('EXTENDED_ADV');
  const [autoSiren, setAutoSiren] = useState(true);
  const [hubRegion, setHubRegion] = useState('Legazpi City (Region V)');
  const [savedSuccess, setSavedSuccess] = useState(false);

  if (!isOpen) return null;

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
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
                  value={maxHops}
                  onChange={(e) => setMaxHops(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <span className="text-foreground font-semibold">Time To Live (TTL)</span>
                <Input
                  type="number"
                  value={packetTtl}
                  onChange={(e) => setPacketTtl(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1">
              <span className="text-xs text-foreground font-semibold">Primary Radio Advertising Layer</span>
              <Select
                value={channelMode}
                onChange={(e) => setChannelMode(e.target.value)}
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
                value={hubRegion}
                onChange={(e) => setHubRegion(e.target.value)}
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
                checked={autoSiren}
                onChange={(e) => setAutoSiren(e.target.checked)}
                className="h-4 w-4 rounded border-border text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer"
              />
            </div>
          </div>

          {/* Footer Buttons */}
          <div className="pt-3 border-t border-border flex items-center justify-between">
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
        </form>
      </div>
    </div>
  );
};
