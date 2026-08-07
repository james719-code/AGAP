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
import { Plus, Trash2, Crosshair } from 'lucide-react';

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

  const statusBadge = (status: string) => {
    switch (status) {
      case 'DISPATCHED':
        return <Badge variant="info" className="text-[10px] font-semibold">DISPATCHED</Badge>;
      case 'IDLE':
        return <Badge variant="success" className="text-[10px] font-semibold">IDLE</Badge>;
      default:
        return <Badge variant="outline" className="text-[10px] font-semibold">{status}</Badge>;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Operations</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Responder Units Roster</h2>
          <p className="mt-1 text-sm text-muted-foreground">Track field responder units, active Target IDs, and live locations</p>
        </div>

        <Button onClick={() => setIsCreateOpen(true)} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
          <Plus className="h-4 w-4" /> Add Responder Unit
        </Button>
      </div>

      {/* Responders Table */}
      <Card className="border-border bg-card shadow-sm">
        <CardHeader className="py-4 border-b border-border">
          <CardTitle className="text-sm font-semibold text-card-foreground">
            Responder Units ({responders.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading responder units...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500 text-sm">{error}</div>
          ) : responders.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">No responders registered yet. Click above to add one.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border bg-muted/60">
                  <TableHead>Unit</TableHead>
                  <TableHead>Agency</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Target ID</TableHead>
                  <TableHead>Coordinates</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {responders.map((unit) => (
                  <TableRow key={unit.id} className="border-b border-border/70 hover:bg-muted/60">
                    <TableCell>
                      <span className="text-sm font-bold text-foreground">{unit.unit_callsign}</span>
                      <div className="text-[11px] text-muted-foreground font-mono">{unit.device_id}</div>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{unit.agency_id}</TableCell>
                    <TableCell>{statusBadge(unit.status)}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground max-w-[200px] truncate">
                      {unit.target_id}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {unit.current_lat?.toFixed(4)}, {unit.current_lon?.toFixed(4)}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            setSelectedLocUnit(unit);
                            setLocLat(unit.current_lat || 13.1391);
                            setLocLon(unit.current_lon || 123.7438);
                            setLocStatus(unit.status);
                          }}
                          className="h-8 w-8 p-0 text-[#1F91A8] hover:bg-secondary/10"
                        >
                          <Crosshair className="h-4 w-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => deleteResponder(unit.id)}
                          className="h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Register Responder Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Crosshair className="h-5 w-5 text-[#1F91A8]" />
            Register New Responder Unit
          </DialogTitle>
          <DialogDescription>Add a field responder unit to the mesh network</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleCreateSubmit} className="space-y-4 py-2 text-sm">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Unit Callsign *</label>
              <Input
                required
                placeholder="e.g. Rescue-1"
                value={formData.unit_callsign}
                onChange={(e) => setFormData({ ...formData, unit_callsign: e.target.value })}
              />
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Agency</label>
              <Select
                value={String(formData.agency_id)}
                onChange={(e) => setFormData({ ...formData, agency_id: parseInt(e.target.value) })}
              >
                {agencies.map((a) => (
                  <option key={a.id} value={String(a.id)}>{a.name}</option>
                ))}
              </Select>
            </div>
          </div>

          <div>
            <label className="text-xs text-muted-foreground mb-1 block">Device ID</label>
            <Input
              value={formData.device_id}
              onChange={(e) => setFormData({ ...formData, device_id: e.target.value })}
            />
          </div>

          <div>
            <label className="text-xs text-muted-foreground mb-1 block">Target ID</label>
            <Input
              value={formData.target_id}
              onChange={(e) => setFormData({ ...formData, target_id: e.target.value })}
            />
          </div>

          <div>
            <label className="text-xs text-muted-foreground mb-1 block">Status</label>
            <Select
              value={formData.status}
              onChange={(e) => setFormData({ ...formData, status: e.target.value })}
            >
              <option value="IDLE">IDLE</option>
              <option value="DISPATCHED">DISPATCHED</option>
              <option value="OFFLINE">OFFLINE</option>
            </Select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Latitude</label>
              <Input
                type="number"
                step="any"
                value={formData.current_lat}
                onChange={(e) => setFormData({ ...formData, current_lat: parseFloat(e.target.value) })}
              />
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Longitude</label>
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
            <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
              {saving ? 'Saving...' : 'Register Unit'}
            </Button>
          </DialogFooter>
        </form>
      </Dialog>

      {/* Update Location & Status Dialog Form */}
      {selectedLocUnit && (
        <Dialog open={!!selectedLocUnit} onOpenChange={() => setSelectedLocUnit(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Crosshair className="h-5 w-5 text-[#1F91A8]" />
              Update Unit GPS Location & Status
            </DialogTitle>
            <DialogDescription>Unit: {selectedLocUnit.unit_callsign}</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleLocationSubmit} className="space-y-4 py-2 text-sm">
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Status</label>
              <Select value={locStatus} onChange={(e) => setLocStatus(e.target.value)}>
                <option value="IDLE">IDLE</option>
                <option value="DISPATCHED">DISPATCHED</option>
                <option value="OFFLINE">OFFLINE</option>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Latitude</label>
                <Input
                  type="number"
                  step="any"
                  value={locLat}
                  onChange={(e) => setLocLat(parseFloat(e.target.value))}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Longitude</label>
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
              <Button type="submit" disabled={saving} className="bg-secondary hover:bg-secondary/90 text-white">
                {saving ? 'Updating...' : 'Update Location'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};


