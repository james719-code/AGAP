import React, { useCallback, useEffect, useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { apiService } from '@/services/api';
import type { DispatchStatus, Incident, IncidentDispatch, IncidentStatus, IncidentStatusHistory } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';
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
} from 'lucide-react';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import { eligibleResponders, isIncidentDispatchable, requiredAgencyType as getRequiredAgencyType } from '@/lib/dispatchEligibility';

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

const isOpenStatus = (status: IncidentStatus) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(status);
const isResponseStatus = (status: IncidentStatus) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(status);

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
    status: isArchive ? CLOSED_STATUSES[0] : undefined,
  });
  const { responders } = useResponders();
  const { agencies } = useAgencies();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
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
          <span className="page-eyebrow">{isArchive ? 'Archive' : 'Operations'}</span>
          <h2 className="page-title">{isArchive ? 'Incident Archive' : 'Incidents'}</h2>
          <p className="page-description">
            {isArchive
              ? 'Review resolved, cancelled, and false-alarm incidents.'
              : 'Review, filter, export, and manage incident records.'}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <Button
            onClick={() => exportIncidentsToCSV(incidents ?? [])}
            variant="outline"
            size="sm"
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <Download className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
            <span>CSV</span>
          </Button>
          <Button
            onClick={() => exportIncidentsToJSON(incidents ?? [])}
            variant="outline"
            size="sm"
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <Download className="h-3.5 w-3.5 text-[#1F91A8]" />
            <span>JSON</span>
          </Button>
          <Button
            onClick={() => void refetch()}
            variant="outline"
            size="sm"
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Refresh</span>
          </Button>
        </div>
      </div>

      {/* Unified control + data surface: view switcher, filters, and results all live
          in one bordered card so they read as a single component instead of three
          disconnected pieces stacked on the page. */}
      <div className="rounded-md border border-border bg-card shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        {/* Toolbar: view switcher + filters share one row and one background,
            visually welded to the table/map below via a single border. */}
        <div className="flex flex-col lg:flex-row lg:items-center gap-3 px-4 py-3 bg-muted border-b border-border">
          <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as 'table' | 'map')} className="flex-shrink-0">
            <TabsList className="bg-card border border-border rounded-md p-0.5">
              <TabsTrigger
                value="table"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-3 py-1.5 gap-1.5"
              >
                <Layers className="h-3.5 w-3.5" /> Table View
              </TabsTrigger>
              <TabsTrigger
                value="map"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-3 py-1.5 gap-1.5"
              >
                <MapPin className="h-3.5 w-3.5 text-[#1F91A8]" /> Map View
              </TabsTrigger>
            </TabsList>
          </Tabs>

          <Separator orientation="vertical" className="hidden lg:block h-6 bg-[#E7E3DC] dark:bg-[#26292C]" />

          {/* Filter Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 flex-1 w-full">
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground font-semibold shrink-0">
              <Filter className="h-3.5 w-3.5 text-[#1F91A8]" /> Filters:
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 flex-1 w-full">
              <Select
                value={filter.type || ''}
                onChange={(e) => setFilter({ ...filter, type: e.target.value || undefined, offset: 0 })}
                className="h-9 rounded-md border-border bg-card text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              >
                <option value="">All Incident Types</option>
                <option value="CRIME">CRIME</option>
                <option value="MEDICAL">MEDICAL</option>
                <option value="ACCIDENT">ACCIDENT</option>
                <option value="DISASTER">DISASTER</option>
                <option value="FIRE">FIRE</option>
              </Select>

              <Select
                value={filter.status || ''}
                onChange={(e) => setFilter({ ...filter, status: e.target.value || undefined, offset: 0 })}
                className="h-9 rounded-md border-border bg-card text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              >
                <option value="">{isArchive ? 'All Closed Statuses' : 'All Statuses'}</option>
                {(isArchive ? CLOSED_STATUSES : INCIDENT_STATUSES).map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </Select>

              <Input
                placeholder="Filter by Region Code (e.g. 05)..."
                value={filter.region || ''}
                onChange={(e) => setFilter({ ...filter, region: e.target.value || undefined, offset: 0 })}
                className="h-9 rounded-md border-border bg-card text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              />
            </div>
          </div>
        </div>

        {/* Results header: sits directly beneath the toolbar with no visual gap,
            so toolbar → status strip → data all read as one continuous surface. */}
        <div className="py-2.5 px-4 border-b border-border flex items-center justify-between">
          <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
            Recorded Telemetry Records <span className="text-muted-foreground font-normal">({total})</span>
          </h3>
          <div className="flex items-center gap-2 text-[10px] text-muted-foreground font-medium">
            <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-pulse"></span> AUTO-SYNCING
          </div>
        </div>

        {/* Main Content Area: Table View or Map View */}
        {viewMode === 'table' ? (
          <>
          <div className="overflow-x-auto">
            {loading ? (
              <div className="p-12 text-center text-muted-foreground text-sm">Loading incident telemetry...</div>
            ) : error ? (
              <div className="p-12 text-center text-red-600 dark:text-red-400 text-sm">{error}</div>
            ) : (incidents ?? []).length === 0 ? (
              <div className="p-12 text-center text-muted-foreground text-sm">No incidents match the selected filter.</div>
            ) : (
              <table className="data-table">
                <thead className="bg-muted border-b border-border">
                  <tr>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">ID</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Type</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Status</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Target Address</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Coordinates</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Mesh Hops</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Channel</th>
                    <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Received At</th>
                    <th className="text-right px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {(incidents ?? []).map((inc) => (
                    <tr key={inc.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-accent transition-colors">
                      <td><span className="identity-chip">INC-{inc.id}</span></td>
                      <td className="px-4 py-3">
                        <Badge
                          variant={
                            inc.incident_type === 'CRIME'
                              ? 'destructive'
                              : inc.incident_type === 'MEDICAL'
                              ? 'info'
                              : inc.incident_type === 'DISASTER'
                              ? 'purple'
                              : 'warning'
                          }
                          className="rounded-md text-[10px] font-semibold"
                        >
                          {inc.incident_type}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          variant={
                            isResponseStatus(inc.status)
                              ? 'warning'
                              : isOpenStatus(inc.status)
                              ? 'destructive'
                              : 'success'
                          }
                          className="rounded-md text-[10px] font-semibold"
                        >
                          {inc.status}
                        </Badge>
                      </td>
                      <td><span className="identity-chip truncate">{typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A'}</span></td>
                      <td><span className="technical-chip">{inc.latitude?.toFixed(4)}, {inc.longitude?.toFixed(4)}</span></td>
                      <td className="px-4 py-3">
                        <span className="technical-chip">
                          {inc.hop_count} hops
                        </span>
                      </td>
                      <td><span className="technical-chip">{inc.channel}</span></td>
                      <td className="px-4 py-3 text-[10px] text-muted-foreground">
                        {inc.received_at || inc.created_at
                          ? new Date(inc.received_at || inc.created_at!).toLocaleTimeString()
                          : '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setSelectedIncident(inc)}
                            title="View incident telemetry & dispatch"
                            className="h-9 w-9 p-0 rounded-md text-[#1F91A8] hover:bg-[#1F91A8]/10 hover:text-[#1F91A8] dark:hover:bg-[#1F91A8]/15"
                          >
                            <Eye className="h-[18px] w-[18px]" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setReportsIncident(inc)}
                            title="View citizen incident reports"
                            className="h-9 w-9 p-0 rounded-md text-amber-600 hover:bg-amber-600/10 hover:text-amber-600 dark:text-amber-400 dark:hover:bg-amber-400/15 dark:hover:text-amber-400"
                          >
                            <FileText className="h-[18px] w-[18px]" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Pagination */}
          {!loading && !error && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted-foreground">
              <span>
                {total === 0
                  ? 'No records'
                  : `Showing ${(filter.offset ?? 0) + 1}\u2013${Math.min((filter.offset ?? 0) + (incidents?.length ?? 0), total)} of ${total}`}
              </span>
              <div className="flex items-center gap-1">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!hasPrevPage}
                  onClick={prevPage}
                  className="h-8 w-8 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent disabled:opacity-40"
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
                  title="Next page"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
          </>
        ) : (
          <div className="h-[600px] overflow-hidden">
            <IncidentMap
              incidents={incidents ?? []}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full"
            />
          </div>
        )}
      </div>

      {/* Incident Detail & Status Update Dialog */}
      {selectedIncident && (
        <Dialog
          open={!!selectedIncident}
          onOpenChange={() => setSelectedIncident(null)}
          contentClassName="max-w-6xl max-h-[88vh] flex flex-col overflow-hidden border-[#E7E3DC] bg-white p-0 dark:border-[#26292C] dark:bg-[#161A1D]"
        >
              <DialogHeader className="px-6 pt-5 pb-3 shrink-0 border-b border-border">
                <DialogTitle className="flex items-center gap-3 text-lg font-bold text-foreground">
                  Incident #{selectedIncident.id}
                  <Badge variant="outline" className="font-mono text-[10px]">{selectedIncident.incident_type}</Badge>
                  <Badge
                    variant={
                      isResponseStatus(selectedIncident.status)
                        ? 'warning'
                        : isOpenStatus(selectedIncident.status)
                        ? 'destructive'
                        : 'success'
                    }
                    className="rounded-md text-[10px]"
                  >
                    {selectedIncident.status}
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-sm text-muted-foreground">
                  Spatial Telemetry Inspection &amp; Dispatch Workflow
                </DialogDescription>
              </DialogHeader>

              {/* Landscape two-column body — each column scrolls independently so a long
                  timeline on one side never forces the whole dialog (and the other column)
                  to reflow/scroll together. This is what made the panel feel laggy before. */}
              <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 gap-0 divide-y lg:divide-y-0 lg:divide-x divide-[#E7E3DC] dark:divide-[#26292C] overflow-hidden">
                {/* Left column: telemetry details + status control + lifecycle timeline */}
                <div className="overflow-y-auto px-6 py-4 space-y-4 text-xs text-foreground">
                  <div className="p-3.5 bg-muted rounded-md border border-border space-y-2">
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
                    <label className="text-xs font-semibold text-foreground">Change Incident Status:</label>
                    {statusError && (
                      <div className="rounded-md border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-[11px] text-red-700 dark:text-red-300">
                        {statusError}
                      </div>
                    )}
                    <div className="flex flex-wrap gap-2">
                      {INCIDENT_STATUSES.map((st) => {
                        const isCurrent = selectedIncident.status === st;
                        const isLegal = INCIDENT_TRANSITIONS[selectedIncident.status]?.includes(st);
                        return (
                          <Button
                            key={st}
                            size="sm"
                            variant={isCurrent ? 'default' : 'outline'}
                            disabled={updating || isCurrent || !isLegal}
                            title={!isCurrent && !isLegal ? `Not a valid transition from ${selectedIncident.status}` : undefined}
                            onClick={() => handleStatusChange(st)}
                            className={`flex-1 text-xs font-bold rounded-md ${
                              isCurrent
                                ? 'bg-primary text-primary-foreground hover:bg-primary/90'
                                : 'border-border bg-card text-muted-foreground hover:bg-accent'
                            } disabled:opacity-40`}
                          >
                            {st}
                          </Button>
                        );
                      })}
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
                                <Badge
                                  variant={
                                    isResponseStatus(event.status)
                                      ? 'warning'
                                      : isOpenStatus(event.status)
                                      ? 'destructive'
                                      : 'success'
                                  }
                                  className="rounded-md text-[10px]"
                                >
                                  {event.status}
                                </Badge>
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
                <div className="overflow-y-auto px-6 py-4 space-y-4 text-xs text-foreground">
                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground">Dispatch a Responder Unit:</label>
                    {!isIncidentDispatchable(selectedIncident) && (
                      <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-[11px] text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
                        Closed incidents cannot receive new dispatches.
                      </div>
                    )}
                    <form onSubmit={handleDispatchUnit} className="flex flex-col sm:flex-row gap-2 rounded-md border border-border bg-[#F7F5F2]/40 dark:bg-[#181C1D]/40 p-3">
                      <Select
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

              <DialogFooter className="px-6 py-3 shrink-0 border-t border-border">
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
