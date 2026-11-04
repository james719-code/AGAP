import React, { useState, useEffect } from 'react';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { MapPin, Sparkles } from 'lucide-react';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { apiService } from '@/services/api';
import type { Barangay, Municipality } from '@/types/api';

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
  const [municipalities, setMunicipalities] = useState<Municipality[]>([]);
  const [barangays, setBarangays] = useState<Barangay[]>([]);
  const [muniId, setMuniId] = useState('');
  const [brgyId, setBrgyId] = useState('');
  const [stationName, setStationName] = useState('LEGAZPI-PORT-PRECINCT');

  useEffect(() => {
    queueMicrotask(() => {
      const match = TARGET_LOCATION_PRESETS.find((p) => p.targetId === value);
      if (match) {
        setSelectedPreset(match.id);
      } else {
        setSelectedPreset('custom');
      }
    });
  }, [value]);

  useEffect(() => {
    let cancelled = false;
    apiService
      .getMunicipalities()
      .then((list) => {
        if (cancelled) return;
        setMunicipalities(list);
        setMuniId((cur) => cur || (list[0] ? String(list[0].id) : ''));
      })
      .catch(() => {
        /* geo unavailable — leave dropdowns empty */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!muniId) return;
    let cancelled = false;
    apiService
      .getBarangays(muniId)
      .then((list) => {
        if (cancelled) return;
        setBarangays(list);
        setBrgyId((cur) => (list.some((b) => String(b.id) === cur) ? cur : (list[0] ? String(list[0].id) : '')));
      })
      .catch(() => {
        /* geo unavailable — leave dropdowns empty */
      });
    return () => {
      cancelled = true;
    };
  }, [muniId]);

  useEffect(() => {
    if (selectedPreset !== 'custom' || !muniId || !brgyId) return;
    const cleanStName = stationName.trim().toUpperCase().replace(/\s+/g, '-').replace(/[^A-Z0-9-]/g, '') || 'UNIT';
    onChange(`05-05-${muniId}-${brgyId}-01::${cleanStName}`, initialLat, initialLon);
  }, [muniId, brgyId, stationName, selectedPreset]);

  const handlePresetSelect = (presetId: string) => {
    setSelectedPreset(presetId);
    if (presetId !== 'custom') {
      const preset = TARGET_LOCATION_PRESETS.find((p) => p.id === presetId);
      if (preset) {
        onChange(preset.targetId, preset.lat, preset.lon);
      }
    }
  };

  const formatted = formatTargetLocation(value);

  return (
    <div className="space-y-3">
      <div>
        <label className="text-xs font-semibold text-foreground mb-1 flex items-center justify-between">
          <span className="flex items-center gap-1.5">
            <MapPin className="h-3.5 w-3.5 text-[#1F91A8]" />
            Target Location <span aria-hidden="true">*</span>
          </span>
          <span className="text-[10px] text-muted-foreground">PSGC encoded</span>
        </label>
        <Select
          value={selectedPreset}
          onChange={(e) => handlePresetSelect(e.target.value)}
          className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
        <div className="grid grid-cols-1 gap-2 border-t border-[#E7E3DC] pt-3 dark:border-[#26292C] sm:grid-cols-3">
          <div>
            <label className="text-[10px] text-muted-foreground mb-1 block">City / Municipality</label>
            <Select
              value={muniId}
              onChange={(e) => setMuniId(e.target.value)}
              className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
            >
              {municipalities.length === 0 && <option value="">Loading municipalities...</option>}
              {municipalities.map((m) => (
                <option key={m.id} value={String(m.id)}>
                  {m.name}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <label className="text-[10px] text-muted-foreground mb-1 block">Barangay</label>
            <Select
              value={brgyId}
              onChange={(e) => setBrgyId(e.target.value)}
              className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
            >
              {barangays.length === 0 && <option value="">Loading barangays...</option>}
              {barangays.map((b) => (
                <option key={b.id} value={String(b.id)}>
                  {b.name}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <label className="text-[10px] text-muted-foreground mb-1 block">Station Callsign</label>
            <Input
              value={stationName}
              placeholder="e.g. PORT-HQ"
              onChange={(e) => setStationName(e.target.value)}
              className="h-9 rounded-md border-border bg-muted text-xs font-mono focus-visible:ring-1 focus-visible:ring-[#D15033]"
            />
          </div>
        </div>
      )}

      <div className="space-y-1 border-t border-[#E7E3DC] pt-3 text-xs dark:border-[#26292C]">
        <div className="flex items-center justify-between text-[11px]">
          <span className="text-muted-foreground font-semibold">Resolved location</span>
          <span className="text-[#1F91A8] font-bold flex items-center gap-1">
            <Sparkles className="h-3 w-3" /> Generated
          </span>
        </div>
        <div className="font-bold text-foreground">{formatted.readable}</div>
        <div className="text-[10px] text-muted-foreground font-mono truncate">{formatted.subText}</div>
      </div>
    </div>
  );
};
