import React, { useState, useEffect } from 'react';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { apiService } from '@/services/api';
import type { ResponderUnit, UserAccount } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Plus, Trash2, Users, MapPin, Phone, UserCheck, ShieldCheck } from 'lucide-react';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { TargetLocationPicker } from '@/components/common/TargetLocationPicker';


export const RespondersPage: React.FC = () => {
  const { responders, loading, error, createResponder, updateLocation, deleteResponder } = useResponders();
  const { agencies } = useAgencies();

  const [responderUsers, setResponderUsers] = useState<UserAccount[]>([]);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedLocUnit, setSelectedLocUnit] = useState<ResponderUnit | null>(null);

  useEffect(() => {
    apiService.getUsers('RESPONDER')
      .then((users) => setResponderUsers(users))
      .catch((err) => console.warn('Failed to load responder users:', err));
  }, []);

  const [formData, setFormData] = useState<Partial<ResponderUnit>>({
    agency_id: 101,
    unit_callsign: '',
    responder_name: '',
    responder_phone: '',
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

  const getAgencyName = (agencyId: number) => {
    const found = agencies.find((a) => a.id === agencyId);
    if (found) return found.name;
    if (agencyId === 101 || agencyId === 1) return 'CDRRMO Legazpi Command Center';
    if (agencyId === 102 || agencyId === 2) return 'BFP Daraga Fire Station';
    if (agencyId === 103 || agencyId === 3) return 'PNP Albay Police HQ';
    if (agencyId === 104 || agencyId === 4) return 'Red Cross Albay Chapter';
    if (agencyId === 105) return 'Philippine Coast Guard Albay';
    return `Agency #${agencyId}`;
  };

  const handleSelectResponderUser = (userIdStr: string) => {
    if (!userIdStr || userIdStr === 'custom') {
      setFormData((prev) => ({
        ...prev,
        user_id: undefined,
        responder_name: '',
        responder_phone: '',
      }));
      return;
    }

    const uId = parseInt(userIdStr, 10);
    const foundUser = responderUsers.find((u) => u.id === uId);
    if (foundUser) {
      setFormData((prev) => ({
        ...prev,
        user_id: uId,
        responder_name: foundUser.name || foundUser.username,
        responder_phone: foundUser.phone_number || '',
        agency_id: foundUser.agency_id || prev.agency_id || 101,
      }));
    }
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createResponder(formData);
      setIsCreateOpen(false);
      setFormData({
        agency_id: agencies[0]?.id || 101,
        unit_callsign: '',
        responder_name: '',
        responder_phone: '',
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
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">Responder Personnel & Fleet Roster</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">Track individual field responders, unit callsigns, operating agencies, and live locations</p>
        </div>

        <Button onClick={() => setIsCreateOpen(true)} className="bg-emerald-600 hover:bg-emerald-700 text-white flex items-center gap-2 font-semibold">
          <Plus className="h-4 w-4" /> Add Responder Unit
        </Button>
      </div>

      {/* Responders Table */}
      <Card className="border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 shadow-lg">
        <CardHeader className="py-4 border-b border-slate-200 dark:border-slate-800">
          <CardTitle className="text-sm font-semibold text-slate-800 dark:text-slate-300">
            Registered Field Responders & Fleet Units ({responders.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading responder personnel...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-400 text-sm">{error}</div>
          ) : responders.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">No responder units added yet. Click above to register a unit.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-950/40">
                  <TableHead>Callsign & Assigned Responder</TableHead>
                  <TableHead>Operating Agency</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Target Address</TableHead>
                  <TableHead>Device Hardware ID</TableHead>
                  <TableHead>Current Location</TableHead>
                  <TableHead>Last Active</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {responders.map((unit) => {
                  const loc = formatTargetLocation(unit.target_id, unit.current_lat, unit.current_lon);
                  const agencyName = getAgencyName(unit.agency_id);

                  return (
                    <TableRow key={unit.id} className="border-b border-slate-100 dark:border-slate-800/60 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                      <TableCell className="py-3">
                        <div className="font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2 text-xs">
                          <Users className="h-4 w-4 text-sky-500 shrink-0" />
                          <span>{unit.unit_callsign}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-semibold text-[11px] mt-1 pl-6">
                          <UserCheck className="h-3 w-3 shrink-0" />
                          <span>{unit.responder_name || 'Officer Juan Dela Cruz'}</span>
                          {unit.responder_phone && (
                            <span className="text-slate-400 font-mono text-[10px] ml-1 flex items-center gap-0.5">
                              <Phone className="h-2.5 w-2.5" /> {unit.responder_phone}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-xs font-medium text-slate-700 dark:text-slate-300 max-w-[180px] truncate" title={agencyName}>
                        <Badge variant="outline" className="text-[10px] border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800">
                          {agencyName}
                        </Badge>
                      </TableCell>
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
                      <TableCell className="text-xs max-w-[200px]">
                        <div className="font-semibold text-slate-800 dark:text-slate-200 truncate">{loc.readable}</div>
                        <div className="font-mono text-[10px] text-slate-400 truncate">{loc.subText}</div>
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
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
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
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
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
              Register Responder Personnel & Unit
            </DialogTitle>
            <DialogDescription>Assign callsign, lead responder officer, and spatial target ID to a field unit</DialogDescription>
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
                <label className="text-xs text-slate-400 mb-1 block">Operating Agency *</label>
                <Select
                  value={formData.agency_id}
                  onChange={(e) => setFormData({ ...formData, agency_id: parseInt(e.target.value, 10) })}
                >
                  {agencies.length === 0 ? (
                    <option value={101}>CDRRMO Legazpi Command Center</option>
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

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Assigned Responder Officer (Registered Account) *</label>
                <Select
                  value={formData.user_id ? formData.user_id.toString() : 'custom'}
                  onChange={(e) => handleSelectResponderUser(e.target.value)}
                >
                  <option value="custom">Select registered responder account...</option>
                  {responderUsers.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name || u.username} ({u.role}) {u.phone_number ? `- ${u.phone_number}` : ''}
                    </option>
                  ))}
                </Select>
                {(!formData.user_id || formData.user_id === 0) && (
                  <Input
                    required
                    className="mt-2 text-xs"
                    placeholder="Enter custom officer name (e.g. Officer Juan Dela Cruz)"
                    value={formData.responder_name || ''}
                    onChange={(e) => setFormData({ ...formData, responder_name: e.target.value })}
                  />
                )}
              </div>

              <div>
                <label className="text-xs text-slate-400 mb-1 block">Personnel Contact Phone</label>
                <Input
                  placeholder="e.g. +63 917 888 1001"
                  value={formData.responder_phone || ''}
                  onChange={(e) => setFormData({ ...formData, responder_phone: e.target.value })}
                />
              </div>
            </div>


            <TargetLocationPicker
              value={formData.target_id || '05-05-12-01-04::LEGAZPI-PORT-PRECINCT'}
              onChange={(newTargetId, lat, lon) => {
                setFormData((prev) => ({
                  ...prev,
                  target_id: newTargetId,
                  current_lat: lat !== undefined ? lat : prev.current_lat,
                  current_lon: lon !== undefined ? lon : prev.current_lon,
                }));
              }}
              initialLat={formData.current_lat}
              initialLon={formData.current_lon}
            />



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
