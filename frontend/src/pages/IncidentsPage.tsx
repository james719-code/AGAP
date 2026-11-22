import React, { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { apiService } from '@/services/api';
import type { DispatchStatus, Incident, IncidentDispatch, IncidentStatus, IncidentStatusHistory } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { IncidentMap } from '@/components/map/IncidentMap';
import { IncidentReportsModal } from '@/components/incidents/IncidentReportsModal';
import {
  Filter,
  Eye,
  FileText,
  RefreshCw,
  Download,
  Layers,
  MapPin,
  Send,
  ChevronLeft,
  ChevronRight,
  Bluetooth,
  Wifi,
} from 'lucide-react';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import { eligibleResponders, isIncidentDispatchable, requiredAgencyType as getRequiredAgencyType } from '@/lib/dispatchEligibility';
import { incidentStatusLabel, incidentStatusTone } from '@/lib/incidentPresentation';

const INCIDENT_STATUSES: IncidentStatus[] = [
  'RECEIVED',
  'ACKNOWLEDGED',
  'DISPATCHED',
  'EN_ROUTE',
  'ON_SCENE',
  'RESOLVED',
  'CANCELLED',
  'FALSE_ALARM',
];

// Mirrors the backend's incident status machine (domain.ErrInvalidStatusTransition).
// Keeping this in sync with the server means we disable illegal transitions in the
// UI instead of letting them round-trip to a 400.
const INCIDENT_TRANSITIONS: Record<IncidentStatus, IncidentStatus[]> = {
  CREATED: ['QUEUED', 'RECEIVED', 'CANCELLED'],
  QUEUED: ['RECEIVED', 'CANCELLED'],
  RECEIVED: ['ACKNOWLEDGED', 'DISPATCHED', 'CANCELLED', 'FALSE_ALARM'],
  ACKNOWLEDGED: ['DISPATCHED', 'CANCELLED', 'FALSE_ALARM'],
  DISPATCHED: ['EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'RESOLVED', 'CANCELLED'],
  ON_SCENE: ['RESOLVED', 'FALSE_ALARM'],
  RESOLVED: [],
  CANCELLED: [],
  FALSE_ALARM: [],
};

// Mirrors dispatch_service.go's isAgencyTypeAllowedForIncident — the backend
// rejects a dispatch outright if the unit's agency type doesn't match the
// incident type, so the picker should only ever offer units that would pass.
const DISPATCH_TRANSITIONS: Record<DispatchStatus, DispatchStatus[]> = {
  ASSIGNED: ['ACCEPTED', 'CANCELLED'],
  ACCEPTED: ['EN_ROUTE', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'COMPLETED', 'CANCELLED'],
  ON_SCENE: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
};

const PAGE_SIZE = 10;

// Matches the map marker colors so type reads the same everywhere.
const TYPE_COLORS: Record<string, string> = {
  MEDICAL: '#176e7e',
  FIRE: '#ac4c14',
  ACCIDENT: '#a86f00',
  CRIME: '#a82e20',
  DISASTER: '#7643a5',
};

const SEVERITY_BY_TONE: Record<string, string> = {
  'tone-danger': 'var(--sev-critical)',
  'tone-warning': 'var(--sev-moderate)',
  'tone-info': 'var(--sev-info)',
  'tone-success': 'var(--sev-resolved)',
};

function receivedAgo(iso?: string) {
  if (!iso) return null;
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return days < 7 ? `${days} d ago` : new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}
const CLOSED_STATUSES: IncidentStatus[] = ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'];

export interface IncidentsPageProps {
  variant?: 'all' | 'archive';
}

export const IncidentsPage: React.FC<IncidentsPageProps> = ({ variant = 'all' }) => {
  const isArchive = variant === 'archive';
  const {
    incidents,
    total,
    filter,
    setFilter,
    loading,
    error,
    refetch,
    updateStatus,
    hasNextPage,
    hasPrevPage,
    nextPage,
    prevPage,
  } = useIncidents({
    limit: PAGE_SIZE,
    offset: 0,
    closed_only: isArchive,
  });
  const { responders } = useResponders();
  const { agencies } = useAgencies();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const [linkedIncidentError, setLinkedIncidentError] = useState<string | null>(null);
  const linkedIncidentId = searchParams.get('incidentId');
  const [updating, setUpdating] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [dispatches, setDispatches] = useState<IncidentDispatch[]>([]);
  const [dispatchesLoading, setDispatchesLoading] = useState(false);
  const [dispatchError, setDispatchError] = useState<string | null>(null);
  const [statusHistory, setStatusHistory] = useState<IncidentStatusHistory[]>([]);
  const [statusHistoryLoading, setStatusHistoryLoading] = useState(false);
  const [statusHistoryError, setStatusHistoryError] = useState<string | null>(null);
  const [updatingDispatchId, setUpdatingDispatchId] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<'table' | 'map'>('table');
  const [selectedUnitId, setSelectedUnitId] = useState('');
  const [dispatching, setDispatching] = useState(false);
  const [reportsIncident, setReportsIncident] = useState<Incident | null>(null);

  useEffect(() => {
    if (!linkedIncidentId) return;
    let cancelled = false;
    const id = Number(linkedIncidentId);
    const open = async () => {
      setLinkedIncidentError(null);
      try {
        if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Invalid incident link.');
        const incident = await apiService.getIncidentById(id);
        if (!cancelled) setSelectedIncident(incident);
      } catch (error) {
        if (!cancelled) setLinkedIncidentError(error instanceof Error ? error.message : 'Could not open incident.');
      } finally {
        if (!cancelled) setSearchParams((params) => {
          const next = new URLSearchParams(params);
          next.delete('incidentId');
          return next;
        }, { replace: true });
      }
    };
    queueMicrotask(() => { if (!cancelled) void open(); });
    return () => { cancelled = true; };
  }, [linkedIncidentId, setSearchParams]);

  const loadStatusHistory = useCallback(async (incidentId: number) => {
    setStatusHistoryLoading(true);
    setStatusHistoryError(null);
    try {
      const items = await apiService.getIncidentStatusHistory(incidentId);
      setStatusHistory(items);
    } catch (err) {
      setStatusHistory([]);
      setStatusHistoryError(err instanceof Error ? err.message : 'Failed to load incident status history');
    } finally {
      setStatusHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!selectedIncident) return;
    const current = incidents.find((incident) => incident.id === selectedIncident.id);
    // Only sync the reference in place — do NOT refetch here. The effect below
    // already refetches whenever selectedIncident's identity changes, so calling
    // loadStatusHistory here as well was firing a second, redundant network
    // round-trip on every incident-list refresh and made opening the dialog feel laggy.
    if (current && current.status !== selectedIncident.status) {
      queueMicrotask(() => setSelectedIncident(current));
    }
  }, [incidents, selectedIncident]);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      if (!selectedIncident) {
        setDispatches([]);
        setDispatchError(null);
        setStatusHistory([]);
        setStatusHistoryError(null);
        setStatusError(null);
        setSelectedUnitId('');
        return;
      }

      setDispatchesLoading(true);
      setDispatchError(null);
      setStatusHistoryLoading(true);
      setStatusHistoryError(null);
      setStatusError(null);
      setSelectedUnitId('');

      apiService
        .getIncidentDispatches(selectedIncident.id)
        .then((items) => {
          if (!cancelled) setDispatches(items);
        })
        .catch((err) => {
          if (!cancelled) {
            setDispatches([]);
            setDispatchError(err instanceof Error ? err.message : 'Failed to load dispatches');
          }
        })
        .finally(() => {
          if (!cancelled) setDispatchesLoading(false);
        });

      apiService
        .getIncidentStatusHistory(selectedIncident.id)
        .then((items) => {
          if (!cancelled) setStatusHistory(items);
        })
        .catch((err) => {
          if (!cancelled) {
            setStatusHistory([]);
            setStatusHistoryError(err instanceof Error ? err.message : 'Failed to load incident status history');
          }
        })
        .finally(() => {
          if (!cancelled) setStatusHistoryLoading(false);
        });
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [selectedIncident]);

  const handleStatusChange = async (newStatus: IncidentStatus) => {
    if (!selectedIncident) return;
    setStatusError(null);
    setUpdating(true);
    try {
      await updateStatus(selectedIncident.id, newStatus);
      setSelectedIncident({ ...selectedIncident, status: newStatus });
      await loadStatusHistory(selectedIncident.id);
    } catch (err) {
      setStatusError(err instanceof Error ? err.message : 'Failed to update incident status.');
    } finally {
      setUpdating(false);
    }
  };

  const handleDispatchUnit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedIncident || !selectedUnitId || !isIncidentDispatchable(selectedIncident)) return;
    setDispatching(true);
    setDispatchError(null);
    try {
      const created = await apiService.createDispatch(selectedIncident.id, Number(selectedUnitId));
      setDispatches((prev) => [created, ...prev]);
      setSelectedUnitId('');
      const [freshIncident] = await Promise.all([
        apiService.getIncidents({ ...filter }).then((res) => res.incidents.find((i) => i.id === selectedIncident.id)),
        refetch(),
      ]);
      if (freshIncident) setSelectedIncident(freshIncident);
      const refreshedHistory = await apiService.getIncidentStatusHistory(selectedIncident.id);
      setStatusHistory(refreshedHistory);
    } catch (err) {
      setDispatchError(err instanceof Error ? err.message : 'Failed to dispatch unit.');
    } finally {
      setDispatching(false);
    }
  };

  const handleDispatchStatusChange = async (dispatch: IncidentDispatch, newStatus: DispatchStatus) => {
    if (!selectedIncident) return;
    setUpdatingDispatchId(dispatch.id);
    setDispatchError(null);
    try {
      const result = await apiService.updateDispatchStatus(selectedIncident.id, dispatch.id, newStatus);
      setDispatches((prev) =>
        prev.map((item) => (item.id === dispatch.id ? { ...item, status: result.new_status } : item))
      );
      if (newStatus === 'EN_ROUTE' || newStatus === 'ON_SCENE') {
        await refetch();
        setSelectedIncident({ ...selectedIncident, status: newStatus });
        await loadStatusHistory(selectedIncident.id);
      } else if (newStatus === 'COMPLETED') {
        await refetch();
        setSelectedIncident({ ...selectedIncident, status: 'RESOLVED' });
        await loadStatusHistory(selectedIncident.id);
      }
    } catch (err) {
      setDispatchError(err instanceof Error ? err.message : 'Failed to update dispatch status');
    } finally {
      setUpdatingDispatchId(null);
    }
  };

  const requiredAgencyType = selectedIncident ? getRequiredAgencyType(selectedIncident) : undefined;

  const availableUnits = React.useMemo(() => {
    const alreadyDispatchedUnitIds = new Set(
      (dispatches ?? []).filter((d) => d.status !== 'COMPLETED' && d.status !== 'CANCELLED').map((d) => d.unit_id)
    );
    return selectedIncident
      ? eligibleResponders(selectedIncident, responders ?? [], agencies ?? [], alreadyDispatchedUnitIds)
      : [];
  }, [dispatches, responders, agencies, selectedIncident]);

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">{isArchive ? 'Records' : 'Dispatch'}</span>
          <h1 className="page-title flex items-center gap-3">
            {isArchive ? 'Incident Archive' : 'Incidents'}
            <span className="rounded-md bg-muted px-2 py-0.5 font-mono text-sm font-semibold text-muted-foreground tabular-nums">{total}</span>
          </h1>
          <p className="page-description">
            {isArchive
              ? 'Resolved, cancelled, and false-alarm incidents.'
              : 'Triage incoming alerts, track response status, and coordinate assignments.'}
          </p>
        </div>

        <div className="page-actions">
          <div className="inline-flex overflow-hidden rounded-[0.625rem] border border-border" role="group" aria-label="Export current page">
            <Button
              onClick={() => exportIncidentsToCSV(incidents ?? [])}
              disabled={loading || Boolean(error) || !incidents.length}
              title="Export the current page as CSV"
              variant="ghost"
              size="sm"
              className="h-control gap-2 rounded-none border-r border-border px-3"
            >
              <Download className="size-3.5" /> CSV
            </Button>
            <Button
              onClick={() => exportIncidentsToJSON(incidents ?? [])}
              disabled={loading || Boolean(error) || !incidents.length}
              title="Export the current page as JSON"
              variant="ghost"
              size="sm"
              className="h-control rounded-none px-3"
            >
              JSON
            </Button>
          </div>
          <Button
            onClick={() => void refetch()}
            disabled={loading}
            variant="outline"
            size="sm"
            className="h-control gap-2 px-3"
          >
            <RefreshCw className={`size-3.5 ${loading ? 'motion-safe:animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {linkedIncidentError && (
        <div role="alert" className="tone-warning flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-4 py-3 text-sm">
          <span>Could not open linked incident: {linkedIncidentError}</span>
          <Button type="button" size="sm" variant="ghost" onClick={() => setLinkedIncidentError(null)}>Dismiss</Button>
        </div>
      )}

      <div className="surface-panel">
        {/* Toolbar: view switch, type + region filters */}
        <div className="flex flex-col gap-2 border-b border-border p-2.5 lg:flex-row lg:items-center">
          <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as 'table' | 'map')} className="shrink-0">
            <TabsList className="segmented">
              <TabsTrigger value="table" className="segmented-item"><Layers className="size-3.5" /> List</TabsTrigger>
              <TabsTrigger value="map" className="segmented-item"><MapPin className="size-3.5" /> Map</TabsTrigger>
            </TabsList>
          </Tabs>

          <div className="grid flex-1 grid-cols-1 gap-2 sm:grid-cols-[minmax(0,12rem)_minmax(0,12rem)_auto] lg:justify-end">
            <Select
              aria-label="Incident type"
              value={filter.type || ''}
              onChange={(e) => setFilter({ ...filter, type: e.target.value || undefined, offset: 0 })}
              className="h-control rounded-[0.625rem] border-border bg-background text-[0.8125rem]"
            >
              <option value="">All types</option>
              <option value="MEDICAL">Medical</option>
              <option value="FIRE">Fire</option>
              <option value="ACCIDENT">Accident</option>
              <option value="CRIME">Crime</option>
              <option value="DISASTER">Disaster</option>
            </Select>
            <Input
              aria-label="Region code"
              placeholder="Region code, e.g. 05"
              value={filter.region || ''}
              onChange={(e) => setFilter({ ...filter, region: e.target.value || undefined, offset: 0 })}
              className="h-control rounded-[0.625rem] border-border bg-background text-[0.8125rem]"
            />
            {(filter.type || filter.status || filter.region) ? (
              <Button type="button" variant="ghost" size="sm" className="h-control" onClick={() => setFilter({ ...filter, type: undefined, status: undefined, region: undefined, offset: 0 })}>
                <Filter className="size-3.5" /> Clear
              </Button>
            ) : <span className="hidden sm:block" />}
          </div>
        </div>

        {/* Status quick filters: one tap instead of a dropdown */}
        <div className="status-chips flex items-center gap-1.5 overflow-x-auto border-b border-border px-2.5 py-2" role="group" aria-label="Filter by status">
          {['', ...(isArchive ? CLOSED_STATUSES : INCIDENT_STATUSES)].map((status) => {
            const active = (filter.status || '') === status;
            const tone = status ? incidentStatusTone(status as IncidentStatus) : '';
            return (
              <button
                key={status || 'all'}
                type="button"
                aria-pressed={active}
                onClick={() => setFilter({ ...filter, status: status || undefined, offset: 0 })}
                className={`status-chip ${active ? 'status-chip-active' : ''}`}
              >
                {status && <span className="size-1.5 rounded-full" style={{ background: SEVERITY_BY_TONE[tone] ?? 'var(--muted-foreground)' }} aria-hidden="true" />}
                {status ? incidentStatusLabel(status) : isArchive ? 'All closed' : 'All statuses'}
              </button>
            );
          })}
          <span className={`status-pill ml-auto shrink-0 ${error ? 'tone-warning' : ''}`}>{error ? 'Data unavailable' : loading ? 'Loading…' : `${total} records`}</span>
        </div>

        {/* Main Content Area: Table View or Map View */}
        {viewMode === 'table' ? (
          <>
          <DataTableViewport label="Incidents table">
            {loading ? (
              <div className="p-12 text-center text-muted-foreground text-sm">Loading incident telemetry...</div>
            ) : error ? (
              <div className="state-message state-message-error" role="alert">
                <p>{error}</p>
                <Button type="button" variant="link" onClick={() => void refetch()}>Retry</Button>
              </div>
            ) : (incidents ?? []).length === 0 ? (
              <div className="state-message">
                <p>{isArchive ? 'No closed incidents match these filters.' : 'No incidents match these filters.'}</p>
                {(filter.type || filter.status || filter.region) && (
                  <Button type="button" variant="link" onClick={() => setFilter({ ...filter, type: undefined, status: undefined, region: undefined, offset: 0 })}>
                    Clear filters
                  </Button>
                )}
              </div>
            ) : (
              <table className="data-table incident-register-table incident-board">
                <thead>
                  <tr>
                    <th scope="col" className="text-left">Incident</th>
                    <th scope="col" className="text-left">Status</th>
                    <th scope="col" className="text-left">Location</th>
                    <th scope="col" className="text-left">Delivery</th>
                    <th scope="col" className="text-left">Received</th>
                    <th scope="col" className="text-right"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {(incidents ?? []).map((inc) => {
                    const location = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude).readable || 'Location unavailable';
                    const receivedIso = inc.received_at || inc.created_at;
                    const tone = incidentStatusTone(inc.status);
                    const isMesh = inc.channel === 'BLE_MESH';
                    return (
                      <tr
                        key={inc.id}
                        className="incident-row cursor-pointer"
                        style={{ ['--row-sev' as string]: SEVERITY_BY_TONE[tone] ?? 'transparent' }}
                        onClick={() => setSelectedIncident(inc)}
                      >
                        <td>
                          <div className="flex items-center gap-2.5">
                            <span className="size-2.5 shrink-0 rounded-full" style={{ background: TYPE_COLORS[inc.incident_type] ?? 'var(--muted-foreground)' }} aria-hidden="true" />
                            <div className="leading-tight">
                              <span className="block font-semibold">{incidentStatusLabel(inc.incident_type)}</span>
                              <span className="block font-mono text-[0.6875rem] text-muted-foreground">INC-{inc.id}</span>
                            </div>
                          </div>
                        </td>
                        <td><span className={`status-pill ${tone}`}>{incidentStatusLabel(inc.status)}</span></td>
                        <td className="incident-location">
                          <span className="block truncate font-medium" title={location}>{location}</span>
                          <span className="block font-mono text-[0.6875rem] text-muted-foreground tabular-nums">{inc.latitude?.toFixed(4)}, {inc.longitude?.toFixed(4)}</span>
                        </td>
                        <td>
                          <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
                            {isMesh ? <Bluetooth className="size-3.5 text-[var(--color-brand-amber)]" aria-hidden="true" /> : <Wifi className="size-3.5 text-[var(--sev-info)]" aria-hidden="true" />}
                            <span className="font-medium">{isMesh ? 'Mesh' : 'Online'}</span>
                            <span className="font-mono text-[0.6875rem] text-muted-foreground">· {inc.hop_count} {inc.hop_count === 1 ? 'hop' : 'hops'}</span>
                          </span>
                        </td>
                        <td className="whitespace-nowrap">
                          {receivedIso ? (
                            <time dateTime={receivedIso} title={new Date(receivedIso).toLocaleString()} className="leading-tight">
                              <span className="block font-medium">{receivedAgo(receivedIso)}</span>
                              <span className="block font-mono text-[0.6875rem] text-muted-foreground">{new Date(receivedIso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}</span>
                            </time>
                          ) : <span className="text-muted-foreground">Not recorded</span>}
                        </td>
                        <td className="text-right" onClick={(event) => event.stopPropagation()}>
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => setReportsIncident(inc)}
                              aria-label={`View citizen reports for incident ${inc.id}`}
                              title="Citizen reports"
                              className="size-8 p-0 text-muted-foreground hover:text-foreground"
                            >
                              <FileText className="size-4" />
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setSelectedIncident(inc)}
                              aria-label={`Open incident ${inc.id}`}
                              title="Open incident telemetry & dispatch"
                              className="h-8 gap-1.5 px-2.5 text-xs"
                            >
                              <Eye className="size-3.5" /> Open
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </DataTableViewport>

          {/* Pagination */}
            {!loading && !error && total > 0 && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted-foreground">
              <span>
                {total === 0
                  ? 'No records'
                  : `Showing ${(filter.offset ?? 0) + 1}\u2013${Math.min((filter.offset ?? 0) + (incidents?.length ?? 0), total)} of ${total}`}
              </span>
              <div className="flex flex-wrap items-center justify-center gap-1" aria-label="Incident pagination">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!hasPrevPage}
                  onClick={prevPage}
                  className="h-8 w-8 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent disabled:opacity-40"
                  aria-label="Previous page"
                  title="Previous page"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </Button>
                {(() => {
                  const pageSize = filter.limit ?? PAGE_SIZE;
                  const currentPage = Math.floor((filter.offset ?? 0) / pageSize) + 1;
                  const totalPages = Math.max(1, Math.ceil(total / pageSize));
                  const goToPage = (page: number) => {
                    const clamped = Math.min(Math.max(1, page), totalPages);
                    setFilter({ ...filter, offset: (clamped - 1) * pageSize });
                  };
                  const windowStart = Math.max(1, Math.min(currentPage - 3, totalPages - 6));
                  const windowEnd = Math.min(totalPages, windowStart + 6);
                  const pageNumbers = Array.from(
                    { length: windowEnd - windowStart + 1 },
                    (_, i) => windowStart + i
                  );

                  return (
                    <>
                      {windowStart > 1 && (
                        <>
                          <Button
                            size="sm"
                            variant="outline"
                            className="h-8 w-8 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
                            onClick={() => goToPage(1)}
                          >
                            1
                          </Button>
                          {windowStart > 2 && <span className="px-1 text-muted-foreground">…</span>}
                        </>
                      )}
                      {pageNumbers.map((p) => (
                        <Button
                          key={p}
                          size="sm"
                          variant={p === currentPage ? 'default' : 'outline'}
                          className={`h-8 w-8 p-0 rounded-md ${
                            p === currentPage
                              ? 'bg-primary text-primary-foreground hover:bg-primary/90 border-primary'
                              : 'border-border bg-card text-muted-foreground hover:bg-accent'
                          }`}
                          aria-current={p === currentPage ? 'page' : undefined}
                          onClick={() => goToPage(p)}
                        >
                          {p}
                        </Button>
                      ))}
                      {windowEnd < totalPages && (
                        <>
                          {windowEnd < totalPages - 1 && <span className="px-1 text-muted-foreground">…</span>}
                          <Button
                            size="sm"
                            variant="outline"
                            className="h-8 w-8 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
                            onClick={() => goToPage(totalPages)}
                          >
                            {totalPages}
                          </Button>
                        </>
                      )}
                    </>
                  );
                })()}
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!hasNextPage}
                  onClick={nextPage}
                  className="h-8 w-8 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent disabled:opacity-40"
                  aria-label="Next page"
                  title="Next page"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
          </>
        ) : (
          <div>
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3 text-sm text-muted-foreground">
            <span>Map shows {incidents.length} incident records from the current page.</span>
            <Link to="/dashboard/map" className="font-semibold text-primary hover:underline">Open full live map</Link>
          </div>
          <div className="relative h-[min(65dvh,600px)] min-h-80 overflow-hidden">
            <IncidentMap
              incidents={incidents ?? []}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full"
            />
          </div>
          </div>
        )}
      </div>

      {/* Incident Detail & Status Update Dialog */}
      {selectedIncident && (
        <Dialog
          open={!!selectedIncident}
          onOpenChange={() => setSelectedIncident(null)}
          contentClassName="agap-sheet my-0 mr-0 ml-auto flex h-dvh max-h-dvh w-full max-w-[60rem] flex-col overflow-hidden rounded-none border-y-0 border-r-0 p-0 sm:rounded-l-2xl sm:p-0"
        >
              <DialogHeader className="mb-0 shrink-0 border-b border-border py-4 pr-14 pl-4 sm:pl-6">
                <p className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.12em] text-muted-foreground">Incident #{selectedIncident.id}</p>
                <DialogTitle className="flex flex-wrap items-center gap-2.5 text-xl font-bold text-foreground">
                  <span className="size-3 rounded-full" style={{ background: TYPE_COLORS[selectedIncident.incident_type] ?? 'var(--muted-foreground)' }} aria-hidden="true" />
                  {incidentStatusLabel(selectedIncident.incident_type)}
                  <span className={`status-pill ${incidentStatusTone(selectedIncident.status)}`}>{incidentStatusLabel(selectedIncident.status)}</span>
                </DialogTitle>
                <DialogDescription className="text-sm text-muted-foreground">
                  Incident details, status history, and responder assignments
                </DialogDescription>
              </DialogHeader>

              {/* Landscape two-column body — each column scrolls independently so a long
                  timeline on one side never forces the whole dialog (and the other column)
                  to reflow/scroll together. This is what made the panel feel laggy before. */}
              <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-border overflow-y-auto lg:grid-cols-2 lg:divide-x lg:divide-y-0 lg:overflow-hidden">
                {/* Left column: telemetry details + status control + lifecycle timeline */}
                <div className="min-w-0 space-y-4 px-4 py-4 text-xs text-foreground sm:px-6 lg:overflow-y-auto">
                  <div className="rounded-lg border border-border bg-muted p-3.5">
                    <p className="text-xs font-semibold text-muted-foreground">Reported location</p>
                    <p className="mt-1 text-sm font-bold">{formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}</p>
                    <p className="mt-1 text-xs tabular-nums text-muted-foreground">{selectedIncident.latitude?.toFixed(5)}, {selectedIncident.longitude?.toFixed(5)}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}</p>
                  </div>
                  <div className="p-3.5 bg-card rounded-md border border-border space-y-2">
                    <div className="flex justify-between border-b border-border pb-1.5">
                      <span className="text-muted-foreground">Incident UUID:</span>
                      <span className="text-foreground font-medium">{selectedIncident.incident_uuid}</span>
                    </div>
                    <div className="flex justify-between border-b border-border pb-1.5">
                      <span className="text-muted-foreground">Packet UUID:</span>
                      <span className="text-foreground font-medium">{selectedIncident.packet_uuid || 'N/A'}</span>
                    </div>
                    <div className="flex justify-between border-b border-border pb-1.5">
                      <span className="text-muted-foreground">Sender Device:</span>
                      <span className="text-foreground font-medium">{selectedIncident.sender_device_id}</span>
                    </div>
                    <div className="flex justify-between border-b border-border pb-1.5">
                      <span className="text-muted-foreground">Target ID:</span>
                      <span className="text-[#1F91A8] font-bold">
                        {typeof selectedIncident.target_id === 'string' ? selectedIncident.target_id : selectedIncident.target_id?.raw}
                      </span>
                    </div>
                    <div className="flex justify-between border-b border-border pb-1.5">
                      <span className="text-muted-foreground">GPS Accuracy:</span>
                      <span className="text-foreground font-medium">{selectedIncident.gps_accuracy} meters</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Mesh Hop Routing:</span>
                      <span className="text-[#1F91A8] font-medium">{selectedIncident.delivery_path || `Direct (${selectedIncident.hop_count} hops)`}</span>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <h3 className="text-sm font-semibold text-foreground">Update response status</h3>
                    {statusError && (
                      <div className="rounded-md border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-[11px] text-red-700 dark:text-red-300">
                        {statusError}
                      </div>
                    )}
                    <div className="flex flex-wrap gap-2">
                      {INCIDENT_TRANSITIONS[selectedIncident.status].map((st) => {
                        return (
                          <Button
                            key={st}
                            size="sm"
                            variant="outline"
                            disabled={updating}
                            onClick={() => handleStatusChange(st)}
                            className={`h-control text-sm ${st === 'CANCELLED' || st === 'FALSE_ALARM' ? 'text-[var(--status-danger)]' : ''}`}
                          >
                            {incidentStatusLabel(st)}
                          </Button>
                        );
                      })}
                      {!INCIDENT_TRANSITIONS[selectedIncident.status].length && <p className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">This incident is closed. No further status changes are available.</p>}
                    </div>
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground">Lifecycle Timeline:</label>
                    <div className="space-y-2">
                      {statusHistoryLoading ? (
                        <div className="p-3 rounded-md border border-border bg-muted text-muted-foreground">
                          Loading status history...
                        </div>
                      ) : statusHistory.length === 0 ? (
                        <div className="p-3 rounded-md border border-border bg-muted text-muted-foreground">
                          {statusHistoryError || 'No status history has been recorded for this incident.'}
                        </div>
                      ) : (
                        statusHistory.map((event) => (
                          <div key={event.id} className="p-3 rounded-md border border-border bg-[#F7F5F2]/70 dark:bg-[#181C1D]/70">
                            <div className="flex items-start justify-between gap-3">
                              <div className="space-y-1">
                                <span className={`status-pill ${incidentStatusTone(event.status)}`}>{incidentStatusLabel(event.status)}</span>
                                <div className="text-muted-foreground">
                                  {new Date(event.created_at).toLocaleString()}
                                </div>
                              </div>
                              {event.changed_by && (
                                <span className="text-muted-foreground font-mono">User #{event.changed_by}</span>
                              )}
                            </div>
                            {event.notes && <div className="mt-2 text-muted-foreground">{event.notes}</div>}
                            {typeof event.latitude === 'number' && typeof event.longitude === 'number' && (
                              <div className="mt-2 font-mono text-muted-foreground">
                                {event.latitude.toFixed(5)}, {event.longitude.toFixed(5)}
                              </div>
                            )}
                          </div>
                        ))
                      )}
                      {statusHistoryError && statusHistory.length > 0 && (
                        <div className="text-xs text-red-600 dark:text-red-400">{statusHistoryError}</div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Right column: dispatch form + dispatch timeline */}
                <div className="min-w-0 space-y-4 px-4 py-4 text-xs text-foreground sm:px-6 lg:overflow-y-auto">
                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground">Dispatch a Responder Unit:</label>
                    {!isIncidentDispatchable(selectedIncident) && (
                      <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-[11px] text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
                        Closed incidents cannot receive new dispatches.
                      </div>
                    )}
                    <form onSubmit={handleDispatchUnit} className="flex flex-col sm:flex-row gap-2 rounded-md border border-border bg-[#F7F5F2]/40 dark:bg-[#181C1D]/40 p-3">
                      <Select
                        aria-label="Responder unit to dispatch"
                        value={selectedUnitId}
                        onChange={(e) => setSelectedUnitId(e.target.value)}
                        disabled={dispatching || !isIncidentDispatchable(selectedIncident) || availableUnits.length === 0}
                        className="flex-1 h-9 rounded-md border-border bg-card text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                      >
                        <option value="">
                          {availableUnits.length === 0
                            ? requiredAgencyType
                              ? `No idle ${requiredAgencyType} units available`
                              : 'No idle units available'
                            : 'Select an idle responder unit'}
                        </option>
                        {availableUnits.map((unit) => (
                          <option key={unit.id} value={String(unit.id)}>
                            {unit.unit_callsign}
                          </option>
                        ))}
                      </Select>
                      <Button
                        type="submit"
                        size="sm"
                        disabled={dispatching || !selectedUnitId || !isIncidentDispatchable(selectedIncident)}
                        className="flex items-center gap-1.5 bg-primary hover:bg-primary/90 text-primary-foreground rounded-md whitespace-nowrap"
                      >
                        <Send className="h-3.5 w-3.5" />
                        {dispatching ? 'Dispatching...' : 'Dispatch Unit'}
                      </Button>
                    </form>
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground">Dispatch Timeline:</label>
                    <div className="space-y-2">
                      {dispatchesLoading ? (
                        <div className="p-3 rounded-md border border-border bg-muted text-muted-foreground">
                          Loading dispatches...
                        </div>
                      ) : dispatches.length === 0 ? (
                        <div className="p-3 rounded-md border border-border bg-muted text-muted-foreground">
                          {dispatchError || 'No responder units have been dispatched for this incident.'}
                        </div>
                      ) : (
                        dispatches.map((dispatch) => (
                          <div key={dispatch.id} className="p-3 rounded-md border border-border bg-[#F7F5F2]/70 dark:bg-[#181C1D]/70 space-y-2">
                            <div className="flex items-center justify-between gap-3">
                              <div>
                                <div className="font-bold text-foreground">
                                  {dispatch.unit_callsign || `Unit #${dispatch.unit_id}`}
                                </div>
                                <div className="text-muted-foreground">
                                  {[dispatch.agency_name, dispatch.responder_name].filter(Boolean).join(' / ') ||
                                    'Responder assignment'}
                                </div>
                              </div>
                              <Badge
                                variant={
                                  dispatch.status === 'COMPLETED'
                                    ? 'success'
                                    : dispatch.status === 'CANCELLED'
                                    ? 'destructive'
                                    : 'warning'
                                }
                                className="rounded-md text-[10px]"
                              >
                                {dispatch.status}
                              </Badge>
                            </div>
                            <div className="flex flex-wrap gap-2">
                              {DISPATCH_TRANSITIONS[dispatch.status].map((status) => (
                                <Button
                                  key={status}
                                  size="sm"
                                  variant="outline"
                                  disabled={updatingDispatchId === dispatch.id}
                                  onClick={() => handleDispatchStatusChange(dispatch, status)}
                                  className="text-xs font-bold rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
                                >
                                  {status}
                                </Button>
                              ))}
                              {DISPATCH_TRANSITIONS[dispatch.status].length === 0 && (
                                <span className="text-muted-foreground">Terminal dispatch state</span>
                              )}
                            </div>
                          </div>
                        ))
                      )}
                      {dispatchError && dispatches.length > 0 && (
                        <div className="text-xs text-red-600 dark:text-red-400">{dispatchError}</div>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              <DialogFooter className="mt-0 shrink-0 border-t border-border px-4 py-3 sm:px-6">
                <Button
                  variant="outline"
                  onClick={() => setSelectedIncident(null)}
                  className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
                >
                  Close
                </Button>
              </DialogFooter>
        </Dialog>
      )}

      {/* Citizen Incident Reports Modal */}
      {reportsIncident && (
        <IncidentReportsModal
          isOpen={!!reportsIncident}
          onClose={() => setReportsIncident(null)}
          incidentId={reportsIncident.id}
          incidentUuid={reportsIncident.incident_uuid}
        />
      )}
    </div>
  );
};
