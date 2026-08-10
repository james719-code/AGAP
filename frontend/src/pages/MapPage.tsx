import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { IncidentMap } from '@/components/map/IncidentMap';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Search, Filter, AlertTriangle, X } from 'lucide-react';
import type { Incident } from '@/types/api';

function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3;
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(Δφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export const MapPage: React.FC = () => {
  const { incidents } = useIncidents({}, { fetchAll: true });
  const { responders } = useResponders();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [showRespondersOnMap, setShowRespondersOnMap] = useState(true);

  const filteredIncidents = incidents.filter((inc) => {
    if (selectedCategory !== 'ALL' && inc.incident_type !== selectedCategory) return false;
    if (activeOpsOnly && ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(inc.status)) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      const targetStr = typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || '';
      const location = typeof inc.target_id === 'string'
        ? ''
        : [inc.target_id?.barangay, inc.target_id?.muni, inc.target_id?.province, inc.target_id?.region]
            .filter(Boolean)
            .join(' ');
      return (
        inc.incident_type.toLowerCase().includes(q) ||
        targetStr.toLowerCase().includes(q) ||
        location.toLowerCase().includes(q) ||
        `${inc.latitude},${inc.longitude}`.includes(q.replace(/\s/g, '')) ||
        inc.id.toString().includes(q)
      );
    }
    return true;
  });

  const selectedIsP2P = selectedIncident && (selectedIncident.channel === 'BLE_MESH' || selectedIncident.delivery_path === 'BLE_CLOUD_SHORTCUT');

  const nearestUnitInfo = React.useMemo(() => {
    if (!selectedIncident?.latitude || !selectedIncident?.longitude) return null;
    const candidates = responders.filter(
      (r) => r.current_lat && r.current_lon && (r.status === 'IDLE' || r.status === 'DISPATCHED')
    );
    if (candidates.length === 0) return null;

    let nearest = candidates[0];
    let nearestDist = haversineMeters(selectedIncident.latitude, selectedIncident.longitude, nearest.current_lat, nearest.current_lon);
    for (const r of candidates.slice(1)) {
      const d = haversineMeters(selectedIncident.latitude, selectedIncident.longitude, r.current_lat, r.current_lon);
      if (d < nearestDist) {
        nearest = r;
        nearestDist = d;
      }
    }

    const distText = nearestDist < 1000 ? `${Math.round(nearestDist)} m` : `${(nearestDist / 1000).toFixed(1)} km`;
    return { unit: nearest, distText };
  }, [selectedIncident, responders]);

  return (
    <div className="relative h-[calc(100vh-6rem)] w-full rounded-[2px] overflow-hidden border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
      <IncidentMap
        incidents={filteredIncidents}
        responders={responders}
        showIncidents={true}
        showResponders={showRespondersOnMap}
        selectedIncidentId={selectedIncident?.id}
        onSelectIncident={(inc) => setSelectedIncident(inc)}
        className="h-full w-full"
      />

      {/* Search Bar */}
      <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1100] w-full max-w-md px-4">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[#6B6864] dark:text-[#9C9994]" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && filteredIncidents.length > 0) {
                setSelectedIncident(filteredIncidents[0]);
              }
            }}
            placeholder="Search Map by Location, Target ID, or Emergency Type..."
            className="pl-9 h-10 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] shadow-[0_1px_2px_rgba(10,10,10,0.04)] focus-visible:ring-1 focus-visible:ring-[#D15033] focus-visible:border-[#D15033]"
          />
          {searchQuery.trim() && (
            <div className="absolute top-full mt-1 w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] shadow-md max-h-48 overflow-y-auto">
              {filteredIncidents.length === 0 ? (
                <div className="px-3 py-2 text-xs text-[#6B6864] dark:text-[#9C9994]">No matching incidents or locations</div>
              ) : filteredIncidents.slice(0, 6).map((incident) => {
                const location = typeof incident.target_id === 'string'
                  ? incident.target_id
                  : [incident.target_id?.barangay, incident.target_id?.muni].filter(Boolean).join(', ') || incident.target_id?.raw;
                return (
                  <button
                    type="button"
                    key={incident.id}
                    onClick={() => { setSelectedIncident(incident); setSearchQuery(''); }}
                    className="block w-full border-b border-[#E7E3DC] dark:border-[#26292C] px-3 py-2 text-left text-xs hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] last:border-0"
                  >
                    <span className="font-semibold">#{incident.id} {incident.incident_type}</span>
                    <span className="block truncate text-[#6B6864] dark:text-[#9C9994]">{location || 'Location unavailable'}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Filters Card */}
      <div className="absolute top-4 right-4 z-[1100] w-80">
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-4 space-y-4">
          <div className="flex items-center justify-between pb-2 border-b border-[#E7E3DC] dark:border-[#26292C]">
            <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994] flex items-center gap-1.5">
              <Filter className="h-3.5 w-3.5 text-[#1F91A8]" /> DATA FILTERS
            </span>
            <Badge variant="outline" className="text-[10px] font-medium text-[#1F91A8] border-[#1F91A8]/30">
              {filteredIncidents.length} ALERTS • {responders.length} UNITS
            </Badge>
          </div>

          {/* Category Pills */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
              CATEGORY
            </label>
            <div className="flex flex-wrap gap-1.5">
              {['ALL', 'CRIME', 'MEDICAL', 'ACCIDENT', 'DISASTER', 'FIRE'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-2.5 py-1 text-[11px] font-semibold rounded-[2px] transition-all ${
                    selectedCategory === cat
                      ? 'bg-[#D15033] text-white shadow-[0_1px_2px_rgba(10,10,10,0.04)]'
                      : 'bg-[#F7F5F2] dark:bg-[#181C1D] text-[#5C5A56] dark:text-[#B9B6B0] hover:bg-[#E7E3DC] dark:hover:bg-[#26292C]'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* Display Options */}
          <div className="space-y-2 pt-1 border-t border-[#E7E3DC] dark:border-[#26292C]">
            <label className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
              DISPLAY OPTIONS
            </label>
            <div className="space-y-1.5 text-sm text-[#0A0A0A] dark:text-[#F2F0EC]">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={activeOpsOnly}
                  onChange={(e) => setActiveOpsOnly(e.target.checked)}
                  className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                />
                <span className="text-xs font-medium">Active Operations</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={showRespondersOnMap}
                  onChange={(e) => setShowRespondersOnMap(e.target.checked)}
                  className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                />
                <span className="text-xs font-medium">Show Responders On Duty</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      {/* Selected Incident Details Card */}
      {selectedIncident && (
        <div className="absolute bottom-6 left-6 z-[1100] max-w-sm w-full">
          <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-[#D15033] flex items-center gap-1.5">
                <AlertTriangle className="h-4 w-4" /> Incident #{selectedIncident.id}
              </span>
              <Badge variant="outline" className="text-[10px] font-medium text-[#F2A540] border-[#F2A540]/40">
                {selectedIncident.status}
              </Badge>
            </div>

            <div className="flex items-center justify-between text-[11px] font-mono">
              <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Alert Source:</span>
              <span className={`px-2 py-0.5 rounded-[2px] font-bold text-[10px] ${
                selectedIsP2P
                  ? 'bg-[#F2A540]/10 text-[#F2A540] border border-[#F2A540]/30'
                  : 'bg-[#1F91A8]/10 text-[#1F91A8] border border-[#1F91A8]/30'
              }`}>
                {selectedIsP2P ? 'P2P RECEIVED' : 'DATABASE ONLINE'}
              </span>
            </div>

            {nearestUnitInfo && (
              <div className="flex items-center justify-between text-[11px] font-mono bg-[#F7F5F2] dark:bg-[#181C1D] p-1.5 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C]">
                <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Nearest Unit ({nearestUnitInfo.unit.unit_callsign}):</span>
                <span className="font-bold text-[#1F91A8]">{nearestUnitInfo.distText}</span>
              </div>
            )}

            <div className="text-xs text-[#0A0A0A] dark:text-white font-medium">
              Target ID: {typeof selectedIncident.target_id === 'string' ? selectedIncident.target_id : selectedIncident.target_id?.raw}
            </div>
            <div className="flex items-center justify-between text-[11px] font-medium text-[#5C5A56] dark:text-[#B9B6B0] pt-1 border-t border-[#E7E3DC] dark:border-[#26292C]">
              <span>Hops: {selectedIncident.hop_count}</span>
              <span>Channel: {selectedIncident.channel}</span>
            </div>

            <button
              onClick={() => setSelectedIncident(null)}
              className="w-full py-1.5 text-xs text-[#6B6864] dark:text-[#9C9994] hover:text-[#0A0A0A] dark:hover:text-white bg-[#F7F5F2] dark:bg-[#181C1D] rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] transition-colors flex items-center justify-center gap-1"
            >
              <X className="h-3 w-3" /> Close Details
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
