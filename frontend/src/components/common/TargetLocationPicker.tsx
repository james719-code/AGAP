import React, { useState, useEffect } from 'react';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { MapPin, Sparkles } from 'lucide-react';
import { formatTargetLocation } from '@/lib/targetIdFormatter';

export interface LocationPreset {
  id: string;
  label: string;
  targetId: string;
  lat: number;
  lon: number;
}

export const TARGET_LOCATION_PRESETS: LocationPreset[] = [
  {
    id: 'legazpi-port',
    label: 'Legazpi Port Area Precinct (Brgy 44, Legazpi City)',
    targetId: '05-05-12-04-01::LEGAZPI-PORT-PRECINCT',
    lat: 13.1458,
    lon: 123.7542,
  },
  {
    id: 'daraga-highway',
    label: 'Daraga Highway Patrol Station (Daraga District)',
    targetId: '05-05-12-02-09::HIGHWAY-PATROL-1',
    lat: 13.1492,
    lon: 123.7185,
  },
  {
    id: 'penaranda-park',
    label: 'Peñaranda Park Command Base (Brgy 16, Legazpi City)',
    targetId: '05-05-12-01-02::PENARANDA-STATION',
    lat: 13.1398,
    lon: 123.7445,
  },
  {
    id: 'mayon-rescue',
    label: 'Mayon Rescue Outpost (Mayon 6km Danger Zone)',
    targetId: '05-05-12-08-08::MAYON-RESCUE-BASE',
    lat: 13.2548,
    lon: 123.6861,
  },
  {
    id: 'coast-guard',
    label: 'Coast Guard Station (Embarcadero, Legazpi City)',
    targetId: '05-05-12-01-44::COAST-GUARD-LEG',
    lat: 13.1425,
    lon: 123.7510,
  },
];

const MUNICIPALITIES = [
  { code: '12', name: 'Legazpi City' },
  { code: '02', name: 'Daraga' },
  { code: '03', name: 'Tabaco City' },
  { code: '08', name: 'Mayon Sector' },
];

const BARANGAYS = [
  { code: '01', name: 'Central Poblacion (Brgy 1)' },
  { code: '02', name: 'Peñaranda Park Zone (Brgy 16)' },
  { code: '03', name: 'San Roque' },
  { code: '04', name: 'Port Area / Embarcadero (Brgy 44)' },
  { code: '05', name: 'Brgy 5 (Rizal Street)' },
  { code: '08', name: 'Mayon 6km Danger Zone' },
  { code: '09', name: 'Daraga Station District' },
];

interface TargetLocationPickerProps {
  value: string;
  onChange: (targetId: string, lat?: number, lon?: number) => void;
  initialLat?: number;
  initialLon?: number;
}

export const TargetLocationPicker: React.FC<TargetLocationPickerProps> = ({
  value,
  onChange,
  initialLat,
  initialLon,
}) => {
  const [selectedPreset, setSelectedPreset] = useState<string>('custom');
  const [muniCode, setMuniCode] = useState('12');
  const [brgyCode, setBrgyCode] = useState('04');
  const [stationName, setStationName] = useState('LEGAZPI-PORT-PRECINCT');

  // Match initial value to preset if possible
  useEffect(() => {
    const match = TARGET_LOCATION_PRESETS.find((p) => p.targetId === value);
    if (match) {
      setSelectedPreset(match.id);
    } else {
      setSelectedPreset('custom');
    }
  }, [value]);

  const handlePresetSelect = (presetId: string) => {
    setSelectedPreset(presetId);
    if (presetId === 'custom') {
      const customId = `05-05-${muniCode}-${brgyCode}-01::${stationName || 'STATION-01'}`;
      onChange(customId, initialLat, initialLon);
    } else {
      const preset = TARGET_LOCATION_PRESETS.find((p) => p.id === presetId);
      if (preset) {
        onChange(preset.targetId, preset.lat, preset.lon);
      }
    }
  };

  const handleCustomChange = (muni: string, brgy: string, stName: string) => {
    setMuniCode(muni);
    setBrgyCode(brgy);
    setStationName(stName);
    const cleanStName = stName.toUpperCase().replace(/\s+/g, '-').replace(/[^A-Z0-9-]/g, '') || 'UNIT';
    const generatedId = `05-05-${muni}-${brgy}-01::${cleanStName}`;
    onChange(generatedId);
  };

  const formatted = formatTargetLocation(value);

  return (
    <div className="space-y-3 bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
      <div>
        <label className="text-xs font-semibold text-slate-300 mb-1 flex items-center justify-between">
          <span className="flex items-center gap-1.5">
            <MapPin className="h-3.5 w-3.5 text-emerald-400" />
            Target Location Station *
          </span>
          <span className="text-[10px] text-slate-400 font-mono">Auto-encodes PSGC</span>
        </label>
        <Select
          value={selectedPreset}
          onChange={(e) => handlePresetSelect(e.target.value)}
          className="bg-slate-900 border-slate-700 text-xs"
        >
          {TARGET_LOCATION_PRESETS.map((p) => (
            <option key={p.id} value={p.id}>
              {p.label}
            </option>
          ))}
          <option value="custom">⚡ Custom Location Builder...</option>
        </Select>
      </div>

      {selectedPreset === 'custom' && (
        <div className="grid grid-cols-3 gap-2 pt-1 border-t border-slate-800/80">
          <div>
            <label className="text-[10px] text-slate-400 mb-1 block">City / Municipality</label>
            <Select
              value={muniCode}
              onChange={(e) => handleCustomChange(e.target.value, brgyCode, stationName)}
              className="bg-slate-900 border-slate-700 text-xs"
            >
              {MUNICIPALITIES.map((m) => (
                <option key={m.code} value={m.code}>
                  {m.name}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <label className="text-[10px] text-slate-400 mb-1 block">Barangay Zone</label>
            <Select
              value={brgyCode}
              onChange={(e) => handleCustomChange(muniCode, e.target.value, stationName)}
              className="bg-slate-900 border-slate-700 text-xs"
            >
              {BARANGAYS.map((b) => (
                <option key={b.code} value={b.code}>
                  {b.name}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <label className="text-[10px] text-slate-400 mb-1 block">Station Callsign</label>
            <Input
              value={stationName}
              placeholder="e.g. PORT-HQ"
              onChange={(e) => handleCustomChange(muniCode, brgyCode, e.target.value)}
              className="bg-slate-900 border-slate-700 text-xs font-mono"
            />
          </div>
        </div>
      )}

      {/* Generated Target Spatial ID Preview Box */}
      <div className="p-2.5 bg-slate-900 rounded-lg border border-emerald-500/30 text-xs space-y-1">
        <div className="flex items-center justify-between text-[11px]">
          <span className="text-slate-400 font-semibold">Resolved Spatial Address:</span>
          <span className="text-emerald-400 font-bold flex items-center gap-1">
            <Sparkles className="h-3 w-3" /> Auto-Generated
          </span>
        </div>
        <div className="font-bold text-slate-100">{formatted.readable}</div>
        <div className="text-[10px] text-slate-400 font-mono truncate">{formatted.subText}</div>
      </div>
    </div>
  );
};
