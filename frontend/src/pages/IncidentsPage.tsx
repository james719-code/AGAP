import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import type { Incident, IncidentStatus } from '@/types/api';
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
import { Filter, Eye, RefreshCw, Download, Layers, MapPin, UserCheck } from 'lucide-react';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import { DispatchModal } from '@/components/dispatch/DispatchModal';
import { formatTargetLocation } from '@/lib/targetIdFormatter';


export const IncidentsPage: React.FC = () => {
  const { incidents, filter, setFilter, loading, error, refetch, updateStatus } = useIncidents();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [dispatchIncident, setDispatchIncident] = useState<Incident | null>(null);
  const [updating, setUpdating] = useState(false);
  const [viewMode, setViewMode] = useState<'table' | 'map'>('table');

  const handleStatusChange = async (newStatus: IncidentStatus) => {
    if (!selectedIncident) return;
    setUpdating(true);
    try {
      await updateStatus(selectedIncident.id, newStatus);
      setSelectedIncident({ ...selectedIncident, status: newStatus });
    } catch (err) {
      console.error(err);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">Distress Incident Registry</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">View, filter, export, and manage distress telemetry packets</p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <Button onClick={() => exportIncidentsToCSV(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9 border-slate-300 dark:border-slate-800">
            <Download className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> Export CSV
          </Button>
          <Button onClick={() => exportIncidentsToJSON(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9 border-slate-300 dark:border-slate-800">
            <Download className="h-3.5 w-3.5 text-sky-500 dark:text-sky-400" /> Export JSON
          </Button>
          <Button onClick={refetch} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs h-9 border-slate-300 dark:border-slate-800">
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
              <MapPin className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> Map View
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* Filter Bar */}
        <div className="flex-1 flex flex-col sm:flex-row gap-3 items-center bg-white dark:bg-slate-900/60 p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
          <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400 font-semibold px-1">
            <Filter className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> Filters:
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
            </Select>

            <Select
              value={filter.status || ''}
              onChange={(e) => setFilter({ ...filter, status: e.target.value || undefined })}
            >
              <option value="">All Statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="DISPATCHED">DISPATCHED</option>
              <option value="RESOLVED">RESOLVED</option>
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
        <Card className="border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 shadow-xl overflow-hidden">
          <CardHeader className="py-4 border-b border-slate-200 dark:border-slate-800 flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-semibold text-slate-800 dark:text-slate-300 flex items-center gap-2">
              Recorded Telemetry Records ({incidents.length})
            </CardTitle>
            <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400 font-mono">
              <span className="h-2 w-2 rounded-full bg-emerald-500 dark:bg-emerald-400 animate-pulse"></span> AUTO-SYNCING
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {loading ? (
              <div className="p-12 text-center text-slate-500 text-sm">Loading incident telemetry...</div>
            ) : error ? (
              <div className="p-12 text-center text-red-400 text-sm">{error}</div>
            ) : incidents.length === 0 ? (
              <div className="p-12 text-center text-slate-500 text-sm">No incidents match the selected filter.</div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-950/40">
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
                    <TableRow key={inc.id} className="border-b border-slate-100 dark:border-slate-800/60 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                      <TableCell className="font-mono text-xs text-slate-500 dark:text-slate-400 font-bold">#{inc.id}</TableCell>
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
                            inc.status === 'ACTIVE'
                              ? 'destructive'
                              : inc.status === 'DISPATCHED'
                              ? 'warning'
                              : 'success'
                          }
                        >
                          {inc.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="font-sans text-xs text-slate-900 dark:text-slate-100 font-semibold max-w-[240px]">
                        <div>{formatTargetLocation(inc.target_id, inc.latitude, inc.longitude).readable}</div>
                        <div className="font-mono text-[10px] text-slate-400 font-normal truncate" title={formatTargetLocation(inc.target_id).raw}>
                          {formatTargetLocation(inc.target_id, inc.latitude, inc.longitude).subText}
                        </div>
                      </TableCell>

                      <TableCell className="font-mono text-xs text-slate-500 dark:text-slate-400">
                        {inc.latitude?.toFixed(4)}, {inc.longitude?.toFixed(4)}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-slate-700 dark:text-slate-300">
                        <span className="px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
                          {inc.hop_count} hops
                        </span>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-slate-500 dark:text-slate-400">{inc.channel}</TableCell>
                      <TableCell className="text-xs text-slate-500 dark:text-slate-400">
                        {new Date(inc.received_at || inc.created_at || Date.now()).toLocaleTimeString()}
                      </TableCell>
                      <TableCell className="text-right flex items-center justify-end gap-1">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setDispatchIncident(inc)}
                          className="h-8 text-[11px] font-bold border-red-500/30 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40"
                        >
                          <UserCheck className="h-3.5 w-3.5 mr-1" /> Dispatch
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => setSelectedIncident(inc)}
                          className="h-8 w-8 p-0 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100"
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
        <div className="h-[600px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl">
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
                  selectedIncident.status === 'ACTIVE'
                    ? 'destructive'
                    : selectedIncident.status === 'DISPATCHED'
                    ? 'warning'
                    : 'success'
                }
              >
                {selectedIncident.status}
              </Badge>
            </DialogTitle>
            <DialogDescription>Spatial Telemetry Inspection & Dispatch Workflow</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2 text-xs font-mono text-slate-300">
            <div className="p-3.5 bg-slate-950 rounded-xl border border-slate-800 space-y-2">
              <div className="flex justify-between border-b border-slate-800/80 pb-1.5">
                <span className="text-slate-500">Packet ID:</span>
                <span className="text-slate-200">{selectedIncident.packet_id}</span>
              </div>
              <div className="flex justify-between border-b border-slate-800/80 pb-1.5">
                <span className="text-slate-500">Sender Device:</span>
                <span className="text-slate-200">{selectedIncident.sender_device_id}</span>
              </div>
              <div className="flex flex-col border-b border-slate-800/80 pb-1.5 gap-0.5">
                <span className="text-slate-500 font-sans">Target Address:</span>
                <span className="text-emerald-400 font-bold font-sans text-xs">
                  {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
                </span>
                <span className="text-slate-400 text-[10px] font-mono">
                  {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}
                </span>
              </div>

              <div className="flex justify-between border-b border-slate-800/80 pb-1.5">
                <span className="text-slate-500">GPS Accuracy:</span>
                <span className="text-slate-200">{selectedIncident.gps_accuracy} meters</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Mesh Hop Routing:</span>
                <span className="text-sky-400">{selectedIncident.delivery_path || `Direct (${selectedIncident.hop_count} hops)`}</span>
              </div>
            </div>

            <div className="space-y-2 font-sans">
              <label className="text-xs font-semibold text-slate-300">Change Dispatch Status:</label>
              <div className="flex gap-2">
                {(['ACTIVE', 'DISPATCHED', 'RESOLVED'] as IncidentStatus[]).map((st) => (
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
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setSelectedIncident(null)}>
              Close
            </Button>
          </DialogFooter>
        </Dialog>
      )}

      {/* Dispatch Unit Modal */}
      {dispatchIncident && (
        <DispatchModal
          isOpen={!!dispatchIncident}
          onClose={() => setDispatchIncident(null)}
          incidentId={dispatchIncident.id}
          emergencyType={dispatchIncident.incident_type}
          onDispatchSuccess={() => {
            updateStatus(dispatchIncident.id, 'DISPATCHED');
          }}
        />
      )}
    </div>
  );
};
