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
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950/60 backdrop-blur-sm p-4 animate-in fade-in">
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden font-sans">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-slate-50/50 dark:bg-slate-950/50">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-teal-500/10 text-teal-600 dark:text-teal-400">
              <Settings className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">Dispatcher System Settings</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">Configure P2P BLE mesh protocol & alert triggers</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Content Body */}
        <form onSubmit={handleSave} className="p-6 space-y-5">
          {/* Section 1: Mesh Telemetry Protocol */}
          <div className="space-y-3">
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Radio className="h-3.5 w-3.5 text-teal-500" /> BLE MESH PROTOCOL CONFIG
            </label>
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="space-y-1">
                <span className="text-slate-700 dark:text-slate-300 font-semibold">Max Hop Count</span>
                <Input
                  type="number"
                  value={maxHops}
                  onChange={(e) => setMaxHops(e.target.value)}
                  className="h-9 text-xs bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800"
                />
              </div>
              <div className="space-y-1">
                <span className="text-slate-700 dark:text-slate-300 font-semibold">Time To Live (TTL)</span>
                <Input
                  type="number"
                  value={packetTtl}
                  onChange={(e) => setPacketTtl(e.target.value)}
                  className="h-9 text-xs bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800"
                />
              </div>
            </div>

            <div className="space-y-1">
              <span className="text-xs text-slate-700 dark:text-slate-300 font-semibold">Primary Radio Advertising Layer</span>
              <Select
                value={channelMode}
                onChange={(e) => setChannelMode(e.target.value)}
                className="h-9 text-xs bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800"
              >
                <option value="EXTENDED_ADV">BLE 5.0 Extended Advertising (255 Bytes Payload)</option>
                <option value="LEGACY_GATT">Legacy GATT Primary Sockets (31 Bytes Fallback)</option>
              </Select>
            </div>
          </div>

          {/* Section 2: Alerting & Regional Hub */}
          <div className="space-y-3 pt-3 border-t border-slate-200 dark:border-slate-800">
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <BellRing className="h-3.5 w-3.5 text-amber-500" /> DISPATCH ALERTS & REGION
            </label>

            <div className="space-y-1">
              <span className="text-xs text-slate-700 dark:text-slate-300 font-semibold">Assigned Region Station</span>
              <Select
                value={hubRegion}
                onChange={(e) => setHubRegion(e.target.value)}
                className="h-9 text-xs bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800"
              >
                <option value="Legazpi City (Region V)">Legazpi City DRRMO HQ (Region V)</option>
                <option value="Naga City (Region V)">Naga City DRRMO Sub-Station</option>
                <option value="Sorsogon (Region V)">Sorsogon Coastal Patrol Sector</option>
              </Select>
            </div>

            <div className="p-3 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs">
              <div>
                <div className="font-bold text-slate-900 dark:text-slate-100">Auto Emergency Siren</div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400">Trigger audio alert on high priority SOS</div>
              </div>
              <input
                type="checkbox"
                checked={autoSiren}
                onChange={(e) => setAutoSiren(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 cursor-pointer"
              />
            </div>
          </div>

          {/* Footer Buttons */}
          <div className="pt-3 border-t border-slate-200 dark:border-slate-800 flex items-center justify-between">
            {savedSuccess ? (
              <Badge className="bg-emerald-500 text-white border-0 text-xs px-3 py-1 flex items-center gap-1">
                <Check className="h-3.5 w-3.5" /> Settings Saved Successfully
              </Badge>
            ) : (
              <span className="text-[11px] text-slate-400 font-mono">v1.0 BLE-MESH SPEC</span>
            )}
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={onClose} className="h-9 text-xs">
                Cancel
              </Button>
              <Button type="submit" size="sm" className="h-9 text-xs bg-teal-600 hover:bg-teal-500 text-white font-bold px-4">
                Save Preferences
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};
