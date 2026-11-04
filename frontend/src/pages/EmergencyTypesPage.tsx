import React, { useMemo, useState } from 'react';
import { AlertTriangle, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Tooltip } from '@/components/ui/tooltip';
import { useEmergencyTypes } from '@/hooks/useEmergencyTypes';
import { useAuth } from '@/context/AuthContext';
import type { AgencyType, EmergencyType } from '@/types/api';

type EmergencyTypeFormState = {
  code: string;
  name: string;
  description: string;
  default_priority: number;
  protocol_code: number;
  target_agency_type: AgencyType | '';
  is_active: boolean;
};

const emptyForm: EmergencyTypeFormState = {
  code: '',
  name: '',
  description: '',
  default_priority: 3,
  protocol_code: 0,
  target_agency_type: '',
  is_active: true,
};

const agencyTypes: Array<AgencyType | ''> = ['', 'DRRMO', 'RESCUE', 'POLICE', 'FIRE', 'MEDICAL', 'VOLUNTEER'];

export const EmergencyTypesPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [showInactive, setShowInactive] = useState(isAdmin);
  const { emergencyTypes, loading, error, refetch, createEmergencyType, updateEmergencyType, deleteEmergencyType } =
    useEmergencyTypes(isAdmin && showInactive);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingType, setEditingType] = useState<EmergencyType | null>(null);
  const [formData, setFormData] = useState<EmergencyTypeFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const visibleTypes = useMemo(
    () => (showInactive ? emergencyTypes : emergencyTypes.filter((item) => item.is_active)),
    [emergencyTypes, showInactive]
  );

  const openCreate = () => {
    setEditingType(null);
    setFormData(emptyForm);
    setSaveError(null);
    setIsDialogOpen(true);
  };

  const openEdit = (emergencyType: EmergencyType) => {
    setEditingType(emergencyType);
    setFormData({
      code: emergencyType.code,
      name: emergencyType.name,
      description: emergencyType.description || '',
      default_priority: emergencyType.default_priority,
      protocol_code: emergencyType.protocol_code,
      target_agency_type: emergencyType.target_agency_type || '',
      is_active: emergencyType.is_active,
    });
    setSaveError(null);
    setIsDialogOpen(true);
  };

  const closeDialog = () => {
    setIsDialogOpen(false);
    setEditingType(null);
    setFormData(emptyForm);
    setSaveError(null);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);

    const payload = {
      code: formData.code,
      name: formData.name,
      description: formData.description || undefined,
      default_priority: formData.default_priority,
      protocol_code: formData.protocol_code,
      target_agency_type: formData.target_agency_type || undefined,
      is_active: formData.is_active,
    };

    try {
      if (editingType) {
        await updateEmergencyType(editingType.id, payload);
      } else {
        await createEmergencyType(payload);
      }
      closeDialog();
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to save emergency type');
    } finally {
      setSaving(false);
    }
  };

  const priorityBadge = (priority: number) => {
    if (priority <= 1) return <Badge variant="destructive" className="rounded-md text-[10px] font-semibold">P{priority}</Badge>;
    if (priority === 2) return <Badge variant="warning" className="rounded-md text-[10px] font-semibold">P{priority}</Badge>;
    return <Badge variant="outline" className="rounded-md text-[10px] font-semibold">P{priority}</Badge>;
  };

  const handleDelete = async (emergencyType: EmergencyType) => {
    if (!window.confirm(`Delete emergency type "${emergencyType.name}"? This cannot be undone.`)) return;
    setSaveError(null);
    try {
      await deleteEmergencyType(emergencyType.id);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to delete emergency type');
    }
  };

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Operations</span>
          <h2 className="page-title">Emergency Types</h2>
          <p className="page-description">
            Classify incidents and route them to the appropriate agency.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Tooltip content="Refresh emergency types">
            <Button
              variant="outline"
              size="sm"
              onClick={refetch}
              className="h-9 w-9 p-0 rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
            >
              <RotateCw className="h-4 w-4" />
            </Button>
          </Tooltip>
          {isAdmin && (
            <Button
              onClick={openCreate}
              className="bg-primary hover:bg-primary/90 text-primary-foreground flex items-center gap-2 font-semibold rounded-md"
            >
              <Plus className="h-4 w-4" /> Add Type
            </Button>
          )}
        </div>
      </div>

      {/* Main Card */}
      <div className="rounded-md border border-border bg-card shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        {/* Toolbar: count + inactive toggle share one shaded strip, welded to the
            table below instead of floating as a separate unstyled row. */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-4 py-2.5 bg-muted border-b border-border">
          <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
            Emergency Types <span className="text-muted-foreground font-normal">({visibleTypes.length})</span>
          </h3>
          {isAdmin && (
            <label htmlFor="show-inactive-emergency-types" className="flex items-center gap-2 text-xs text-muted-foreground cursor-pointer select-none">
              <input
                id="show-inactive-emergency-types"
                type="checkbox"
                checked={showInactive}
                onChange={(event) => setShowInactive(event.target.checked)}
                className="h-4 w-4 rounded-md border-border text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
              />
              Show inactive emergency types
            </label>
          )}
        </div>

        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading emergency types...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-600 dark:text-red-400 text-sm">{error}</div>
          ) : visibleTypes.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">No emergency types configured.</div>
          ) : (
            <table className="data-table">
              <thead className="bg-muted border-b border-border">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Code</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Name</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Priority</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Target Agency</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Status</th>
                  {isAdmin && <th className="text-right px-4 py-3 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {visibleTypes.map((emergencyType) => (
                  <tr key={emergencyType.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-accent transition-colors">
                    <td><span className="identity-chip">{emergencyType.code}</span></td>
                    <td className="px-4 py-3">
                      <span className="table-primary">{emergencyType.name}</span>
                      {emergencyType.description && (
                        <span className="table-secondary-inline" title={emergencyType.description}>{emergencyType.description}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">{priorityBadge(emergencyType.default_priority)}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {emergencyType.target_agency_type || 'N/A'}
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={emergencyType.is_active ? 'success' : 'outline'}
                        className="rounded-md text-[10px] font-semibold"
                      >
                        {emergencyType.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                        <div className="flex items-center justify-end gap-1.5">
                          <Tooltip content="Edit emergency type">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEdit(emergencyType)}
                              className="h-9 w-9 p-0 rounded-md text-[#3B7DD1] hover:bg-[#3B7DD1]/10 hover:text-[#3B7DD1] dark:hover:bg-[#3B7DD1]/15"
                            >
                              <Edit3 className="h-[17px] w-[17px]" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete emergency type">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => handleDelete(emergencyType)}
                              className="h-9 w-9 p-0 rounded-md text-red-500 hover:bg-red-500/10 hover:text-red-600 dark:hover:bg-red-500/15"
                            >
                              <Trash2 className="h-[17px] w-[17px]" />
                            </Button>
                          </Tooltip>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Create/Edit Dialog */}
      {isAdmin && (
        <Dialog open={isDialogOpen} onOpenChange={(open) => !open && closeDialog()}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="max-w-2xl w-full mx-4 rounded-md border border-border bg-card shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-foreground">
                  <AlertTriangle className="h-5 w-5 text-[#1F91A8]" />
                  {editingType ? 'Edit Emergency Type' : 'Add Emergency Type'}
                </DialogTitle>
                <DialogDescription className="text-sm text-muted-foreground">
                  Set routing and default priority for incident classification
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
                {saveError && (
                  <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-300">
                    {saveError}
                  </div>
                )}

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Code *</label>
                    <Input
                      required
                      placeholder="e.g. FLOOD"
                      value={formData.code}
                      onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                      className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Name *</label>
                    <Input
                      required
                      placeholder="Flood Rescue"
                      value={formData.name}
                      onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                      className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">Description</label>
                  <textarea
                    rows={3}
                    className="flex min-h-[86px] w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                    value={formData.description}
                    onChange={(event) => setFormData({ ...formData, description: event.target.value })}
                  />
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Default Priority</label>
                    <Input
                      type="number"
                      min={1}
                      max={5}
                      value={formData.default_priority}
                      onChange={(event) => setFormData({ ...formData, default_priority: Number(event.target.value) })}
                      className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Protocol Code</label>
                    <Input
                      type="number"
                      min={0}
                      max={255}
                      value={formData.protocol_code}
                      onChange={(event) => setFormData({ ...formData, protocol_code: Number(event.target.value) })}
                      className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Target Agency</label>
                    <Select
                      value={formData.target_agency_type}
                      onChange={(event) => setFormData({ ...formData, target_agency_type: event.target.value as AgencyType | '' })}
                      className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      {agencyTypes.map((type) => (
                        <option key={type || 'none'} value={type}>{type || 'None'}</option>
                      ))}
                    </Select>
                  </div>
                  <label className="flex items-center gap-3 rounded-md border border-border bg-muted px-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      checked={formData.is_active}
                      onChange={(event) => setFormData({ ...formData, is_active: event.target.checked })}
                      className="h-4 w-4 rounded-md border-border text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                    />
                    <span className="text-muted-foreground">Active</span>
                  </label>
                </div>

                <DialogFooter className="pt-3 border-t border-border flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={closeDialog}
                    className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    disabled={saving}
                    className="rounded-md bg-primary hover:bg-primary/90 text-primary-foreground"
                  >
                    {saving ? 'Saving...' : 'Save Type'}
                  </Button>
                </DialogFooter>
              </form>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
};
