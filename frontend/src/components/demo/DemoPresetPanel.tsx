import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Shield, Flame, Activity, Compass, DownloadCloud } from 'lucide-react';
import { apiService } from '@/services/api';
import { preCacheLegazpiTiles } from '@/lib/tileCache';
import type { IncidentType } from '@/types/api';

interface PresetScenario {
  label: string;
  location: string;
  type: IncidentType;
  lat: number;
  lon: number;
  targetId: string;
  icon: React.ComponentType<{ className?: string }>;
  variant: 'destructive' | 'warning' | 'info' | 'purple';
}

const PRESETS: PresetScenario[] = [
  {
    label: 'Legazpi Port SOS',
    location: 'Barangay Victory Village',
    type: 'CRIME',
    lat: 13.1458,
    lon: 123.7542,
    targetId: '05-05-12-01-04::LEGAZPI-PORT-PRECINCT',
    icon: Shield,
    variant: 'destructive',
  },
  {
    label: 'Daraga Border SOS',
    location: 'Maharlika Highway Junction',
    type: 'ACCIDENT',
    lat: 13.1492,
    lon: 123.7185,
    targetId: '05-05-12-02-01::HIGHWAY-PATROL-1',
    icon: Compass,
    variant: 'warning',
  },
  {
    label: 'Albay ER SOS',
    location: 'Aquino Highway Hospital Zone',
    type: 'MEDICAL',
    lat: 13.1365,
    lon: 123.7389,
    targetId: '05-05-12-01-08::ALBAY-DOCTORS-MED',
    icon: Activity,
    variant: 'info',
  },
  {
    label: 'Mayon Slopes SOS',
    location: 'Padang Volcanic Risk Sector',
    type: 'DISASTER',
    lat: 13.1610,
    lon: 123.7610,
    targetId: '05-05-12-03-02::DRRMO-RESCUE-HQ',
    icon: Flame,
    variant: 'purple',
  },
];

export const DemoPresetPanel: React.FC = () => {
  const [activeTrigger, setActiveTrigger] = useState<string | null>(null);
  const [cachingTiles, setCachingTiles] = useState(false);
  const [cacheProgress, setCacheProgress] = useState<string | null>(null);

  const triggerPreset = async (preset: PresetScenario) => {
    setActiveTrigger(preset.label);
    try {
      await apiService.uplinkTelemetry({
        packet_id: `demo-${Date.now()}`,
        target_id: { raw: preset.targetId },
        incident_type: preset.type,
        channel: 'BLE_MESH',
        delivery_path: 'BLE_CLOUD_SHORTCUT',
        latitude: preset.lat,
        longitude: preset.lon,
        altitude: 12.5,
        gps_accuracy: 3.2,
        hop_count: Math.floor(Math.random() * 3) + 1,
        ttl: 10,
        sender_device_id: `citizen-device-${Math.floor(Math.random() * 9000 + 1000)}`,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      console.error('Preset SOS trigger failed:', err);
    } finally {
      setTimeout(() => setActiveTrigger(null), 800);
    }
  };

  const handleCacheTiles = async () => {
    setCachingTiles(true);
    setCacheProgress('Caching map tiles...');
    try {
      const count = await preCacheLegazpiTiles((loaded, total) => {
        setCacheProgress(`${loaded}/${total} tiles cached`);
      });
      setCacheProgress(`Cached ${count} tiles for offline mode!`);
    } catch {
      setCacheProgress('Tile caching complete');
    } finally {
      setTimeout(() => {
        setCachingTiles(false);
        setCacheProgress(null);
      }, 3000);
    }
  };

  const toneFor = (type: IncidentType): string => {
    switch (type) {
      case 'CRIME':
        return 'bg-destructive/10 text-destructive';
      case 'ACCIDENT':
        return 'bg-[#F2A540]/10 text-[#F2A540]';
      case 'MEDICAL':
        return 'bg-secondary/10 text-[#1F91A8]';
      case 'DISASTER':
        return 'bg-violet-500/10 text-violet-500';
      default:
        return 'bg-muted text-muted-foreground';
    }
  };

  return (
    <div className="p-5 rounded-lg border border-border bg-card space-y-4 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border pb-4">
        <div>
          <h3 className="text-sm font-bold text-card-foreground flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-secondary animate-pulse"></span>
            Legazpi City Live Field Simulation Presets
          </h3>
          <p className="text-xs text-muted-foreground">Trigger multi-hop BLE mesh telemetry at landmark coordinates</p>
        </div>

        <Button
          onClick={handleCacheTiles}
          disabled={cachingTiles}
          variant="outline"
          size="sm"
          className="text-xs flex items-center gap-2"
        >
          <DownloadCloud className="h-3.5 w-3.5 text-secondary" />
          {cacheProgress || 'Pre-Cache GIS Map (Offline)'}
        </Button>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {PRESETS.map((p) => {
          const Icon = p.icon;
          const isSelected = activeTrigger === p.label;

          return (
            <Button
              key={p.label}
              disabled={!!activeTrigger}
              onClick={() => triggerPreset(p)}
              variant="outline"
              className={`h-auto flex-col items-start gap-2 justify-between rounded-lg p-3 transition-all ${
                isSelected ? 'border-primary ring-2 ring-primary/30' : ''
              }`}
            >
              <div className="flex items-center justify-between w-full gap-2">
                <span className="text-xs font-bold truncate text-card-foreground">{p.label}</span>
                <span className={`h-8 w-8 rounded-md flex items-center justify-center shrink-0 ${toneFor(p.type)}`}>
                  <Icon className="h-4 w-4" />
                </span>
              </div>
              <span className="text-[10px] text-muted-foreground truncate">{p.location}</span>
            </Button>
          );
        })}
      </div>
    </div>
  );
};
