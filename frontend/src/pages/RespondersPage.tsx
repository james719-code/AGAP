import React, { useEffect, useState } from 'react';
import { useResponders } from '@/hooks/useResponders';
import { useAgencies } from '@/hooks/useAgencies';
import { useAuth } from '@/context/AuthContext';
import { apiService } from '@/services/api';
import type { IncidentDispatch, RescueUnitMember, ResponderUnit, UserAccount } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Crosshair, History, Pencil, Plus, Power, Trash2, Users, UserPlus } from 'lucide-react';
import { isResponderActive } from '@/lib/responderStatus';

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

  const statusBadge = (unit: ResponderUnit) => {
    const status = unit.status;
    if (!isResponderActive(unit)) {
      return <Badge variant="destructive" className="rounded-[2px] text-[10px] font-semibold">STALE / OFFLINE</Badge>;
    }
    switch (status) {
      case 'DISPATCHED':
        return <Badge variant="info" className="rounded-[2px] text-[10px] font-semibold">DISPATCHED</Badge>;
      case 'IDLE':
        return <Badge variant="success" className="rounded-[2px] text-[10px] font-semibold">IDLE</Badge>;
      case 'OFFLINE':
        return <Badge variant="destructive" className="rounded-[2px] text-[10px] font-semibold">OFFLINE</Badge>;
      default:
        return <Badge variant="outline" className="rounded-[2px] text-[10px] font-semibold">{status}</Badge>;
    }
  };

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-[#D15033]">Operations</span>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white">
            Responder Units Roster
          </h2>
          <p className="mt-1 text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Track field responder units, active Target IDs, and live locations
          </p>
        </div>

        {isAdmin && (
          <Button
            onClick={openCreateDialog}
            className="bg-[#D15033] hover:bg-[#B5432A] text-white flex items-center gap-2 font-semibold rounded-[2px] shrink-0"
          >
            <Plus className="h-4 w-4" /> Add Responder Unit
          </Button>
        )}
      </div>

      {toggleError && (
        <div className="rounded-[2px] border border-red-300 bg-red-50 dark:bg-red-900/20 px-4 py-2 text-sm text-red-700 dark:text-red-300">
          {toggleError}
        </div>
      )}

      {/* Responders Table */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        <div className="py-3 px-4 border-b border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white">
            Responder Units ({responders.length})
          </h3>
        </div>

        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">Loading responder units...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-600 dark:text-red-400 text-sm">{error}</div>
          ) : responders.length === 0 ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">
              No responders registered yet. {isAdmin ? 'Click above to add one.' : ''}
            </div>
          ) : (
            <table className="w-full text-xs">
              <thead className="bg-[#F7F5F2] dark:bg-[#181C1D] border-b border-[#E7E3DC] dark:border-[#26292C]">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Unit</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Agency</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Status</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Target ID</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Coordinates</th>
                  <th className="text-right px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Actions</th>
                </tr>
              </thead>
              <tbody>
                {responders.map((unit) => (
                  <tr key={unit.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors">
                    <td className="px-4 py-3">
                      <span className="text-sm font-bold text-[#0A0A0A] dark:text-white">{unit.unit_callsign}</span>
                      <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] font-mono">{unit.device_id}</div>
                    </td>
                    <td className="px-4 py-3 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">{agencyName(unit.agency_id)}</td>
                    <td className="px-4 py-3">{statusBadge(unit)}</td>
                    <td className="px-4 py-3 font-mono text-xs text-[#5C5A56] dark:text-[#B9B6B0] max-w-[200px] truncate">
                      {unit.target_id}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
                      {unit.current_lat?.toFixed(4)}, {unit.current_lon?.toFixed(4)}
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                      <div className="flex items-center justify-end gap-1">
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            title="Edit unit"
                            disabled={togglingId === unit.id}
                            onClick={() => openEditDialog(unit)}
                            className="h-8 w-8 p-0 rounded-[2px] text-[#D15033] hover:bg-[#D15033]/10"
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          title="Manage members"
                          onClick={() => loadMembers(unit)}
                          className="h-8 w-8 p-0 rounded-[2px] text-[#2F6F4E] hover:bg-emerald-500/10"
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
                            className="h-8 w-8 p-0 rounded-[2px] text-[#1F91A8] hover:bg-[#1F91A8]/10"
                          >
                            <Crosshair className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          title="View deployment history"
                          onClick={() => loadDispatchHistory(unit)}
                          className="h-8 w-8 p-0 rounded-[2px] text-violet-600 hover:bg-violet-500/10"
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
                            className={`h-8 w-8 p-0 rounded-[2px] ${
                              unit.status === 'OFFLINE'
                                ? 'text-emerald-600 hover:bg-emerald-500/10 disabled:opacity-50'
                                : 'text-red-500 hover:text-red-600 hover:bg-red-500/10 disabled:opacity-50'
                            }`}
                          >
                            <Power className="h-4 w-4" />
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Confirm deactivation of a unit with an active dispatch */}
      {toggleTarget && (
        <Dialog open={!!toggleTarget} onOpenChange={() => setToggleTarget(null)}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-md w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6">
              <DialogHeader>
                <DialogTitle className="text-lg font-bold text-red-600 dark:text-red-400">
                  Deactivate a unit that's currently dispatched?
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0] mt-2">
                  {toggleTarget.unit.unit_callsign} is currently {toggleTarget.dispatch.status} on incident #{toggleTarget.dispatch.incident_id}.
                  Taking it offline now won't cancel that dispatch — it will just mark the unit unavailable.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter className="mt-6 flex justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setToggleTarget(null)}
                  className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  disabled={togglingId === toggleTarget.unit.id}
                  onClick={() => performToggle(toggleTarget.unit)}
                  className="rounded-[2px] bg-red-600 hover:bg-red-700 text-white"
                >
                  {togglingId === toggleTarget.unit.id ? 'Deactivating...' : 'Deactivate anyway'}
                </Button>
              </DialogFooter>
            </div>
          </div>
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
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-2xl w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  {editingUnit ? <Pencil className="h-5 w-5 text-[#D15033]" /> : <Crosshair className="h-5 w-5 text-[#1F91A8]" />}
                  {editingUnit ? 'Edit Responder Unit' : 'Register New Responder Unit'}
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  {editingUnit ? 'Update field responder unit details and operational status' : 'Add a field responder unit to the mesh network'}
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleCreateSubmit} className="space-y-4 py-2 text-sm">
                {formError && (
                  <div className="rounded-[2px] border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-xs text-red-700 dark:text-red-300">
                    {formError}
                  </div>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Unit Callsign *</label>
                    <Input
                      required
                      placeholder="e.g. Rescue-1"
                      value={formData.unit_callsign}
                      onChange={(e) => setFormData({ ...formData, unit_callsign: e.target.value })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Agency *</label>
                    <Select
                      required
                      value={String(formData.agency_id || '')}
                      onChange={(e) => setFormData({ ...formData, agency_id: parseInt(e.target.value, 10) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="">Select agency</option>
                      {agencies.map((a) => (
                        <option key={a.id} value={String(a.id)}>{a.name}</option>
                      ))}
                    </Select>
                  </div>
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Device ID</label>
                  <Input
                    value={formData.device_id}
                    onChange={(e) => setFormData({ ...formData, device_id: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  />
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Target ID *</label>
                  <Input
                    required
                    value={formData.target_id}
                    onChange={(e) => setFormData({ ...formData, target_id: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  />
                  {!editingUnit && (
                    <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                      Auto-generated per unit — edit only if you know the spatial addressing scheme.
                    </p>
                  )}
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Status</label>
                  <Select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  >
                    <option value="IDLE">IDLE</option>
                    <option value="DISPATCHED">DISPATCHED</option>
                    <option value="OFFLINE">OFFLINE</option>
                  </Select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Latitude *</label>
                    <Input
                      required
                      type="number"
                      step="any"
                      min={-90}
                      max={90}
                      value={Number.isNaN(formData.current_lat) ? '' : formData.current_lat}
                      onChange={(e) => setFormData({ ...formData, current_lat: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Longitude *</label>
                    <Input
                      required
                      type="number"
                      step="any"
                      min={-180}
                      max={180}
                      value={Number.isNaN(formData.current_lon) ? '' : formData.current_lon}
                      onChange={(e) => setFormData({ ...formData, current_lon: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                </div>

                <DialogFooter className="mt-6 flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => { setIsCreateOpen(false); resetForm(); }}
                    className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    disabled={saving}
                    className="rounded-[2px] bg-[#D15033] hover:bg-[#B5432A] text-white"
                  >
                    {saving ? 'Saving...' : editingUnit ? 'Save Changes' : 'Register Unit'}
                  </Button>
                </DialogFooter>
              </form>
            </div>
          </div>
        </Dialog>
      )}

      {/* Update Location & Status Dialog Form */}
      {selectedLocUnit && isAdmin && (
        <Dialog open={!!selectedLocUnit} onOpenChange={() => setSelectedLocUnit(null)}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-md w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  <Crosshair className="h-5 w-5 text-[#1F91A8]" />
                  Update Unit GPS Location & Status
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  Unit: {selectedLocUnit.unit_callsign}
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleLocationSubmit} className="space-y-4 py-2 text-sm">
                {locError && (
                  <div className="rounded-[2px] border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-xs text-red-700 dark:text-red-300">
                    {locError}
                  </div>
                )}

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Status</label>
                  <Select
                    value={locStatus}
                    onChange={(e) => setLocStatus(e.target.value)}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  >
                    <option value="IDLE">IDLE</option>
                    <option value="DISPATCHED">DISPATCHED</option>
                    <option value="OFFLINE">OFFLINE</option>
                  </Select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Latitude</label>
                    <Input
                      type="number"
                      step="any"
                      min={-90}
                      max={90}
                      value={Number.isNaN(locLat) ? '' : locLat}
                      onChange={(e) => setLocLat(e.target.value === '' ? NaN : parseFloat(e.target.value))}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Longitude</label>
                    <Input
                      type="number"
                      step="any"
                      min={-180}
                      max={180}
                      value={Number.isNaN(locLon) ? '' : locLon}
                      onChange={(e) => setLocLon(e.target.value === '' ? NaN : parseFloat(e.target.value))}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                </div>

                <DialogFooter className="mt-6 flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setSelectedLocUnit(null)}
                    className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    disabled={saving}
                    className="rounded-[2px] bg-[#1F91A8] hover:bg-[#17809A] text-white"
                  >
                    {saving ? 'Updating...' : 'Update Location'}
                  </Button>
                </DialogFooter>
              </form>
            </div>
          </div>
        </Dialog>
      )}

      {selectedHistoryUnit && (
        <Dialog open={!!selectedHistoryUnit} onOpenChange={() => setSelectedHistoryUnit(null)}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-2xl w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  <History className="h-5 w-5 text-violet-600" />
                  Deployment History
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  Unit: {selectedHistoryUnit.unit_callsign}
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-4 text-sm">
                {historyError && (
                  <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-400">
                    {historyError}
                  </div>
                )}

                <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] p-3">
                  <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
                    Current Deployment
                  </div>
                  {historyLoading ? (
                    <div className="mt-2 text-[#5C5A56] dark:text-[#B9B6B0]">Loading deployments...</div>
                  ) : currentDispatch ? (
                    <div className="mt-2 flex items-start justify-between gap-3">
                      <div>
                        <div className="font-semibold text-[#0A0A0A] dark:text-white">Incident #{currentDispatch.incident_id}</div>
                        <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                          Dispatched {formatTimestamp(currentDispatch.dispatched_at)}
                        </div>
                      </div>
                      <Badge variant="info" className="rounded-[2px] text-[10px] font-semibold">{currentDispatch.status}</Badge>
                    </div>
                  ) : (
                    <div className="mt-2 text-[#5C5A56] dark:text-[#B9B6B0]">No active deployment.</div>
                  )}
                </div>

                <div className="max-h-[360px] overflow-y-auto rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C]">
                  {historyLoading ? (
                    <div className="p-4 text-center text-[#5C5A56] dark:text-[#B9B6B0]">Loading deployment history...</div>
                  ) : unitDispatches.length === 0 ? (
                    <div className="p-4 text-center text-[#5C5A56] dark:text-[#B9B6B0]">No dispatch history recorded for this unit.</div>
                  ) : (
                    <div className="divide-y divide-[#E7E3DC] dark:divide-[#26292C]">
                      {unitDispatches.map((dispatch) => (
                        <div key={dispatch.id} className="p-3">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <div className="font-semibold text-[#0A0A0A] dark:text-white">Incident #{dispatch.incident_id}</div>
                              <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                                {dispatch.agency_name || agencyName(selectedHistoryUnit.agency_id)}
                              </div>
                            </div>
                            <Badge
                              variant={
                                dispatch.status === 'COMPLETED'
                                  ? 'success'
                                  : dispatch.status === 'CANCELLED'
                                  ? 'destructive'
                                  : 'info'
                              }
                              className="rounded-[2px] text-[10px] font-semibold"
                            >
                              {dispatch.status}
                            </Badge>
                          </div>
                          <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
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

                <DialogFooter className="mt-6 flex justify-end">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setSelectedHistoryUnit(null)}
                    className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    Close
                  </Button>
                </DialogFooter>
              </div>
            </div>
          </div>
        </Dialog>
      )}

      {selectedMemberUnit && (
        <Dialog open={!!selectedMemberUnit} onOpenChange={() => setSelectedMemberUnit(null)}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-md w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  <Users className="h-5 w-5 text-[#2F6F4E]" />
                  Rescue Unit Members
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  Unit: {selectedMemberUnit.unit_callsign}
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-4 text-sm">
                {memberError && (
                  <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-400">
                    {memberError}
                  </div>
                )}

                <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C]">
                  {membersLoading ? (
                    <div className="p-4 text-center text-[#5C5A56] dark:text-[#B9B6B0]">Loading members...</div>
                  ) : activeMembers.length === 0 ? (
                    <div className="p-4 text-center text-[#5C5A56] dark:text-[#B9B6B0]">No active responder members assigned.</div>
                  ) : (
                    <div className="divide-y divide-[#E7E3DC] dark:divide-[#26292C]">
                      {activeMembers.map((member) => (
                        <div key={member.id} className="flex items-center justify-between gap-3 p-3">
                          <div>
                            <div className="font-semibold text-[#0A0A0A] dark:text-white">{memberName(member.responder_id)}</div>
                            <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
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
                              className="h-8 w-8 p-0 rounded-[2px] text-red-500 hover:bg-red-500/10"
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
                  <form onSubmit={handleAddMember} className="space-y-3 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2]/40 dark:bg-[#181C1D]/40 p-3">
                    <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
                      <UserPlus className="h-4 w-4" />
                      Add Responder
                    </div>
                    <Select
                      value={selectedResponderId}
                      onChange={(e) => setSelectedResponderId(e.target.value)}
                      disabled={saving || membersLoading}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
                    <label className="flex items-center gap-2 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
                      <input
                        type="checkbox"
                        checked={newMemberIsLeader}
                        onChange={(e) => setNewMemberIsLeader(e.target.checked)}
                        className="h-4 w-4 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                      />
                      Assign as unit leader
                    </label>
                    <DialogFooter className="mt-3 flex justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setSelectedMemberUnit(null)}
                        className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                      >
                        Close
                      </Button>
                      <Button
                        type="submit"
                        disabled={saving || !selectedResponderId}
                        className="rounded-[2px] bg-[#D15033] hover:bg-[#B5432A] text-white"
                      >
                        {saving ? 'Saving...' : 'Add Member'}
                      </Button>
                    </DialogFooter>
                  </form>
                )}
                {!isAdmin && (
                  <DialogFooter className="mt-4 flex justify-end">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setSelectedMemberUnit(null)}
                      className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                    >
                      Close
                    </Button>
                  </DialogFooter>
                )}
              </div>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
};
