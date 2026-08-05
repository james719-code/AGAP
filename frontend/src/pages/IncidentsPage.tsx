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
import { Filter, Eye, RefreshCw, Download } from 'lucide-react';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';

export const IncidentsPage: React.FC = () => {
  const { incidents, filter, setFilter, loading, error, refetch, updateStatus } = useIncidents();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [updating, setUpdating] = useState(false);

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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">Distress Incident Registry</h2>
          <p className="text-sm text-slate-400">View, filter, export, and update status of all distress packets</p>
        </div>

        <div className="flex items-center gap-2">
          <Button onClick={() => exportIncidentsToCSV(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs">
            <Download className="h-3.5 w-3.5 text-emerald-400" /> Export CSV
          </Button>
          <Button onClick={() => exportIncidentsToJSON(incidents)} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs">
            <Download className="h-3.5 w-3.5 text-sky-400" /> Export JSON
          </Button>
          <Button onClick={refetch} variant="outline" size="sm" className="flex items-center gap-1.5 text-xs">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
        </div>
      </div>

      {/* Filters Bar */}
      <Card className="p-4 border-slate-800 bg-slate-900/60">
        <div className="flex flex-col sm:flex-row gap-4 items-center">
          <div className="flex items-center gap-2 text-sm text-slate-400 font-medium">
            <Filter className="h-4 w-4 text-emerald-400" /> Filters:
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 flex-1 w-full">
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
            />
          </div>
        </div>
      </Card>

      {/* Incidents Table */}
      <Card className="border-slate-800 bg-slate-900/90">
        <CardHeader className="py-4 border-b border-slate-800">
          <CardTitle className="text-sm font-semibold text-slate-300">
            Total Incidents Recorded ({incidents.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading incident telemetry...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-400 text-sm">{error}</div>
          ) : incidents.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">No incidents match the selected filter.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
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
                  <TableRow key={inc.id}>
                    <TableCell className="font-mono text-xs text-slate-400">#{inc.id}</TableCell>
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
                    <TableCell className="font-mono text-xs text-slate-300 max-w-[200px] truncate">
                      {typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A'}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">
                      {inc.latitude?.toFixed(4)}, {inc.longitude?.toFixed(4)}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">{inc.hop_count}</TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">{inc.channel}</TableCell>
                    <TableCell className="text-xs text-slate-400">
                      {new Date(inc.received_at || inc.created_at || Date.now()).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setSelectedIncident(inc)}
                        className="h-8 w-8 p-0"
                      >
                        <Eye className="h-4 w-4 text-slate-400 hover:text-slate-100" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Incident Detail & Status Update Dialog */}
      {selectedIncident && (
        <Dialog open={!!selectedIncident} onOpenChange={() => setSelectedIncident(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-3">
              Incident #{selectedIncident.id}
              <Badge variant="outline">{selectedIncident.incident_type}</Badge>
            </DialogTitle>
            <DialogDescription>Spatial Telemetry & Dispatch Control</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2 text-xs font-mono text-slate-300">
            <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 space-y-1.5">
              <div><span className="text-slate-500">Packet ID:</span> {selectedIncident.packet_id}</div>
              <div><span className="text-slate-500">Sender Device:</span> {selectedIncident.sender_device_id}</div>
              <div><span className="text-slate-500">Target ID:</span> {typeof selectedIncident.target_id === 'string' ? selectedIncident.target_id : selectedIncident.target_id?.raw}</div>
              <div><span className="text-slate-500">GPS Accuracy:</span> {selectedIncident.gps_accuracy} meters</div>
              <div><span className="text-slate-500">Delivery Path:</span> {selectedIncident.delivery_path}</div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-sans font-medium text-slate-200">Update Incident Dispatch Status:</label>
              <div className="flex gap-2">
                {(['ACTIVE', 'DISPATCHED', 'RESOLVED'] as IncidentStatus[]).map((st) => (
                  <Button
                    key={st}
                    size="sm"
                    variant={selectedIncident.status === st ? 'default' : 'outline'}
                    disabled={updating}
                    onClick={() => handleStatusChange(st)}
                    className="flex-1 text-xs font-semibold"
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
    </div>
  );
};
