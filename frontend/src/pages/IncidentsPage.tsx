import React, { useCallback, useEffect, useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
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
import { Filter, Eye, RefreshCw, Download, Layers, MapPin } from 'lucide-react';
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

const isOpenStatus = (status: IncidentStatus) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(status);
const isResponseStatus = (status: IncidentStatus) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(status);
const DISPATCH_TRANSITIONS: Record<DispatchStatus, DispatchStatus[]> = {
  ASSIGNED: ['ACCEPTED', 'CANCELLED'],
  ACCEPTED: ['EN_ROUTE', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'CANCELLED'],
  ON_SCENE: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
};

export const IncidentsPage: React.FC = () => {
  const { incidents, filter, setFilter, loading, error, refetch, updateStatus } = useIncidents();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [updating, setUpdating] = useState(false);
  const [dispatches, setDispatches] = useState<IncidentDispatch[]>([]);
  const [dispatchesLoading, setDispatchesLoading] = useState(false);
  const [dispatchError, setDispatchError] = useState<string | null>(null);
  const [statusHistory, setStatusHistory] = useState<IncidentStatusHistory[]>([]);
  const [statusHistoryLoading, setStatusHistoryLoading] = useState(false);
  const [statusHistoryError, setStatusHistoryError] = useState<string | null>(null);
  const [updatingDispatchId, setUpdatingDispatchId] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<'table' | 'map'>('table');

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
      return;
    }

    let cancelled = false;
    setDispatchesLoading(true);
    setDispatchError(null);
    setStatusHistoryLoading(true);
    setStatusHistoryError(null);

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
    setUpdating(true);
    try {
      await updateStatus(selectedIncident.id, newStatus);
      setSelectedIncident({ ...selectedIncident, status: newStatus });
      await loadStatusHistory(selectedIncident.id);
    } catch (err) {
      console.error(err);
    } finally {
      setUpdating(false);
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

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">Distress Incident Registry</h2>
          <p className="text-sm text-muted-foreground mt-1">View, filter, export, and manage distress telemetry packets</p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <Button onClick={() => exportIncidentsToCSV(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9">
            <Download className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" /> Export CSV
          </Button>
          <Button onClick={() => exportIncidentsToJSON(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9">
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
              onChange={(e) => setFilter({ ...filter, type: e.target.value || undefined })}
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
              onChange={(e) => setFilter({ ...filter, status: e.target.value || undefined })}
            >
              <option value="">All Statuses</option>
              {INCIDENT_STATUSES.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </Select>

            <Input
              placeholder="Filter by Region Code (e.g. 05)..."
              value={filter.region || ''}
              onChange={(e) => setFilter({ ...filter, region: e.target.value || undefined })}
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
              Recorded Telemetry Records ({incidents.length})
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
            ) : incidents.length === 0 ? (
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
                  {incidents.map((inc) => (
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
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => setSelectedIncident(inc)}
                          className="h-8 w-8 p-0 hover:bg-muted text-muted-foreground hover:text-foreground"
                        >
                          <Eye className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="h-[600px] rounded-lg overflow-hidden border border-border shadow-sm">
          <IncidentMap
            incidents={incidents}
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
              <div className="flex flex-wrap gap-2">
                {INCIDENT_STATUSES.map((st) => (
                  <Button
                    key={st}
                    size="sm"
                    variant={selectedIncident.status === st ? 'default' : 'outline'}
                    disabled={updating}
                    onClick={() => handleStatusChange(st)}
                    className="flex-1 text-xs font-bold"
                  >
                    {st}
                  </Button>
                ))}
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
    </div>
  );
};
