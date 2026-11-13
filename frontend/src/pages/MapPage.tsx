import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { IncidentMap } from '@/components/map/IncidentMap';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Search, Filter, AlertTriangle, X, Radio, RefreshCw } from 'lucide-react';
import type { Incident } from '@/types/api';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { apiService } from '@/services/api';
import { usesHostedApi } from '@/services/backend';
import { eligibleResponders, isIncidentDispatchable, requiredAgencyType } from '@/lib/dispatchEligibility';

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
  } = useIncidents({ limit: 200, offset: 0 });
  const { responders, loading: respondersLoading, error: respondersError, refetch: refetchResponders } = useResponders();
  const { agencies, loading: agenciesLoading, error: agenciesError } = useAgencies();
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
  const deferredSearchQuery = React.useDeferredValue(searchQuery);

  const incidentCategories = React.useMemo(
    () => Array.from(new Set(incidents.map((incident) => incident.incident_type))).sort(),
    [incidents]
  );

  const filteredIncidents = React.useMemo(() => {
    const q = deferredSearchQuery.trim().toLowerCase();
    return incidents.filter((inc) => {
      if (selectedCategory !== 'ALL' && inc.incident_type !== selectedCategory) return false;
      if (activeOpsOnly && ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(inc.status)) return false;
      if (!q) return true;
      const location = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude);
      return (
        inc.incident_type.toLowerCase().includes(q) ||
        location.raw.toLowerCase().includes(q) ||
        location.readable.toLowerCase().includes(q) ||
        location.subText.toLowerCase().includes(q) ||
        `${inc.latitude},${inc.longitude}`.includes(q.replace(/\s/g, '')) ||
        inc.id.toString().includes(q)
      );
    });
  }, [activeOpsOnly, deferredSearchQuery, incidents, selectedCategory]);

  React.useEffect(() => {
    if (selectedIncident && !filteredIncidents.some((incident) => incident.id === selectedIncident.id)) {
      queueMicrotask(() => setSelectedIncident(null));
    }
  }, [filteredIncidents, selectedIncident]);

  const selectedIsP2P = selectedIncident && (selectedIncident.channel === 'BLE_MESH' || selectedIncident.delivery_path === 'BLE_CLOUD_SHORTCUT');

  const availableResponders = React.useMemo(
    () => selectedIncident ? eligibleResponders(selectedIncident, responders, agencies) : [],
    [agencies, responders, selectedIncident]
  );

  const nearestUnitInfo = React.useMemo(() => {
    if (!selectedIncident?.latitude || !selectedIncident?.longitude) return null;
    const candidates = availableResponders.filter(
      (r) => Number.isFinite(r.current_lat) && Number.isFinite(r.current_lon)
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
  }, [availableResponders, selectedIncident]);

  React.useEffect(() => {
    if (!selectedIncident) return;
    const current = incidents.find((incident) => incident.id === selectedIncident.id);
    if (current && current !== selectedIncident) queueMicrotask(() => setSelectedIncident(current));
  }, [incidents, selectedIncident]);

  React.useEffect(() => {
    queueMicrotask(() => {
      setSelectedUnitId(nearestUnitInfo ? String(nearestUnitInfo.unit.id) : '');
      setDispatchFeedback(null);
    });
  }, [nearestUnitInfo, selectedIncident?.id]);

  const handleDispatch = async () => {
    if (!selectedIncident || !selectedUnitId || dispatching || !isIncidentDispatchable(selectedIncident)) return;
    setDispatching(true);
    setDispatchFeedback(null);
    try {
      await apiService.createDispatch(selectedIncident.id, Number(selectedUnitId));
      setDispatchFeedback('Responder unit dispatched successfully.');
      await Promise.all([refetchIncidents(), refetchResponders()]);
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
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Dispatch</span>
          <h1 className="page-title">Live Map</h1>
          <p className="page-description">
            Locate incidents, monitor responders, and dispatch the nearest available unit.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className={`h-9 gap-2 px-3 text-[10px] font-semibold ${isWsConnected ? 'border-emerald-500/30 text-emerald-700 dark:text-emerald-400' : 'text-muted-foreground'}`}>
            <Radio className={`h-3.5 w-3.5 ${isWsConnected ? 'text-emerald-600 dark:text-emerald-400' : ''}`} />
            {usesHostedApi ? 'Auto-refresh' : isWsConnected ? 'Live' : 'Reconnecting'}
          </Badge>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={refreshing || incidentsLoading || respondersLoading}
            className="h-9 gap-2"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} /> Refresh
          </Button>
        </div>
      </div>

      {(incidentsError || respondersError || agenciesError) && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-500">
          <span>{incidentsError || respondersError || agenciesError}</span>
          <button type="button" onClick={handleRefresh} className="shrink-0 font-semibold underline underline-offset-2">Retry</button>
        </div>
      )}

      {/* Search Bar */}
      <div className="flex items-start gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && filteredIncidents.length > 0) {
                setSelectedIncident(filteredIncidents[0]);
                setSearchQuery('');
              }
            }}
            aria-label="Search incidents on the map"
            placeholder="Search incidents or places"
            className="h-10 rounded-md border-border bg-card pl-9 text-foreground"
          />
          {searchQuery.trim() && (
            <div className="mt-2 max-h-48 overflow-y-auto rounded-md border border-border bg-card">
              {filteredIncidents.length === 0 ? (
                <div className="px-3 py-2 text-xs text-muted-foreground">No matching incidents or locations</div>
              ) : filteredIncidents.slice(0, 6).map((incident) => {
                const location = formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable;
                return (
                  <button
                    type="button"
                    key={incident.id}
                    onClick={() => { setSelectedIncident(incident); setSearchQuery(''); }}
                    className="block w-full border-b border-border px-3 py-2 text-left text-xs last:border-0 hover:bg-accent"
                  >
                    <span className="font-semibold">#{incident.id} {incident.incident_type}</span>
                    <span className="block truncate text-muted-foreground">{location || 'Location unavailable'}</span>
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
          className="h-10 shrink-0 gap-2 bg-card"
          aria-controls="live-map-filters"
          aria-expanded={filtersOpen}
        >
          <Filter className="size-4" /> Filters
        </Button>
      </div>

      {/* Filters Card */}
      <div id="live-map-filters" hidden={!filtersOpen}>
        <div className="grid gap-4 rounded-md border border-border bg-card p-4 sm:grid-cols-2">
          {/* Category Pills */}
          <div className="space-y-1.5">
            <p className="text-sm font-semibold">Emergency type</p>
            <div className="flex flex-wrap gap-1.5">
              {['ALL', ...incidentCategories].map((cat) => (
                <button
                  key={cat}
                  type="button"
                  aria-pressed={selectedCategory === cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`min-h-9 rounded-md px-2.5 py-1 text-xs font-semibold ${
                    selectedCategory === cat
                      ? 'bg-primary text-primary-foreground shadow-[0_1px_2px_rgba(10,10,10,0.04)]'
                      : 'bg-muted text-muted-foreground hover:bg-[#E7E3DC] dark:hover:bg-[#26292C] hover:text-foreground'
                  }`}
                >
                  {cat === 'ALL' ? 'All types' : cat.replaceAll('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          {/* Display Options */}
          <div className="space-y-2">
            <p className="text-sm font-semibold">Display options</p>
            <div className="space-y-1.5 text-sm text-foreground">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={activeOpsOnly}
                  onChange={(e) => setActiveOpsOnly(e.target.checked)}
                  className="rounded-md border-border text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                />
                <span className="text-xs font-medium">Active Operations</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={showRespondersOnMap}
                  onChange={(e) => setShowRespondersOnMap(e.target.checked)}
                  className="rounded-md border-border text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                />
                <span className="text-xs font-medium">Show Responder Units</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground tabular-nums">
        <span>{filteredIncidents.length} {filteredIncidents.length === 1 ? 'incident' : 'incidents'} shown</span>
        {showRespondersOnMap && <span>{responders.length} responder {responders.length === 1 ? 'unit' : 'units'}</span>}
      </div>
      <div className="flex min-w-0 flex-col gap-4 xl:flex-row">
        <section aria-label="Live incident map" className="surface-panel isolate h-[55dvh] min-h-80 min-w-0 xl:h-[65dvh] xl:flex-1">
          <IncidentMap
            incidents={filteredIncidents}
            responders={responders}
            showIncidents={true}
            showResponders={showRespondersOnMap}
            selectedIncidentId={selectedIncident?.id}
            onSelectIncident={(inc) => setSelectedIncident(inc)}
            className="h-full w-full"
          />
        </section>
        {/* Selected Incident Details Card */}
        {selectedIncident && (
          <div className="surface-panel min-w-0 xl:w-80 xl:shrink-0 xl:self-start">
            <div className="space-y-3 p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-primary flex items-center gap-1.5">
                  <AlertTriangle className="h-4 w-4" /> Incident #{selectedIncident.id}
                </span>
                <Badge variant="outline" className="text-[10px] font-medium text-[#F2A540] border-[#F2A540]/40">
                  {selectedIncident.status}
                </Badge>
              </div>

              <div className="flex items-center justify-between gap-2 text-xs">
                <span className="text-muted-foreground">Alert Source:</span>
                <span className={`px-2 py-0.5 rounded-md font-bold text-[10px] ${
                  selectedIsP2P
                    ? 'bg-[#F2A540]/10 text-[#F2A540] border border-[#F2A540]/30'
                    : 'bg-[#1F91A8]/10 text-[#1F91A8] border border-[#1F91A8]/30'
                }`}>
                  {selectedIsP2P ? 'P2P RECEIVED' : 'DATABASE ONLINE'}
                </span>
              </div>

              {nearestUnitInfo && (
                <div className="flex items-center justify-between gap-2 rounded-md border border-border bg-muted p-1.5 font-mono text-[11px]">
                  <span className="text-muted-foreground">Nearest Unit ({nearestUnitInfo.unit.unit_callsign}):</span>
                  <span className="font-bold text-[#1F91A8]">{nearestUnitInfo.distText}</span>
                </div>
              )}

              <div className="text-xs text-foreground font-medium">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
              </div>
              <div className="text-[10px] text-muted-foreground font-mono">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}
              </div>
              <div className="flex items-center justify-between border-t border-border pt-1 text-[11px] font-medium text-muted-foreground">
                <span>Hops: {selectedIncident.hop_count}</span>
                <span>Channel: {selectedIncident.channel}</span>
              </div>

              <div className="space-y-2 border-t border-border pt-2">
                <label htmlFor="map-dispatch-unit" className="text-[10px] font-bold uppercase tracking-[0.12em] text-muted-foreground">
                  Dispatch responder
                </label>
                {!isIncidentDispatchable(selectedIncident) && (
                  <p className="text-[10px] text-amber-600 dark:text-amber-400">Closed incidents cannot receive new dispatches.</p>
                )}
                <select
                  id="map-dispatch-unit"
                  value={selectedUnitId}
                  onChange={(event) => setSelectedUnitId(event.target.value)}
                  disabled={dispatching || agenciesLoading || !isIncidentDispatchable(selectedIncident) || availableResponders.length === 0}
                  className="h-9 w-full rounded-md border border-border bg-card px-2 text-xs text-foreground outline-none focus:border-[#1F91A8]"
                >
                  <option value="">
                    {agenciesLoading
                      ? 'Loading eligible units...'
                      : availableResponders.length === 0
                        ? `No active idle ${requiredAgencyType(selectedIncident)} units available`
                        : 'Select available unit'}
                  </option>
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
                  disabled={!selectedUnitId || dispatching || !isIncidentDispatchable(selectedIncident)}
                  className="h-control w-full"
                >
                  {dispatching ? 'Dispatching...' : 'Dispatch selected unit'}
                </Button>
                {dispatchFeedback && (
                  <p role="status" className={`text-xs ${dispatchFeedback.includes('successfully') ? 'text-emerald-500' : 'text-red-500'}`}>
                    {dispatchFeedback}
                  </p>
                )}
              </div>

              <button
                onClick={() => setSelectedIncident(null)}
                className="flex w-full items-center justify-center gap-1 rounded-md border border-border bg-muted py-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
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
