import React, { useState } from 'react';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import type { ResponderUnit } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Plus, Trash2, Users, MapPin } from 'lucide-react';

export const RespondersPage: React.FC = () => {
  const { responders, loading, error, createResponder, updateLocation, deleteResponder } = useResponders();
  const { agencies } = useAgencies();

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedLocUnit, setSelectedLocUnit] = useState<ResponderUnit | null>(null);

  const [formData, setFormData] = useState<Partial<ResponderUnit>>({
    agency_id: 1,
    unit_callsign: '',
    device_id: `dev-${Date.now()}`,
    target_id: '05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111',
    status: 'IDLE',
    current_lat: 13.1391,
    current_lon: 123.7438,
  });
  const [saving, setSaving] = useState(false);

  // Location update state
  const [locLat, setLocLat] = useState(13.1391);
  const [locLon, setLocLon] = useState(123.7438);
  const [locStatus, setLocStatus] = useState('DISPATCHED');

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createResponder(formData);
      setIsCreateOpen(false);
      setFormData({
        agency_id: agencies[0]?.id || 1,
        unit_callsign: '',
        device_id: `dev-${Date.now()}`,
        target_id: '05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111',
        status: 'IDLE',
        current_lat: 13.1391,
        current_lon: 123.7438,
      });
    } catch (err) {
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const handleLocationSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLocUnit) return;
    setSaving(true);
    try {
      await updateLocation(selectedLocUnit.id, locLat, locLon, locStatus);
      setSelectedLocUnit(null);
    } catch (err) {
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">Responder Units Roster</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">Track field responder units, active Target IDs, and live locations</p>
        </div>

        <Button onClick={() => setIsCreateOpen(true)} className="bg-emerald-600 hover:bg-emerald-700 text-white flex items-center gap-2 font-semibold">
          <Plus className="h-4 w-4" /> Add Responder Unit
        </Button>
      </div>

      {/* Responders Table */}
      <Card className="border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 shadow-lg">
        <CardHeader className="py-4 border-b border-slate-200 dark:border-slate-800">
          <CardTitle className="text-sm font-semibold text-slate-800 dark:text-slate-300">
            Registered Field Units ({responders.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading responder units...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-400 text-sm">{error}</div>
          ) : responders.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">No responder units added yet. Click above to register a unit.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-950/40">
                  <TableHead>Callsign</TableHead>
                  <TableHead>Agency ID</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Target ID Address</TableHead>
                  <TableHead>Device UUID</TableHead>
                  <TableHead>Current Location</TableHead>
                  <TableHead>Last Seen</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {responders.map((unit) => (
                  <TableRow key={unit.id} className="border-b border-slate-100 dark:border-slate-800/60 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                    <TableCell className="font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                      <Users className="h-4 w-4 text-sky-500 dark:text-sky-400" />
                      {unit.unit_callsign}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-500 dark:text-slate-400">Agency #{unit.agency_id}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          unit.status === 'IDLE'
                            ? 'success'
                            : unit.status === 'DISPATCHED'
                            ? 'warning'
                            : 'default'
                        }
                      >
                        {unit.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-700 dark:text-slate-300 max-w-[180px] truncate">
                      {unit.target_id}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-500 dark:text-slate-400 max-w-[120px] truncate">
                      {unit.device_id}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">
                      {unit.current_lat?.toFixed(4)}, {unit.current_lon?.toFixed(4)}
                    </TableCell>
                    <TableCell className="text-xs text-slate-400">
                      {unit.last_seen_at ? new Date(unit.last_seen_at).toLocaleTimeString() : 'Recently'}
                    </TableCell>
                    <TableCell className="text-right flex items-center justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        title="Update GPS Location"
                        onClick={() => {
                          setSelectedLocUnit(unit);
                          setLocLat(unit.current_lat || 13.1391);
                          setLocLon(unit.current_lon || 123.7438);
                          setLocStatus(unit.status || 'DISPATCHED');
                        }}
                        className="h-8 w-8 p-0 text-sky-400 hover:text-sky-300 hover:bg-sky-950/50"
                      >
                        <MapPin className="h-4 w-4" />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => deleteResponder(unit.id)}
                        className="h-8 w-8 p-0 text-red-400 hover:text-red-300 hover:bg-red-950/50"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Create Responder Unit Dialog Form */}
      {isCreateOpen && (
        <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Users className="h-5 w-5 text-sky-400" />
              Register Responder Unit
            </DialogTitle>
            <DialogDescription>Assign callsign and target spatial ID to a field mobile unit</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4 py-2 text-sm">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Unit Callsign *</label>
                <Input
                  required
                  placeholder="e.g. DRRMO-ALPHA-1"
                  value={formData.unit_callsign}
                  onChange={(e) => setFormData({ ...formData, unit_callsign: e.target.value })}
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 mb-1 block">Agency *</label>
                <Select
                  value={formData.agency_id}
                  onChange={(e) => setFormData({ ...formData, agency_id: parseInt(e.target.value, 10) })}
                >
                  {agencies.length === 0 ? (
                    <option value={1}>Agency #1</option>
                  ) : (
                    agencies.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.code} - {a.name}
                      </option>
                    ))
                  )}
                </Select>
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 mb-1 block">Target Spatial ID *</label>
              <Input
                required
                placeholder="05-05-12-01-04::UUID"
                value={formData.target_id}
                onChange={(e) => setFormData({ ...formData, target_id: e.target.value })}
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 mb-1 block">Mobile Device ID UUID</label>
              <Input
                value={formData.device_id}
                onChange={(e) => setFormData({ ...formData, device_id: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Initial Status</label>
                <Select
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                >
                  <option value="IDLE">IDLE</option>
                  <option value="DISPATCHED">DISPATCHED</option>
                  <option value="OFFLINE">OFFLINE</option>
                </Select>
              </div>
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Latitude</label>
                <Input
                  type="number"
                  step="any"
                  value={formData.current_lat}
                  onChange={(e) => setFormData({ ...formData, current_lat: parseFloat(e.target.value) })}
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Longitude</label>
                <Input
                  type="number"
                  step="any"
                  value={formData.current_lon}
                  onChange={(e) => setFormData({ ...formData, current_lon: parseFloat(e.target.value) })}
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-emerald-600 hover:bg-emerald-700">
                {saving ? 'Saving...' : 'Register Unit'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Update Location & Status Dialog Form */}
      {selectedLocUnit && (
        <Dialog open={!!selectedLocUnit} onOpenChange={() => setSelectedLocUnit(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <MapPin className="h-5 w-5 text-sky-400" />
              Update Unit GPS Location & Status
            </DialogTitle>
            <DialogDescription>Unit: {selectedLocUnit.unit_callsign}</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleLocationSubmit} className="space-y-4 py-2 text-sm">
            <div>
              <label className="text-xs text-slate-400 mb-1 block">Status</label>
              <Select value={locStatus} onChange={(e) => setLocStatus(e.target.value)}>
                <option value="IDLE">IDLE</option>
                <option value="DISPATCHED">DISPATCHED</option>
                <option value="OFFLINE">OFFLINE</option>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Latitude</label>
                <Input
                  type="number"
                  step="any"
                  value={locLat}
                  onChange={(e) => setLocLat(parseFloat(e.target.value))}
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Longitude</label>
                <Input
                  type="number"
                  step="any"
                  value={locLon}
                  onChange={(e) => setLocLon(parseFloat(e.target.value))}
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSelectedLocUnit(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-sky-600 hover:bg-sky-700">
                {saving ? 'Updating...' : 'Update Location'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};
