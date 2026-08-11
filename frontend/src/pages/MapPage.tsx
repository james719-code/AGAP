import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { IncidentMap } from '@/components/map/IncidentMap';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Search, Filter, AlertTriangle, X, Radio, RefreshCw } from 'lucide-react';
import type { Incident } from '@/types/api';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { apiService } from '@/services/api';

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
  const {
    incidents,
    loading: incidentsLoading,
    error: incidentsError,
    refetch: refetchIncidents,
    isWsConnected,
  } = useIncidents({}, { fetchAll: true });
  const { responders, loading: respondersLoading, error: respondersError, refetch: refetchResponders } = useResponders();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [showRespondersOnMap, setShowRespondersOnMap] = useState(true);
  const [selectedUnitId, setSelectedUnitId] = useState('');
  const [dispatching, setDispatching] = useState(false);
  const [dispatchFeedback, setDispatchFeedback] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const incidentCategories = React.useMemo(
    () => Array.from(new Set(incidents.map((incident) => incident.incident_type))).sort(),
    [incidents]
  );

  const filteredIncidents = incidents.filter((inc) => {
    if (selectedCategory !== 'ALL' && inc.incident_type !== selectedCategory) return false;
    if (activeOpsOnly && ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(inc.status)) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      const location = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude);
      return (
        inc.incident_type.toLowerCase().includes(q) ||
        location.raw.toLowerCase().includes(q) ||
        location.readable.toLowerCase().includes(q) ||
        location.subText.toLowerCase().includes(q) ||
        `${inc.latitude},${inc.longitude}`.includes(q.replace(/\s/g, '')) ||
        inc.id.toString().includes(q)
      );
    }
    return true;
  });

  React.useEffect(() => {
    if (selectedIncident && !filteredIncidents.some((incident) => incident.id === selectedIncident.id)) {
      setSelectedIncident(null);
    }
  }, [filteredIncidents, selectedIncident]);

  const selectedIsP2P = selectedIncident && (selectedIncident.channel === 'BLE_MESH' || selectedIncident.delivery_path === 'BLE_CLOUD_SHORTCUT');

  const availableResponders = React.useMemo(
    () => responders.filter((responder) => responder.status === 'IDLE'),
    [responders]
  );

  const nearestUnitInfo = React.useMemo(() => {
    if (!selectedIncident?.latitude || !selectedIncident?.longitude) return null;
    const candidates = responders.filter(
      (r) => r.current_lat && r.current_lon && r.status === 'IDLE'
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

  React.useEffect(() => {
    setSelectedUnitId(nearestUnitInfo ? String(nearestUnitInfo.unit.id) : '');
    setDispatchFeedback(null);
  }, [nearestUnitInfo, selectedIncident?.id]);

  const handleDispatch = async () => {
    if (!selectedIncident || !selectedUnitId || dispatching) return;
    setDispatching(true);
    setDispatchFeedback(null);
    try {
      await apiService.createDispatch(selectedIncident.id, Number(selectedUnitId));
      setDispatchFeedback('Responder unit dispatched successfully.');
      await refetchResponders();
    } catch (error) {
      setDispatchFeedback(error instanceof Error ? error.message : 'Failed to dispatch responder unit');
    } finally {
      setDispatching(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await Promise.all([refetchIncidents(), refetchResponders()]);
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <span className="page-eyebrow">Command Dispatch</span>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white">
            Live GIS Map
          </h2>
          <p className="mt-1 text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Locate active incidents, monitor responder units, and dispatch the nearest available team.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className={`h-9 gap-2 px-3 text-[10px] font-semibold ${isWsConnected ? 'border-emerald-500/30 text-emerald-500' : 'text-[#5C5A56] dark:text-[#B9B6B0]'}`}>
            <Radio className={`h-3.5 w-3.5 ${isWsConnected ? 'animate-pulse' : ''}`} />
            {isWsConnected ? 'LIVE UPDATES' : 'RECONNECTING'}
          </Badge>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={refreshing || incidentsLoading || respondersLoading}
            className="h-9 gap-2"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} /> Refresh map
          </Button>
        </div>
      </div>

      {(incidentsError || respondersError) && (
        <div className="flex items-center justify-between gap-3 rounded-[2px] border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-500">
          <span>{incidentsError || respondersError}</span>
          <button type="button" onClick={handleRefresh} className="shrink-0 font-semibold underline underline-offset-2">Retry</button>
        </div>
      )}

      <div className="relative isolate h-[calc(100vh-12rem)] min-h-[560px] w-full overflow-hidden rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
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
      <div className="absolute left-1/2 top-4 z-[1100] flex w-[calc(100%-2rem)] max-w-sm -translate-x-1/2 gap-2">
        <div className="relative flex-1">
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
            className="h-10 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] pl-9 text-[#0A0A0A] dark:text-white shadow-md focus-visible:border-[#D15033] focus-visible:ring-1 focus-visible:ring-[#D15033]"
          />
          {searchQuery.trim() && (
            <div className="absolute top-full mt-1 max-h-48 w-full overflow-y-auto rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg">
              {filteredIncidents.length === 0 ? (
                <div className="px-3 py-2 text-xs text-[#6B6864] dark:text-[#9C9994]">No matching incidents or locations</div>
              ) : filteredIncidents.slice(0, 6).map((incident) => {
                const location = formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable;
                return (
                  <button
                    type="button"
                    key={incident.id}
                    onClick={() => { setSelectedIncident(incident); setSearchQuery(''); }}
                    className="block w-full border-b border-[#E7E3DC] dark:border-[#26292C] px-3 py-2 text-left text-xs last:border-0 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    <span className="font-semibold">#{incident.id} {incident.incident_type}</span>
                    <span className="block truncate text-[#6B6864] dark:text-[#9C9994]">{location || 'Location unavailable'}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setFiltersOpen((open) => !open)}
          className="h-10 w-10 shrink-0 rounded-[2px] bg-white dark:bg-[#161A1D] p-0 shadow-md md:hidden"
          aria-label="Toggle map filters"
          aria-expanded={filtersOpen}
        >
          <Filter className="h-4 w-4" />
        </Button>
      </div>

      {/* Filters Card */}
      <div className={`absolute left-4 right-4 top-16 z-[1100] md:left-auto md:top-4 md:w-80 ${filtersOpen ? 'block' : 'hidden md:block'}`}>
        <div className="space-y-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white/95 dark:bg-[#161A1D]/95 p-4 shadow-lg backdrop-blur">
          <div className="flex items-center justify-between border-b border-[#E7E3DC] dark:border-[#26292C] pb-2">
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
              {['ALL', ...incidentCategories].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`rounded-[2px] px-2.5 py-1 text-[11px] font-semibold transition-all ${
                    selectedCategory === cat
                      ? 'bg-[#D15033] text-white shadow-[0_1px_2px_rgba(10,10,10,0.04)]'
                      : 'bg-[#F7F5F2] dark:bg-[#181C1D] text-[#5C5A56] dark:text-[#B9B6B0] hover:bg-[#E7E3DC] dark:hover:bg-[#26292C] hover:text-[#0A0A0A] dark:hover:text-white'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* Display Options */}
          <div className="space-y-2 border-t border-[#E7E3DC] dark:border-[#26292C] pt-1">
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
                <span className="text-xs font-medium">Show Responder Units</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      {/* Selected Incident Details Card */}
      {selectedIncident && (
        <div className="absolute bottom-4 left-4 right-4 z-[1100] sm:right-auto sm:w-full sm:max-w-sm">
          <div className="max-h-[calc(100vh-15rem)] space-y-2.5 overflow-y-auto rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white/95 dark:bg-[#161A1D]/95 p-4 shadow-lg backdrop-blur">
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
              <div className="flex items-center justify-between rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] p-1.5 font-mono text-[11px]">
                <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Nearest Unit ({nearestUnitInfo.unit.unit_callsign}):</span>
                <span className="font-bold text-[#1F91A8]">{nearestUnitInfo.distText}</span>
              </div>
            )}

            <div className="text-xs text-[#0A0A0A] dark:text-white font-medium">
              {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
            </div>
            <div className="text-[10px] text-[#5C5A56] dark:text-[#B9B6B0] font-mono">
              {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}
            </div>
            <div className="flex items-center justify-between border-t border-[#E7E3DC] dark:border-[#26292C] pt-1 text-[11px] font-medium text-[#5C5A56] dark:text-[#B9B6B0]">
              <span>Hops: {selectedIncident.hop_count}</span>
              <span>Channel: {selectedIncident.channel}</span>
            </div>

            <div className="space-y-2 border-t border-[#E7E3DC] dark:border-[#26292C] pt-2">
              <label htmlFor="map-dispatch-unit" className="text-[10px] font-bold uppercase tracking-[0.12em] text-[#6B6864] dark:text-[#9C9994]">
                Dispatch responder
              </label>
              <select
                id="map-dispatch-unit"
                value={selectedUnitId}
                onChange={(event) => setSelectedUnitId(event.target.value)}
                disabled={dispatching || availableResponders.length === 0}
                className="h-9 w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] px-2 text-xs text-[#0A0A0A] dark:text-white outline-none focus:border-[#1F91A8]"
              >
                <option value="">{availableResponders.length === 0 ? 'No available units' : 'Select available unit'}</option>
                {availableResponders.map((responder) => (
                  <option key={responder.id} value={responder.id}>
                    {responder.unit_callsign}{nearestUnitInfo?.unit.id === responder.id ? ` · nearest (${nearestUnitInfo.distText})` : ''}
                  </option>
                ))}
              </select>
              <Button
                type="button"
                size="sm"
                onClick={handleDispatch}
                disabled={!selectedUnitId || dispatching}
                className="h-8 w-full bg-[#1F91A8] text-xs text-white hover:bg-[#187588]"
              >
                {dispatching ? 'Dispatching...' : 'Dispatch selected unit'}
              </Button>
              {dispatchFeedback && (
                <p className={`text-[10px] ${dispatchFeedback.includes('successfully') ? 'text-emerald-500' : 'text-red-500'}`}>
                  {dispatchFeedback}
                </p>
              )}
            </div>

            <button
              onClick={() => setSelectedIncident(null)}
              className="flex w-full items-center justify-center gap-1 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] py-1.5 text-xs text-[#5C5A56] dark:text-[#B9B6B0] transition-colors hover:text-[#0A0A0A] dark:hover:text-white"
            >
              <X className="h-3 w-3" /> Close Details
            </button>
          </div>
        </div>
      )}
      </div>
    </div>
  );
};