import React, { useMemo, useState } from 'react';
import { AlertTriangle, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { DeleteRecordDialog } from '@/components/common/DeleteRecordDialog';
import { RegistrySearch } from '@/components/common/RegistrySearch';
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

  const [deleteTarget, setDeleteTarget] = useState<EmergencyType | null>(null);
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(isAdmin);
  const { emergencyTypes, loading, error, refetch, createEmergencyType, updateEmergencyType, deleteEmergencyType } =
    useEmergencyTypes(isAdmin && showInactive);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingType, setEditingType] = useState<EmergencyType | null>(null);
  const [formData, setFormData] = useState<EmergencyTypeFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const visibleTypes = useMemo(() => {
    const query = search.trim().toLowerCase();
    return emergencyTypes.filter((item) =>
      (showInactive || item.is_active) && [item.name, item.code, item.target_agency_type].some((value) => value?.toLowerCase().includes(query))
    );
  }, [emergencyTypes, search, showInactive]);

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

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Administration</span>
          <h1 className="page-title">Emergency Types</h1>
          <p className="page-description">
            Classify incidents and route them to the appropriate agency.
          </p>
        </div>

        <div className="page-actions">
          <Tooltip content="Refresh emergency types">
            <Button
              variant="outline"
              size="sm"
              onClick={refetch}
              disabled={loading}
              className="h-control gap-2 rounded-lg border-border bg-card text-foreground hover:bg-accent"
            >
              <RotateCw className={`size-4 ${loading ? 'motion-safe:animate-spin' : ''}`} /> Refresh
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
      <div className="surface-panel">
        {/* Toolbar: count + inactive toggle share one shaded strip, welded to the
            table below instead of floating as a separate unstyled row. */}
        <div className="surface-header flex-wrap">
          <div>
            <h2 className="font-bold">Classification catalog</h2>
            <p className="text-xs text-muted-foreground tabular-nums">{loading ? 'Loading…' : `${visibleTypes.length} emergency types`}</p>
          </div>
          <RegistrySearch
            label="Search emergency types"
            placeholder="Search name, code, or agency type"
            value={search}
            onChange={setSearch}
          />
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

        <DataTableViewport label="Emergency types table">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading emergency types...</div>
          ) : error ? (
            <div className="state-message state-message-error" role="alert">
              <p>{error}</p>
              <Button type="button" variant="link" onClick={refetch}>Retry</Button>
            </div>
          ) : visibleTypes.length === 0 ? (
            <div className="state-message">
              <p>{search.trim() ? 'No emergency types match your search.' : 'No emergency types to display.'}</p>
              {search.trim() ? (
                <Button type="button" variant="link" onClick={() => setSearch('')}>Clear search</Button>
              ) : isAdmin && (
                <Button type="button" variant="link" onClick={openCreate}>Add emergency type</Button>
              )}
            </div>
          ) : (
            <table className="data-table">
              <thead className="bg-muted border-b border-border">
                <tr>
                  <th scope="col" className="text-left">Code</th>
                  <th scope="col" className="text-left">Name</th>
                  <th scope="col" className="text-left">Priority</th>
                  <th scope="col" className="text-left">Target Agency</th>
                  <th scope="col" className="text-left">Status</th>
                  {isAdmin && <th scope="col" className="text-right">Actions</th>}
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
                              aria-label={`Edit ${emergencyType.name}`}
                            className="record-action disabled:opacity-50"
                            >
                              <Edit3 className="h-[17px] w-[17px]" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete emergency type">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => setDeleteTarget(emergencyType)}
                              aria-label={`Delete emergency type ${emergencyType.name}`}
                              className="record-action record-action-danger"
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
        </DataTableViewport>
      </div>

      {/* Create/Edit Dialog */}
      {isAdmin && (
        <Dialog open={isDialogOpen} onOpenChange={(open) => !open && closeDialog()} contentClassName="max-w-2xl">
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
                <label htmlFor="emergencytypespage-code" className="text-xs text-muted-foreground mb-1 block">Code *</label>
                <Input id="emergencytypespage-code"
                  required
                  placeholder="e.g. FLOOD"
                  value={formData.code}
                  onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="emergencytypespage-name" className="text-xs text-muted-foreground mb-1 block">Name *</label>
                <Input id="emergencytypespage-name"
                  required
                  placeholder="Flood Rescue"
                  value={formData.name}
                  onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
            </div>

            <div>
              <label htmlFor="emergencytypespage-description" className="text-xs text-muted-foreground mb-1 block">Description</label>
              <textarea id="emergencytypespage-description"
                rows={3}
                className="flex min-h-[86px] w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                value={formData.description}
                onChange={(event) => setFormData({ ...formData, description: event.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
              <div>
                <label htmlFor="emergencytypespage-default-priority" className="text-xs text-muted-foreground mb-1 block">Default Priority</label>
                <Input id="emergencytypespage-default-priority"
                  type="number"
                  min={1}
                  max={5}
                  value={formData.default_priority}
                  onChange={(event) => setFormData({ ...formData, default_priority: Number(event.target.value) })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="emergencytypespage-protocol-code" className="text-xs text-muted-foreground mb-1 block">Protocol Code</label>
                <Input id="emergencytypespage-protocol-code"
                  type="number"
                  min={0}
                  max={255}
                  value={formData.protocol_code}
                  onChange={(event) => setFormData({ ...formData, protocol_code: Number(event.target.value) })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="emergencytypespage-target-agency" className="text-xs text-muted-foreground mb-1 block">Target Agency</label>
                <Select id="emergencytypespage-target-agency"
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
        </Dialog>
      )}

      {deleteTarget && (
        <DeleteRecordDialog
          title="Delete emergency type?"
          description={`“${deleteTarget.name}” will be permanently deleted.`}
          onDelete={() => deleteEmergencyType(deleteTarget.id)}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
};
