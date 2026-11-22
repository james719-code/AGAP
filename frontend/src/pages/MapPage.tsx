import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { incidentStatusLabel, incidentStatusTone } from '@/lib/incidentPresentation';
import { IncidentMap, MapLegend } from '@/components/map/IncidentMap';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Search, Filter, AlertTriangle, X, RefreshCw, MapPin } from 'lucide-react';
import type { Incident } from '@/types/api';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { apiService } from '@/services/api';
import { usesHostedApi } from '@/services/backend';
import { eligibleResponders, isIncidentDispatchable, requiredAgencyType } from '@/lib/dispatchEligibility';
import { retainUnitSelection } from '@/lib/workspaceSelection';

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
  const { agencies, loading: agenciesLoading, error: agenciesError, refetch: refetchAgencies } = useAgencies();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [showRespondersOnMap, setShowRespondersOnMap] = useState(true);
  const [selectedUnitId, setSelectedUnitId] = useState('');
  const [dispatching, setDispatching] = useState(false);
  const [dispatchFeedback, setDispatchFeedback] = useState<{ incidentId: number; message: string; successful: boolean } | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const incidentForUnitRef = React.useRef<number | undefined>(undefined);
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
    const incidentChanged = incidentForUnitRef.current !== selectedIncident?.id;
    incidentForUnitRef.current = selectedIncident?.id;
    queueMicrotask(() => {
      if (incidentChanged) {
        setSelectedUnitId('');
        setDispatchFeedback(null);
      } else {
        setSelectedUnitId((current) => retainUnitSelection(current, availableResponders));
      }
    });
  }, [availableResponders, selectedIncident?.id]);

  const handleDispatch = async () => {
    if (!selectedIncident || !selectedUnitId || dispatching || !isIncidentDispatchable(selectedIncident) || !availableResponders.some((unit) => String(unit.id) === selectedUnitId)) return;
    const incidentId = selectedIncident.id;
    setDispatching(true);
    setDispatchFeedback(null);
    try {
      await apiService.createDispatch(incidentId, Number(selectedUnitId));
      setDispatchFeedback({ incidentId, message: 'Responder unit dispatched successfully.', successful: true });
      await Promise.all([refetchIncidents(), refetchResponders()]);
    } catch (error) {
      setDispatchFeedback({ incidentId, message: error instanceof Error ? error.message : 'Failed to dispatch responder unit', successful: false });
    } finally {
      setDispatching(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await Promise.all([refetchIncidents(), refetchResponders(), refetchAgencies()]);
    } finally {
      setRefreshing(false);
    }
  };

  const dataError = incidentsError || respondersError || agenciesError;
  const filtersActive = selectedCategory !== 'ALL' || !activeOpsOnly || !showRespondersOnMap;

  return (
    <div className="map-stage">
      <h1 className="sr-only">Live Map</h1>
      <section aria-label="Live incident map" className="absolute inset-0">
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

      {/* Command strip: search, filters, live state */}
      <div className="map-float left-3 right-3 top-3 p-2 sm:left-4 sm:right-auto sm:top-4 sm:w-[26rem] lg:left-16">
        <div className="flex items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
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
              className="h-10 rounded-lg border-transparent bg-muted pl-9 pr-9 text-sm text-foreground"
            />
            {searchQuery && <button type="button" aria-label="Clear map search" onClick={() => setSearchQuery('')} className="absolute right-1 top-1 grid size-8 place-items-center rounded-md text-muted-foreground hover:bg-accent"><X className="size-4" /></button>}
            {searchQuery.trim() && (
              <div className="absolute inset-x-0 top-full z-30 mt-2 max-h-64 overflow-y-auto rounded-xl border border-border bg-popover shadow-lg">
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
            variant={filtersOpen ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFiltersOpen((open) => !open)}
            className="relative h-10 shrink-0 gap-2"
            aria-controls="live-map-filters"
            aria-expanded={filtersOpen}
          >
            <Filter className="size-4" /> <span className="max-sm:sr-only">Filters</span>
            {filtersActive && <span className="absolute right-1.5 top-1.5 size-2 rounded-full bg-primary" aria-label="Filters active" />}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={handleRefresh}
            disabled={refreshing || incidentsLoading || respondersLoading}
            className="size-10 shrink-0"
            aria-label="Refresh map data"
          >
            <RefreshCw className={`size-4 ${refreshing ? 'animate-spin' : ''}`} />
          </Button>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-2 px-1 pb-0.5 text-xs text-muted-foreground">
          <span className={`status-pill gap-1.5 before:hidden ${dataError ? 'tone-warning' : isWsConnected || usesHostedApi ? 'tone-success' : ''}`}>
            <span className={`live-dot ${dataError ? 'live-dot-warn' : isWsConnected || usesHostedApi ? 'live-dot-ok' : 'live-dot-idle'}`} aria-hidden="true" />
            {dataError ? 'Data unavailable' : usesHostedApi ? 'Auto-refresh' : isWsConnected ? 'Live feed' : 'Reconnecting'}
          </span>
          <span className="font-mono tabular-nums"><strong className="text-foreground">{filteredIncidents.length}</strong> {filteredIncidents.length === 1 ? 'incident' : 'incidents'}</span>
          {showRespondersOnMap && <span className="font-mono tabular-nums"><strong className="text-foreground">{responders.length}</strong> {responders.length === 1 ? 'unit' : 'units'}</span>}
        </div>

        <div id="live-map-filters" hidden={!filtersOpen} className="mt-2 space-y-4 border-t border-border px-1 pb-1 pt-3">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">Emergency type</p>
            <div className="flex flex-wrap gap-1.5">
              {['ALL', ...incidentCategories].map((cat) => (
                <button
                  key={cat}
                  type="button"
                  aria-pressed={selectedCategory === cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`min-h-8 rounded-md px-2.5 text-xs font-semibold transition-colors ${
                    selectedCategory === cat
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted text-muted-foreground hover:bg-accent hover:text-foreground'
                  }`}
                >
                  {cat === 'ALL' ? 'All types' : cat.replaceAll('_', ' ')}
                </button>
              ))}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
            <label className="flex cursor-pointer items-center gap-2">
              <input type="checkbox" checked={activeOpsOnly} onChange={(e) => setActiveOpsOnly(e.target.checked)} className="size-4 accent-[var(--primary)]" />
              <span className="text-xs font-medium">Active operations only</span>
            </label>
            <label className="flex cursor-pointer items-center gap-2">
              <input type="checkbox" checked={showRespondersOnMap} onChange={(e) => setShowRespondersOnMap(e.target.checked)} className="size-4 accent-[var(--primary)]" />
              <span className="text-xs font-medium">Show responder units</span>
            </label>
            <Button type="button" variant="ghost" size="sm" className="ml-auto h-8 text-xs" onClick={() => { setSelectedCategory('ALL'); setActiveOpsOnly(true); setShowRespondersOnMap(true); }}>Reset</Button>
          </div>
        </div>
      </div>

      {dataError && (
        <div role="alert" className="map-float left-3 right-3 top-[7.5rem] flex items-center justify-between gap-3 px-4 py-3 text-xs text-[var(--status-danger)] sm:left-4 sm:right-auto sm:w-[26rem] lg:left-16">
          <span>{dataError}</span>
          <button type="button" onClick={handleRefresh} className="shrink-0 font-semibold underline underline-offset-2">Retry</button>
        </div>
      )}

      {/* Legend */}
      <div className="map-float bottom-4 left-4 hidden px-3 py-2.5 md:block">
        <MapLegend showResponders={showRespondersOnMap} />
      </div>

      {/* Incident detail panel */}
      <aside
        aria-label="Incident details"
        className="map-float bottom-3 left-3 right-3 max-h-[55%] overflow-y-auto sm:bottom-auto sm:left-auto sm:right-4 sm:top-4 sm:max-h-[calc(100%-2rem)] sm:w-[22rem]"
      >
        {selectedIncident ? (
          <div>
            <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-border bg-[inherit] px-4 py-3.5 backdrop-blur">
              <div className="min-w-0">
                <p className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.12em] text-muted-foreground">Incident #{selectedIncident.id}</p>
                <h2 className="mt-1 flex items-center gap-2 text-lg font-bold">
                  <AlertTriangle className="size-4.5 text-[var(--sev-critical)]" aria-hidden="true" />
                  {incidentStatusLabel(selectedIncident.incident_type)}
                </h2>
              </div>
              <button type="button" onClick={() => setSelectedIncident(null)} aria-label="Close incident details" className="grid size-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground">
                <X className="size-4" />
              </button>
            </div>

            <div className="space-y-4 p-4">
              <div className="flex flex-wrap gap-1.5">
                <span className={`status-pill ${incidentStatusTone(selectedIncident.status)}`}>{incidentStatusLabel(selectedIncident.status)}</span>
                <span className={`status-pill ${selectedIsP2P ? 'tone-warning' : 'tone-info'}`}>{selectedIsP2P ? 'Bluetooth mesh' : 'Online'}</span>
              </div>

              <div>
                <p className="text-sm font-semibold">{formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}</p>
                <p className="mt-1 font-mono text-xs text-muted-foreground tabular-nums">{selectedIncident.latitude?.toFixed(5)}, {selectedIncident.longitude?.toFixed(5)}</p>
              </div>

              <dl className="grid grid-cols-2 gap-2 text-xs">
                <div className="rounded-lg bg-muted px-3 py-2"><dt className="text-muted-foreground">Hops</dt><dd className="mt-0.5 font-mono text-sm font-bold">{selectedIncident.hop_count}</dd></div>
                <div className="rounded-lg bg-muted px-3 py-2"><dt className="text-muted-foreground">Channel</dt><dd className="mt-0.5 font-mono text-sm font-bold">{selectedIncident.channel}</dd></div>
              </dl>

              {nearestUnitInfo && (
                <div className="rounded-lg border border-border p-3 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-muted-foreground">Nearest eligible unit<br /><strong className="text-sm text-foreground">{nearestUnitInfo.unit.unit_callsign}</strong></span>
                    <span className="font-mono text-sm font-bold tabular-nums">{nearestUnitInfo.distText}</span>
                  </div>
                  {isIncidentDispatchable(selectedIncident) && <Button type="button" variant="outline" size="sm" disabled={dispatching} onClick={() => setSelectedUnitId(String(nearestUnitInfo.unit.id))} className="mt-2.5 w-full">Select nearest unit</Button>}
                </div>
              )}

              <div className="space-y-2 border-t border-border pt-4">
                <label htmlFor="map-dispatch-unit" className="text-sm font-bold">Dispatch responder</label>
                {!isIncidentDispatchable(selectedIncident) && (
                  <p className="text-xs text-[var(--status-warning)]">Closed incidents cannot receive new dispatches.</p>
                )}
                <select
                  id="map-dispatch-unit"
                  value={selectedUnitId}
                  onChange={(event) => setSelectedUnitId(event.target.value)}
                  disabled={dispatching || agenciesLoading || !isIncidentDispatchable(selectedIncident) || availableResponders.length === 0}
                  className="h-10 w-full rounded-lg border border-border bg-background px-2 text-sm text-foreground focus:border-primary"
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
                  onClick={handleDispatch}
                  disabled={!selectedUnitId || dispatching || !isIncidentDispatchable(selectedIncident)}
                  className="h-10 w-full"
                >
                  {dispatching ? 'Dispatching...' : 'Dispatch selected unit'}
                </Button>
                {dispatchFeedback?.incidentId === selectedIncident.id && (
                  <p role="status" className={`rounded-md p-2 text-sm ${dispatchFeedback.successful ? 'tone-success' : 'tone-danger'}`}>
                    {dispatchFeedback.message}
                  </p>
                )}
              </div>

              <Link to={`/dashboard/incidents?incidentId=${selectedIncident.id}`} className="flex h-10 w-full items-center justify-center rounded-lg border border-border text-sm font-semibold hover:bg-accent">Open incident record</Link>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-3 p-4">
            <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-muted text-muted-foreground"><MapPin className="size-5" aria-hidden="true" /></span>
            <div>
              <h2 className="text-sm font-bold">Select an incident</h2>
              <p className="mt-1 text-xs leading-relaxed text-muted-foreground">Choose a marker or search to see location, status, and the nearest eligible unit.</p>
            </div>
          </div>
        )}
      </aside>
    </div>
  );
};

