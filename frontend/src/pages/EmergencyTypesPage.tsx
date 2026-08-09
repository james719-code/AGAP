import React, { useMemo, useState } from 'react';
import { AlertTriangle, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tooltip } from '@/components/ui/tooltip';
import { useEmergencyTypes } from '@/hooks/useEmergencyTypes';
import { useAuth } from '@/context/AuthContext';
import type { AgencyType, EmergencyType } from '@/types/api';

type EmergencyTypeFormState = {
  code: string;
  name: string;
  description: string;
  default_priority: number;
  target_agency_type: AgencyType | '';
  is_active: boolean;
};

const emptyForm: EmergencyTypeFormState = {
  code: '',
  name: '',
  description: '',
  default_priority: 3,
  target_agency_type: '',
  is_active: true,
};

const agencyTypes: Array<AgencyType | ''> = ['', 'DRRMO', 'RESCUE', 'POLICE', 'FIRE', 'MEDICAL', 'VOLUNTEER'];

export const EmergencyTypesPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  // include_inactive=true 403s for non-admins (see emergency_type_handler.go),
  // so the default must follow role — starting non-admins on "show inactive"
  // would break the page load for them every time.
  const [showInactive, setShowInactive] = useState(isAdmin);
  const { emergencyTypes, loading, error, refetch, createEmergencyType, updateEmergencyType, deleteEmergencyType } = useEmergencyTypes(isAdmin && showInactive);
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
    if (priority <= 1) return <Badge variant="destructive" className="text-[10px] font-semibold">P{priority}</Badge>;
    if (priority === 2) return <Badge variant="warning" className="text-[10px] font-semibold">P{priority}</Badge>;
    return <Badge variant="outline" className="text-[10px] font-semibold">P{priority}</Badge>;
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
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Operations</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Emergency Types</h2>
          <p className="mt-1 text-sm text-muted-foreground">Classify incidents and route them to the correct response agency type</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Tooltip content="Refresh emergency types">
            <Button variant="outline" size="sm" onClick={refetch} className="h-9 w-9 p-0 rounded-md">
              <RotateCw className="h-4 w-4" />
            </Button>
          </Tooltip>
          {isAdmin && (
            <Button onClick={openCreate} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
              <Plus className="h-4 w-4" /> Add Type
            </Button>
          )}
        </div>
      </div>

      {isAdmin && (
        <div className="flex items-center gap-3 rounded-md border border-border bg-card px-3 py-2 text-sm">
          <input
            id="show-inactive-emergency-types"
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
            className="h-4 w-4 rounded border-border"
          />
          <label htmlFor="show-inactive-emergency-types" className="text-muted-foreground">
            Show inactive emergency types
          </label>
        </div>
      )}

      <Card className="border-border bg-card shadow-sm">
        <CardHeader className="py-4 border-b border-border">
          <CardTitle className="text-sm font-semibold text-card-foreground">
            Emergency Types ({visibleTypes.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading emergency types...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500 text-sm">{error}</div>
          ) : visibleTypes.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">No emergency types configured.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border bg-muted/60">
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Target Agency</TableHead>
                  <TableHead>Status</TableHead>
                  {isAdmin && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {visibleTypes.map((emergencyType) => (
                  <TableRow key={emergencyType.id} className="border-b border-border/70 hover:bg-muted/60">
                    <TableCell className="font-mono text-xs text-muted-foreground font-bold">{emergencyType.code}</TableCell>
                    <TableCell>
                      <div className="text-sm font-medium text-foreground">{emergencyType.name}</div>
                      {emergencyType.description && (
                        <div className="mt-1 max-w-[520px] text-[11px] text-muted-foreground">{emergencyType.description}</div>
                      )}
                    </TableCell>
                    <TableCell>{priorityBadge(emergencyType.default_priority)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{emergencyType.target_agency_type || 'N/A'}</TableCell>
                    <TableCell>
                      <Badge variant={emergencyType.is_active ? 'success' : 'outline'} className="text-[10px] font-semibold">
                        {emergencyType.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Tooltip content="Edit emergency type">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEdit(emergencyType)}
                              className="h-8 w-8 p-0 text-[#1F91A8] hover:bg-secondary/10"
                            >
                              <Edit3 className="h-4 w-4" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete emergency type">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => handleDelete(emergencyType)}
                              className="h-8 w-8 p-0 text-red-500 hover:bg-red-500/10"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </Tooltip>
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {isAdmin && (
        <Dialog open={isDialogOpen} onOpenChange={(open) => !open && closeDialog()}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-[#1F91A8]" />
              {editingType ? 'Edit Emergency Type' : 'Add Emergency Type'}
            </DialogTitle>
            <DialogDescription>Set routing and default priority for incident classification</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            {saveError && <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-300">{saveError}</div>}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Code *</label>
                <Input
                  required
                  placeholder="e.g. FLOOD"
                  value={formData.code}
                  onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Name *</label>
                <Input
                  required
                  placeholder="Flood Rescue"
                  value={formData.name}
                  onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Description</label>
              <textarea
                rows={3}
                className="flex min-h-[86px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                value={formData.description}
                onChange={(event) => setFormData({ ...formData, description: event.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Default Priority</label>
                <Input
                  type="number"
                  min={1}
                  max={5}
                  value={formData.default_priority}
                  onChange={(event) => setFormData({ ...formData, default_priority: Number(event.target.value) })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Target Agency</label>
                <Select
                  value={formData.target_agency_type}
                  onChange={(event) => setFormData({ ...formData, target_agency_type: event.target.value as AgencyType | '' })}
                >
                  {agencyTypes.map((type) => (
                    <option key={type || 'none'} value={type}>{type || 'None'}</option>
                  ))}
                </Select>
              </div>
              <label className="flex items-center gap-3 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                <input
                  type="checkbox"
                  checked={formData.is_active}
                  onChange={(event) => setFormData({ ...formData, is_active: event.target.checked })}
                  className="h-4 w-4 rounded border-border"
                />
                <span className="text-muted-foreground">Active</span>
              </label>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeDialog}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
                {saving ? 'Saving...' : 'Save Type'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};