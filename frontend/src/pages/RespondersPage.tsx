import React, { useEffect, useState } from 'react';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { useAuth } from '@/context/AuthContext';
import { apiService } from '@/services/api';
import type { IncidentDispatch, RescueUnitMember, ResponderUnit, UserAccount } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Crosshair, History, Pencil, Plus, Power, Trash2, Users, UserPlus } from 'lucide-react';

// crypto.randomUUID() is available in all modern evergreen browsers this app targets.
const newTargetId = (agencyId: number) =>
  `05-05-12-01-${String(agencyId).padStart(2, '0')}::${crypto.randomUUID()}`;

const blankResponderForm = (agencyId: number): Partial<ResponderUnit> => ({
  agency_id: agencyId,
  unit_callsign: '',
  device_id: `dev-${Date.now()}`,
  target_id: newTargetId(agencyId),
  status: 'IDLE',
  current_lat: 13.1391,
  current_lon: 123.7438,
});

const formatTimestamp = (value?: string) => {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const ACTIVE_DISPATCH_STATUSES = ['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE'];

export const RespondersPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const {
    responders,
    loading,
    error,
    createResponder,
    updateResponder,
    updateLocation,
    getRescueUnitMembers,
    getRescueUnitDispatches,
    addRescueUnitMember,
    removeRescueUnitMember,
  } = useResponders();
  const { agencies } = useAgencies();

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingUnit, setEditingUnit] = useState<ResponderUnit | null>(null);
  const [selectedLocUnit, setSelectedLocUnit] = useState<ResponderUnit | null>(null);
  const [selectedMemberUnit, setSelectedMemberUnit] = useState<ResponderUnit | null>(null);
  const [selectedHistoryUnit, setSelectedHistoryUnit] = useState<ResponderUnit | null>(null);
  const [unitMembers, setUnitMembers] = useState<RescueUnitMember[]>([]);
  const [unitDispatches, setUnitDispatches] = useState<IncidentDispatch[]>([]);
  const [responderUsers, setResponderUsers] = useState<UserAccount[]>([]);
  const [selectedResponderId, setSelectedResponderId] = useState('');
  const [newMemberIsLeader, setNewMemberIsLeader] = useState(false);
  const [memberError, setMemberError] = useState<string | null>(null);
  const [membersLoading, setMembersLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [formData, setFormData] = useState<Partial<ResponderUnit>>(() => blankResponderForm(0));
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);
  const [toggleTarget, setToggleTarget] = useState<{ unit: ResponderUnit; dispatch: IncidentDispatch } | null>(null);

  // Location update state
  const [locLat, setLocLat] = useState(13.1391);
  const [locLon, setLocLon] = useState(123.7438);
  const [locStatus, setLocStatus] = useState('DISPATCHED');
  const [locError, setLocError] = useState<string | null>(null);

  // Keep the create-form default agency in sync once agencies have loaded,
  // so a create click that fires before the fetch resolves doesn't fall
  // back to a hardcoded (and possibly nonexistent) agency id.
  useEffect(() => {
    queueMicrotask(() => {
      if (!editingUnit && !isCreateOpen && agencies[0]) {
        setFormData((prev) => (prev.agency_id ? prev : blankResponderForm(agencies[0].id)));
      }
    });
  }, [agencies, editingUnit, isCreateOpen]);

  const activeMembers = unitMembers.filter((member) => !member.removed_at);
  const currentDispatch = unitDispatches.find((dispatch) => ACTIVE_DISPATCH_STATUSES.includes(dispatch.status));

  const resetForm = () => {
    setEditingUnit(null);
    setFormError(null);
    setFormData(blankResponderForm(agencies[0]?.id || 0));
  };

  const openCreateDialog = () => {
    resetForm();
    setIsCreateOpen(true);
  };

  const openEditDialog = (unit: ResponderUnit) => {
    setEditingUnit(unit);
    setFormError(null);
    setFormData({
      agency_id: unit.agency_id,
      user_id: unit.user_id,
      unit_callsign: unit.unit_callsign,
      device_id: unit.device_id,
      target_id: unit.target_id,
      status: unit.status,
      current_lat: unit.current_lat,
      current_lon: unit.current_lon,
      responder_name: unit.responder_name,
      responder_phone: unit.responder_phone,
    });
    setIsCreateOpen(true);
  };

  const loadMembers = async (unit: ResponderUnit) => {
    setSelectedMemberUnit(unit);
    setMembersLoading(true);
    setMemberError(null);
    try {
      const [members, users] = await Promise.all([
        getRescueUnitMembers(unit.id),
        apiService.getUsers('RESPONDER'),
      ]);
      setUnitMembers(members);
      setResponderUsers(users.filter((u) => u.account_status === 'APPROVED'));
      setSelectedResponderId('');
      setNewMemberIsLeader(false);
    } catch (err) {
      setMemberError(err instanceof Error ? err.message : 'Failed to load rescue unit members');
    } finally {
      setMembersLoading(false);
    }
  };

  const loadDispatchHistory = async (unit: ResponderUnit) => {
    setSelectedHistoryUnit(unit);
    setUnitDispatches([]);
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const dispatches = await getRescueUnitDispatches(unit.id);
      setUnitDispatches(dispatches);
    } catch (err) {
      setUnitDispatches([]);
      setHistoryError(err instanceof Error ? err.message : 'Failed to load rescue unit deployment history');
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMemberUnit || !selectedResponderId) return;
    setSaving(true);
    setMemberError(null);
    try {
      const member = await addRescueUnitMember(selectedMemberUnit.id, Number(selectedResponderId), newMemberIsLeader);
      setUnitMembers((prev) => [...prev.filter((item) => item.responder_id !== member.responder_id || item.removed_at), member]);
      setSelectedResponderId('');
      setNewMemberIsLeader(false);
    } catch (err) {
      setMemberError(err instanceof Error ? err.message : 'Failed to add rescue unit member');
    } finally {
      setSaving(false);
    }
  };

  const handleRemoveMember = async (member: RescueUnitMember) => {
    if (!selectedMemberUnit) return;
    setSaving(true);
    setMemberError(null);
    try {
      await removeRescueUnitMember(selectedMemberUnit.id, member.responder_id);
      setUnitMembers((prev) =>
        prev.map((item) =>
          item.responder_id === member.responder_id ? { ...item, removed_at: new Date().toISOString() } : item
        )
      );
    } catch (err) {
      setMemberError(err instanceof Error ? err.message : 'Failed to remove rescue unit member');
    } finally {
      setSaving(false);
    }
  };

  const memberName = (responderId: number) => {
    const u = responderUsers.find((item) => Number(item.id) === responderId);
    return u?.name || u?.username || `Responder #${responderId}`;
  };

  const agencyName = (agencyId: number) => agencies.find((a) => a.id === agencyId)?.name || `Agency #${agencyId}`;

  const validateForm = (data: Partial<ResponderUnit>): string | null => {
    if (!data.unit_callsign?.trim()) return 'Unit callsign is required.';
    if (!data.agency_id) return 'An agency must be selected.';
    if (!data.target_id?.trim()) return 'Target ID is required.';
    if (data.current_lat === undefined || Number.isNaN(data.current_lat) || data.current_lat < -90 || data.current_lat > 90) {
      return 'Latitude must be a valid number between -90 and 90.';
    }
    if (data.current_lon === undefined || Number.isNaN(data.current_lon) || data.current_lon < -180 || data.current_lon > 180) {
      return 'Longitude must be a valid number between -180 and 180.';
    }
    return null;
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    const validationError = validateForm(formData);
    if (validationError) {
      setFormError(validationError);
      return;
    }
    setSaving(true);
    try {
      if (editingUnit) {
        await updateResponder(editingUnit.id, formData);
      } else {
        await createResponder(formData);
      }
      setIsCreateOpen(false);
      resetForm();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to save responder unit. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  // Deactivating a unit that's mid-dispatch shouldn't happen silently — check
  // for an active dispatch first and make the admin confirm before we knock
  // a responding unit offline.
  const requestToggleUnitActive = async (unit: ResponderUnit) => {
    setToggleError(null);
    if (unit.status === 'OFFLINE') {
      performToggle(unit);
      return;
    }
    try {
      const dispatches = await getRescueUnitDispatches(unit.id);
      const active = dispatches.find((d) => ACTIVE_DISPATCH_STATUSES.includes(d.status));
      if (active) {
        setToggleTarget({ unit, dispatch: active });
        return;
      }
      performToggle(unit);
    } catch (err) {
      setToggleError(err instanceof Error ? err.message : `Failed to check dispatch status for ${unit.unit_callsign}.`);
    }
  };

  const performToggle = async (unit: ResponderUnit) => {
    setTogglingId(unit.id);
    setToggleError(null);
    try {
      await updateResponder(unit.id, {
        ...unit,
        status: unit.status === 'OFFLINE' ? 'IDLE' : 'OFFLINE',
      });
    } catch (err) {
      setToggleError(err instanceof Error ? err.message : `Failed to update status for ${unit.unit_callsign}.`);
    } finally {
      setTogglingId(null);
      setToggleTarget(null);
    }
  };

  const handleLocationSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLocUnit) return;
    setLocError(null);
    if (Number.isNaN(locLat) || locLat < -90 || locLat > 90 || Number.isNaN(locLon) || locLon < -180 || locLon > 180) {
      setLocError('Enter valid coordinates before updating.');
      return;
    }
    setSaving(true);
    try {
      await updateLocation(selectedLocUnit.id, locLat, locLon, locStatus);
      setSelectedLocUnit(null);
    } catch (err) {
      setLocError(err instanceof Error ? err.message : 'Failed to update location. Please try again.');
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
      case 'OFFLINE':
        return <Badge variant="destructive" className="text-[10px] font-semibold">OFFLINE</Badge>;
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

        {isAdmin && (
          <Button onClick={openCreateDialog} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
            <Plus className="h-4 w-4" /> Add Responder Unit
          </Button>
        )}
      </div>

      {toggleError && (
        <div className="rounded-md border border-red-300 bg-red-50 px-4 py-2 text-sm text-red-700">{toggleError}</div>
      )}

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
            <div className="p-8 text-center text-muted-foreground text-sm">No responders registered yet. {isAdmin ? 'Click above to add one.' : ''}</div>
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
                    <TableCell className="text-xs text-muted-foreground">{agencyName(unit.agency_id)}</TableCell>
                    <TableCell>{statusBadge(unit.status)}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground max-w-[200px] truncate">
                      {unit.target_id}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {unit.current_lat?.toFixed(4)}, {unit.current_lon?.toFixed(4)}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            title="Edit unit"
                            disabled={togglingId === unit.id}
                            onClick={() => openEditDialog(unit)}
                            className="h-8 w-8 p-0 text-primary hover:bg-primary/10"
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          title="Manage members"
                          onClick={() => loadMembers(unit)}
                          className="h-8 w-8 p-0 text-[#2F6F4E] hover:bg-emerald-500/10"
                        >
                          <Users className="h-4 w-4" />
                        </Button>
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            title="Update location"
                            onClick={() => {
                              setSelectedLocUnit(unit);
                              setLocError(null);
                              setLocLat(unit.current_lat || 13.1391);
                              setLocLon(unit.current_lon || 123.7438);
                              setLocStatus(unit.status);
                            }}
                            className="h-8 w-8 p-0 text-[#1F91A8] hover:bg-secondary/10"
                          >
                            <Crosshair className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          title="View deployment history"
                          onClick={() => loadDispatchHistory(unit)}
                          className="h-8 w-8 p-0 text-violet-600 hover:bg-violet-500/10"
                        >
                          <History className="h-4 w-4" />
                        </Button>
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            title={unit.status === 'OFFLINE' ? 'Reactivate unit' : 'Deactivate unit'}
                            disabled={togglingId === unit.id}
                            onClick={() => requestToggleUnitActive(unit)}
                            className={
                              unit.status === 'OFFLINE'
                                ? 'h-8 w-8 p-0 text-emerald-600 hover:bg-emerald-500/10 disabled:opacity-50'
                                : 'h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10 disabled:opacity-50'
                            }
                          >
                            <Power className="h-4 w-4" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Confirm deactivation of a unit with an active dispatch */}
      {toggleTarget && (
        <Dialog open={!!toggleTarget} onOpenChange={() => setToggleTarget(null)}>
          <DialogHeader>
            <DialogTitle className="text-red-600">Deactivate a unit that's currently dispatched?</DialogTitle>
            <DialogDescription>
              {toggleTarget.unit.unit_callsign} is currently {toggleTarget.dispatch.status} on incident #{toggleTarget.dispatch.incident_id}.
              Taking it offline now won't cancel that dispatch — it will just mark the unit unavailable.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setToggleTarget(null)}>
              Cancel
            </Button>
            <Button
              type="button"
              disabled={togglingId === toggleTarget.unit.id}
              onClick={() => performToggle(toggleTarget.unit)}
              className="bg-red-600 hover:bg-red-700 text-white"
            >
              {togglingId === toggleTarget.unit.id ? 'Deactivating...' : 'Deactivate anyway'}
            </Button>
          </DialogFooter>
        </Dialog>
      )}

      {/* Register/Edit Responder Dialog */}
      {isAdmin && (
        <Dialog
          open={isCreateOpen}
          onOpenChange={(open) => {
            setIsCreateOpen(open);
            if (!open) resetForm();
          }}
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {editingUnit ? <Pencil className="h-5 w-5 text-primary" /> : <Crosshair className="h-5 w-5 text-[#1F91A8]" />}
              {editingUnit ? 'Edit Responder Unit' : 'Register New Responder Unit'}
            </DialogTitle>
            <DialogDescription>
              {editingUnit ? 'Update field responder unit details and operational status' : 'Add a field responder unit to the mesh network'}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4 py-2 text-sm">
            {formError && (
              <div className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-700">{formError}</div>
            )}

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
                <label className="text-xs text-muted-foreground mb-1 block">Agency *</label>
                <Select
                  required
                  value={String(formData.agency_id || '')}
                  onChange={(e) => setFormData({ ...formData, agency_id: parseInt(e.target.value, 10) })}
                >
                  <option value="">Select agency</option>
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
              <label className="text-xs text-muted-foreground mb-1 block">Target ID *</label>
              <Input
                required
                value={formData.target_id}
                onChange={(e) => setFormData({ ...formData, target_id: e.target.value })}
              />
              {!editingUnit && (
                <p className="mt-1 text-[11px] text-muted-foreground">Auto-generated per unit — edit only if you know the spatial addressing scheme.</p>
              )}
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
                <label className="text-xs text-muted-foreground mb-1 block">Latitude *</label>
                <Input
                  required
                  type="number"
                  step="any"
                  min={-90}
                  max={90}
                  value={Number.isNaN(formData.current_lat) ? '' : formData.current_lat}
                  onChange={(e) => setFormData({ ...formData, current_lat: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Longitude *</label>
                <Input
                  required
                  type="number"
                  step="any"
                  min={-180}
                  max={180}
                  value={Number.isNaN(formData.current_lon) ? '' : formData.current_lon}
                  onChange={(e) => setFormData({ ...formData, current_lon: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setIsCreateOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
                {saving ? 'Saving...' : editingUnit ? 'Save Changes' : 'Register Unit'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Update Location & Status Dialog Form */}
      {selectedLocUnit && isAdmin && (
        <Dialog open={!!selectedLocUnit} onOpenChange={() => setSelectedLocUnit(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Crosshair className="h-5 w-5 text-[#1F91A8]" />
              Update Unit GPS Location & Status
            </DialogTitle>
            <DialogDescription>Unit: {selectedLocUnit.unit_callsign}</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleLocationSubmit} className="space-y-4 py-2 text-sm">
            {locError && (
              <div className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-700">{locError}</div>
            )}

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
                  min={-90}
                  max={90}
                  value={Number.isNaN(locLat) ? '' : locLat}
                  onChange={(e) => setLocLat(e.target.value === '' ? NaN : parseFloat(e.target.value))}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Longitude</label>
                <Input
                  type="number"
                  step="any"
                  min={-180}
                  max={180}
                  value={Number.isNaN(locLon) ? '' : locLon}
                  onChange={(e) => setLocLon(e.target.value === '' ? NaN : parseFloat(e.target.value))}
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

      {selectedHistoryUnit && (
        <Dialog open={!!selectedHistoryUnit} onOpenChange={() => setSelectedHistoryUnit(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <History className="h-5 w-5 text-violet-600" />
              Deployment History
            </DialogTitle>
            <DialogDescription>Unit: {selectedHistoryUnit.unit_callsign}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 text-sm">
            {historyError && (
              <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-500">
                {historyError}
              </div>
            )}

            <div className="rounded-md border border-border bg-muted/40 p-3">
              <div className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">Current Deployment</div>
              {historyLoading ? (
                <div className="mt-2 text-muted-foreground">Loading deployments...</div>
              ) : currentDispatch ? (
                <div className="mt-2 flex items-start justify-between gap-3">
                  <div>
                    <div className="font-semibold text-foreground">Incident #{currentDispatch.incident_id}</div>
                    <div className="text-[11px] text-muted-foreground">
                      Dispatched {formatTimestamp(currentDispatch.dispatched_at)}
                    </div>
                  </div>
                  <Badge variant="info" className="text-[10px] font-semibold">{currentDispatch.status}</Badge>
                </div>
              ) : (
                <div className="mt-2 text-muted-foreground">No active deployment.</div>
              )}
            </div>

            <div className="max-h-[360px] overflow-y-auto rounded-md border border-border">
              {historyLoading ? (
                <div className="p-4 text-center text-muted-foreground">Loading deployment history...</div>
              ) : unitDispatches.length === 0 ? (
                <div className="p-4 text-center text-muted-foreground">No dispatch history recorded for this unit.</div>
              ) : (
                <div className="divide-y divide-border">
                  {unitDispatches.map((dispatch) => (
                    <div key={dispatch.id} className="p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="font-semibold text-foreground">Incident #{dispatch.incident_id}</div>
                          <div className="text-[11px] text-muted-foreground">
                            {dispatch.agency_name || agencyName(selectedHistoryUnit.agency_id)}
                          </div>
                        </div>
                        <Badge variant={dispatch.status === 'COMPLETED' ? 'success' : dispatch.status === 'CANCELLED' ? 'destructive' : 'info'} className="text-[10px] font-semibold">
                          {dispatch.status}
                        </Badge>
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
                        <span>Dispatched: {formatTimestamp(dispatch.dispatched_at)}</span>
                        <span>Accepted: {formatTimestamp(dispatch.accepted_at)}</span>
                        <span>Departed: {formatTimestamp(dispatch.departed_at)}</span>
                        <span>Arrived: {formatTimestamp(dispatch.arrived_at)}</span>
                        <span>Completed: {formatTimestamp(dispatch.completed_at)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSelectedHistoryUnit(null)}>
                Close
              </Button>
            </DialogFooter>
          </div>
        </Dialog>
      )}

      {selectedMemberUnit && (
        <Dialog open={!!selectedMemberUnit} onOpenChange={() => setSelectedMemberUnit(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Users className="h-5 w-5 text-[#2F6F4E]" />
              Rescue Unit Members
            </DialogTitle>
            <DialogDescription>Unit: {selectedMemberUnit.unit_callsign}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 text-sm">
            {memberError && (
              <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-500">
                {memberError}
              </div>
            )}

            <div className="rounded-md border border-border">
              {membersLoading ? (
                <div className="p-4 text-center text-muted-foreground">Loading members...</div>
              ) : activeMembers.length === 0 ? (
                <div className="p-4 text-center text-muted-foreground">No active responder members assigned.</div>
              ) : (
                <div className="divide-y divide-border">
                  {activeMembers.map((member) => (
                    <div key={member.id} className="flex items-center justify-between gap-3 p-3">
                      <div>
                        <div className="font-semibold text-foreground">{memberName(member.responder_id)}</div>
                        <div className="text-[11px] text-muted-foreground">
                          User #{member.responder_id} - {member.is_leader ? 'Leader' : 'Member'}
                        </div>
                      </div>
                      {isAdmin && (
                        <Button
                          size="sm"
                          variant="ghost"
                          title="Remove member"
                          disabled={saving}
                          onClick={() => handleRemoveMember(member)}
                          className="h-8 w-8 p-0 text-red-500 hover:bg-red-500/10"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {isAdmin && (
              <form onSubmit={handleAddMember} className="space-y-3 rounded-md border border-border bg-muted/40 p-3">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
                  <UserPlus className="h-4 w-4" />
                  Add Responder
                </div>
                <Select
                  value={selectedResponderId}
                  onChange={(e) => setSelectedResponderId(e.target.value)}
                  disabled={saving || membersLoading}
                >
                  <option value="">Select approved responder account</option>
                  {responderUsers
                    .filter((u) => !activeMembers.some((member) => member.responder_id === Number(u.id)))
                    .map((u) => (
                      <option key={u.id} value={String(u.id)}>
                        {u.name || u.username} (#{u.id})
                      </option>
                    ))}
                </Select>
                <label className="flex items-center gap-2 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={newMemberIsLeader}
                    onChange={(e) => setNewMemberIsLeader(e.target.checked)}
                    className="h-4 w-4 rounded border-border"
                  />
                  Assign as unit leader
                </label>
                <DialogFooter className="mt-3">
                  <Button type="button" variant="outline" onClick={() => setSelectedMemberUnit(null)}>
                    Close
                  </Button>
                  <Button type="submit" disabled={saving || !selectedResponderId} className="bg-primary hover:bg-primary/90">
                    {saving ? 'Saving...' : 'Add Member'}
                  </Button>
                </DialogFooter>
              </form>
            )}
            {!isAdmin && (
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setSelectedMemberUnit(null)}>
                  Close
                </Button>
              </DialogFooter>
            )}
          </div>
        </Dialog>
      )}
    </div>
  );
};
