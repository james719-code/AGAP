import React, { useCallback, useEffect, useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { apiService } from '@/services/api';
import type { DispatchStatus, Incident, IncidentDispatch, IncidentStatus, IncidentStatusHistory } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';
import { IncidentMap } from '@/components/map/IncidentMap';
import { IncidentReportsModal } from '@/components/incidents/IncidentReportsModal';
import { Filter, Eye, FileText, RefreshCw, Download, Layers, MapPin, Send, ChevronLeft, ChevronRight } from 'lucide-react';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';

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
const AGENCY_TYPE_FOR_INCIDENT: Record<string, string> = {
  MEDICAL: 'MEDICAL',
  ACCIDENT: 'RESCUE',
  FIRE: 'FIRE',
  DISASTER: 'DRRMO',
  CRIME: 'POLICE',
};
const DISPATCH_TRANSITIONS: Record<DispatchStatus, DispatchStatus[]> = {
  ASSIGNED: ['ACCEPTED', 'CANCELLED'],
  ACCEPTED: ['EN_ROUTE', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'COMPLETED', 'CANCELLED'],
  ON_SCENE: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
};

const PAGE_SIZE = 10;

// CLOSED_STATUSES defines what "archived" means for the /dashboard/archive
// route. The backend's IncidentFilter only accepts a single status value
// (see repository.IncidentFilter / incident_handler.go), not a list, so we
// can't ask the server for "any of RESOLVED/CANCELLED/FALSE_ALARM" in one
// request. We default the status filter to RESOLVED (the common case) and
// let the existing status dropdown be used to switch to CANCELLED or
// FALSE_ALARM — the dropdown itself lists closed statuses first in archive
// mode. If multi-status filtering is needed later, that requires extending
// repository.IncidentFilter/List() on the backend, not a client workaround.
const CLOSED_STATUSES: IncidentStatus[] = ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'];

export interface IncidentsPageProps {
  // 'archive' pre-filters to closed incidents and relabels the page instead
  // of duplicating the 'all' view verbatim.
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
    if (!selectedIncident) {
      setDispatches([]);
      setDispatchError(null);
      setStatusHistory([]);
      setStatusHistoryError(null);
      setStatusError(null);
      setSelectedUnitId('');
      return;
    }

    let cancelled = false;
    setDispatchesLoading(true);
    setDispatchError(null);
    setStatusHistoryLoading(true);
    setStatusHistoryError(null);
    setStatusError(null);
    setSelectedUnitId('');

    apiService.getIncidentDispatches(selectedIncident.id)
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

    apiService.getIncidentStatusHistory(selectedIncident.id)
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

    return () => {
      cancelled = true;
    };
  }, [selectedIncident?.id]);

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
    if (!selectedIncident || !selectedUnitId) return;
    setDispatching(true);
    setDispatchError(null);
    try {
      const created = await apiService.createDispatch(selectedIncident.id, Number(selectedUnitId));
      setDispatches((prev) => [created, ...prev]);
      setSelectedUnitId('');
      // NOTE: unconfirmed whether CreateDispatch on the backend also flips the
      // incident's status to DISPATCHED (that logic lives in dispatch_service.go,
      // which hasn't been reviewed here). We deliberately don't guess a status
      // locally — pull the authoritative record instead, so this can't drift
      // out of sync or flicker between an assumed and an actual value.
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

  // Units already dispatched (in a non-terminal state) to this incident shouldn't
  // be offered again in the picker.
  const alreadyDispatchedUnitIds = new Set(
    (dispatches ?? []).filter((d) => d.status !== 'COMPLETED' && d.status !== 'CANCELLED').map((d) => d.unit_id)
  );
  const requiredAgencyType = selectedIncident ? AGENCY_TYPE_FOR_INCIDENT[selectedIncident.incident_type] : undefined;
  const availableUnits = (responders ?? []).filter((u) => {
    if (u.status !== 'IDLE' || alreadyDispatchedUnitIds.has(u.id)) return false;
    if (!requiredAgencyType) return true; // unknown mapping — don't hide units, let the server be the source of truth
    const agency = (agencies ?? []).find((a) => a.id === u.agency_id);
    return agency?.type === requiredAgencyType;
  });

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">
            {isArchive ? 'Incident Archive' : 'Distress Incident Registry'}
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            {isArchive
              ? 'Closed incidents (resolved, cancelled, or false alarm) for historical review'
              : 'View, filter, export, and manage distress telemetry packets'}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <Button onClick={() => exportIncidentsToCSV(incidents ?? [])} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9">
            <Download className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" /> Export CSV
          </Button>
          <Button onClick={() => exportIncidentsToJSON(incidents ?? [])} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9">
            <Download className="h-3.5 w-3.5 text-[#1F91A8]" /> Export JSON
          </Button>
          <Button onClick={refetch} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
        </div>
      </div>

      <Separator />

      {/* Tabs View Switcher & Filters */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as 'table' | 'map')}>
          <TabsList>
            <TabsTrigger value="table" className="flex items-center gap-1.5">
              <Layers className="h-3.5 w-3.5" /> Table View
            </TabsTrigger>
            <TabsTrigger value="map" className="flex items-center gap-1.5">
              <MapPin className="h-3.5 w-3.5 text-[#1F91A8]" /> Map View
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* Filter Bar */}
        <div className="flex-1 flex flex-col sm:flex-row gap-3 items-center bg-card p-2.5 rounded-lg border border-border shadow-sm">
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground font-semibold px-1">
            <Filter className="h-3.5 w-3.5 text-[#1F91A8]" /> Filters:
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 flex-1 w-full">
            <Select
              value={filter.type || ''}
              onChange={(e) => setFilter({ ...filter, type: e.target.value || undefined, offset: 0 })}
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
            >
              <option value="">{isArchive ? 'All Closed Statuses' : 'All Statuses'}</option>
              {(isArchive ? CLOSED_STATUSES : INCIDENT_STATUSES).map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </Select>

            <Input
              placeholder="Filter by Region Code (e.g. 05)..."
              value={filter.region || ''}
              onChange={(e) => setFilter({ ...filter, region: e.target.value || undefined, offset: 0 })}
              className="h-9"
            />
          </div>
        </div>
      </div>

      {/* Main Content Area: Table View or Map View */}
      {viewMode === 'table' ? (
        <Card className="border-border bg-card shadow-sm overflow-hidden">
          <CardHeader className="py-4 border-b border-border flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-semibold text-card-foreground flex items-center gap-2">
              Recorded Telemetry Records ({total})
            </CardTitle>
            <div className="flex items-center gap-2 text-xs text-muted-foreground font-medium">
              <span className="h-2 w-2 rounded-full bg-secondary animate-pulse"></span> AUTO-SYNCING
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {loading ? (
              <div className="p-12 text-center text-muted-foreground text-sm">Loading incident telemetry...</div>
            ) : error ? (
              <div className="p-12 text-center text-red-500 text-sm">{error}</div>
            ) : (incidents ?? []).length === 0 ? (
              <div className="p-12 text-center text-muted-foreground text-sm">No incidents match the selected filter.</div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="border-b border-border bg-muted/60">
                    <TableHead>ID</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Target Address</TableHead>
                    <TableHead>Coordinates</TableHead>
                    <TableHead>Mesh Hops</TableHead>
                    <TableHead>Channel</TableHead>
                    <TableHead>Received At</TableHead>
                    <TableHead className="text-right">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(incidents ?? []).map((inc) => (
                    <TableRow key={inc.id} className="border-b border-border/70 hover:bg-muted/60 transition-colors">
                      <TableCell className="font-mono text-xs text-muted-foreground font-bold">#{inc.id}</TableCell>
                      <TableCell>
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
                        >
                          {inc.incident_type}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            isResponseStatus(inc.status)
                              ? 'warning'
                              : isOpenStatus(inc.status)
                              ? 'destructive'
                              : 'success'
                          }
                        >
                          {inc.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-foreground max-w-[200px] truncate">
                        {typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A'}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {inc.latitude?.toFixed(4)}, {inc.longitude?.toFixed(4)}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-foreground">
                        <span className="px-2 py-0.5 rounded bg-muted border border-border">
                          {inc.hop_count} hops
                        </span>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">{inc.channel}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(inc.received_at || inc.created_at || Date.now()).toLocaleTimeString()}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setSelectedIncident(inc)}
                            title="View incident telemetry & dispatch"
                            className="h-8 w-8 p-0 hover:bg-muted text-muted-foreground hover:text-foreground"
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setReportsIncident(inc)}
                            title="View citizen incident reports"
                            className="h-8 w-8 p-0 hover:bg-muted text-muted-foreground hover:text-foreground"
                          >
                            <FileText className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
          {!loading && !error && (
            (() => {
              const pageSize = filter.limit ?? PAGE_SIZE;
              const currentPage = Math.floor((filter.offset ?? 0) / pageSize) + 1;
              const totalPages = Math.max(1, Math.ceil(total / pageSize));
              const goToPage = (page: number) => {
                const clamped = Math.min(Math.max(1, page), totalPages);
                setFilter({ ...filter, offset: (clamped - 1) * pageSize });
              };
              // Show at most 7 page buttons centered on the current page.
              const windowStart = Math.max(1, Math.min(currentPage - 3, totalPages - 6));
              const windowEnd = Math.min(totalPages, windowStart + 6);
              const pageNumbers = Array.from(
                { length: windowEnd - windowStart + 1 },
                (_, i) => windowStart + i
              );

              return (
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
                      className="h-8 w-8 p-0"
                      title="Previous page"
                    >
                      <ChevronLeft className="h-3.5 w-3.5" />
                    </Button>
                    {windowStart > 1 && (
                      <>
                        <Button size="sm" variant="outline" className="h-8 w-8 p-0" onClick={() => goToPage(1)}>1</Button>
                        {windowStart > 2 && <span className="px-1">…</span>}
                      </>
                    )}
                    {pageNumbers.map((p) => (
                      <Button
                        key={p}
                        size="sm"
                        variant={p === currentPage ? 'default' : 'outline'}
                        className="h-8 w-8 p-0"
                        onClick={() => goToPage(p)}
                      >
                        {p}
                      </Button>
                    ))}
                    {windowEnd < totalPages && (
                      <>
                        {windowEnd < totalPages - 1 && <span className="px-1">…</span>}
                        <Button size="sm" variant="outline" className="h-8 w-8 p-0" onClick={() => goToPage(totalPages)}>
                          {totalPages}
                        </Button>
                      </>
                    )}
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!hasNextPage}
                      onClick={nextPage}
                      className="h-8 w-8 p-0"
                      title="Next page"
                    >
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              );
            })()
          )}
        </Card>
      ) : (
        <div className="h-[600px] rounded-lg overflow-hidden border border-border shadow-sm">
          <IncidentMap
            incidents={incidents ?? []}
            selectedIncidentId={selectedIncident?.id}
            onSelectIncident={(inc) => setSelectedIncident(inc)}
            className="h-full w-full"
          />
        </div>
      )}

      {/* Incident Detail & Status Update Dialog */}
      {selectedIncident && (
        <Dialog open={!!selectedIncident} onOpenChange={() => setSelectedIncident(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-3">
              Incident #{selectedIncident.id}
              <Badge variant="outline" className="font-mono">{selectedIncident.incident_type}</Badge>
              <Badge
                variant={
                  isResponseStatus(selectedIncident.status)
                    ? 'warning'
                    : isOpenStatus(selectedIncident.status)
                    ? 'destructive'
                    : 'success'
                }
              >
                {selectedIncident.status}
              </Badge>
            </DialogTitle>
            <DialogDescription>Spatial Telemetry Inspection & Dispatch Workflow</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2 text-xs text-foreground">
            <div className="p-3.5 bg-muted rounded-lg border border-border space-y-2">
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
                <div className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-[11px] text-red-700">{statusError}</div>
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
                      className="flex-1 text-xs font-bold disabled:opacity-40"
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
                  <div className="p-3 rounded-lg border border-border bg-muted text-muted-foreground">Loading status history...</div>
                ) : statusHistory.length === 0 ? (
                  <div className="p-3 rounded-lg border border-border bg-muted text-muted-foreground">
                    {statusHistoryError || 'No status history has been recorded for this incident.'}
                  </div>
                ) : (
                  statusHistory.map((event) => (
                    <div key={event.id} className="p-3 rounded-lg border border-border bg-muted/70">
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
                      {event.notes && (
                        <div className="mt-2 text-muted-foreground">{event.notes}</div>
                      )}
                      {typeof event.latitude === 'number' && typeof event.longitude === 'number' && (
                        <div className="mt-2 font-mono text-muted-foreground">
                          {event.latitude.toFixed(5)}, {event.longitude.toFixed(5)}
                        </div>
                      )}
                    </div>
                  ))
                )}
                {statusHistoryError && statusHistory.length > 0 && (
                  <div className="text-xs text-red-500">{statusHistoryError}</div>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-semibold text-foreground">Dispatch a Responder Unit:</label>
              <form onSubmit={handleDispatchUnit} className="flex flex-col sm:flex-row gap-2 rounded-lg border border-border bg-muted/40 p-3">
                <Select
                  value={selectedUnitId}
                  onChange={(e) => setSelectedUnitId(e.target.value)}
                  disabled={dispatching || availableUnits.length === 0}
                  className="flex-1"
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
                  disabled={dispatching || !selectedUnitId}
                  className="flex items-center gap-1.5 bg-primary hover:bg-primary/90 whitespace-nowrap"
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
                  <div className="p-3 rounded-lg border border-border bg-muted text-muted-foreground">Loading dispatches...</div>
                ) : dispatches.length === 0 ? (
                  <div className="p-3 rounded-lg border border-border bg-muted text-muted-foreground">
                    {dispatchError || 'No responder units have been dispatched for this incident.'}
                  </div>
                ) : (
                  dispatches.map((dispatch) => (
                    <div key={dispatch.id} className="p-3 rounded-lg border border-border bg-muted/70 space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <div className="font-bold text-foreground">
                            {dispatch.unit_callsign || `Unit #${dispatch.unit_id}`}
                          </div>
                          <div className="text-muted-foreground">
                            {[dispatch.agency_name, dispatch.responder_name].filter(Boolean).join(' / ') || 'Responder assignment'}
                          </div>
                        </div>
                        <Badge variant={dispatch.status === 'COMPLETED' ? 'success' : dispatch.status === 'CANCELLED' ? 'destructive' : 'warning'}>
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
                            className="text-xs font-bold"
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
                  <div className="text-xs text-red-500">{dispatchError}</div>
                )}
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setSelectedIncident(null)}>
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