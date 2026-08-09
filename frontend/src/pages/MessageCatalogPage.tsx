import React, { useMemo, useState } from 'react';
import { BookOpenText, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tooltip } from '@/components/ui/tooltip';
import { useMessageCatalog } from '@/hooks/useMessageCatalog';
import { useEmergencyTypes } from '@/hooks/useEmergencyTypes';
import { useAuth } from '@/context/AuthContext';
import type { PredefinedMessage, PredefinedMessageType } from '@/types/api';

type CatalogFormState = {
  code: string;
  protocol_code: number;
  message_en: string;
  message_fil: string;
  message_bikol: string;
  message_type: PredefinedMessageType;
  emergency_type_id: string;
  is_active: boolean;
};

const emptyForm: CatalogFormState = {
  code: '',
  protocol_code: 0,
  message_en: '',
  message_fil: '',
  message_bikol: '',
  message_type: 'ACK',
  emergency_type_id: '',
  is_active: true,
};

export const MessageCatalogPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  // include_inactive=true 403s for non-admins (see message_handler.go's
  // ListCatalog), so the default has to follow role, not just default true.
  const [showInactive, setShowInactive] = useState(isAdmin);
  const { messages, loading, error, refetch, createMessage, updateMessage, deleteMessage } = useMessageCatalog(isAdmin && showInactive);
  // Real emergency types, not a hardcoded id->label table — catalog entries
  // reference emergency_types.id, which is an auto-increment DB id with no
  // guaranteed fixed mapping.
  const { emergencyTypes } = useEmergencyTypes(false);

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingMessage, setEditingMessage] = useState<PredefinedMessage | null>(null);
  const [formData, setFormData] = useState<CatalogFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const visibleMessages = useMemo(
    () => (showInactive ? messages : messages.filter((message) => message.is_active)),
    [messages, showInactive]
  );

  const emergencyTypeLabel = (id?: number) => {
    if (!id) return 'General';
    return emergencyTypes.find((type) => type.id === id)?.name || `Type #${id}`;
  };

  const openCreate = () => {
    setEditingMessage(null);
    setFormData(emptyForm);
    setSaveError(null);
    setIsDialogOpen(true);
  };

  const openEdit = (message: PredefinedMessage) => {
    setEditingMessage(message);
    setFormData({
      code: message.code,
      protocol_code: message.protocol_code,
      message_en: message.message_en,
      message_fil: message.message_fil || '',
      message_bikol: message.message_bikol || '',
      message_type: message.message_type,
      emergency_type_id: message.emergency_type_id ? String(message.emergency_type_id) : '',
      is_active: message.is_active,
    });
    setSaveError(null);
    setIsDialogOpen(true);
  };

  const closeDialog = () => {
    setIsDialogOpen(false);
    setEditingMessage(null);
    setFormData(emptyForm);
    setSaveError(null);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);

    const payload = {
      code: formData.code,
      protocol_code: formData.protocol_code,
      message_en: formData.message_en,
      message_fil: formData.message_fil || undefined,
      message_bikol: formData.message_bikol || undefined,
      message_type: formData.message_type,
      emergency_type_id: formData.emergency_type_id ? Number(formData.emergency_type_id) : undefined,
      is_active: formData.is_active,
    };

    try {
      if (editingMessage) {
        await updateMessage(editingMessage.id, payload);
      } else {
        await createMessage(payload);
      }
      closeDialog();
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to save message');
    } finally {
      setSaving(false);
    }
  };

  const typeBadge = (type: PredefinedMessageType) => {
    if (type === 'SOS') return <Badge variant="destructive" className="text-[10px] font-semibold">SOS</Badge>;
    if (type === 'STATUS') return <Badge variant="info" className="text-[10px] font-semibold">STATUS</Badge>;
    return <Badge variant="secondary" className="text-[10px] font-semibold">ACK</Badge>;
  };

  const handleDelete = async (message: PredefinedMessage) => {
    if (!window.confirm(`Delete catalog message "${message.code}"? This cannot be undone.`)) return;
    setSaveError(null);
    try {
      await deleteMessage(message.id);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to delete catalog message');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">BLE Mesh</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Predefined Message Catalog</h2>
          <p className="mt-1 text-sm text-muted-foreground">Maintain encoded emergency acknowledgements and responder status phrases</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Tooltip content="Refresh catalog">
            <Button variant="outline" size="sm" onClick={refetch} className="h-9 w-9 p-0 rounded-md">
              <RotateCw className="h-4 w-4" />
            </Button>
          </Tooltip>
          {isAdmin && (
            <Button onClick={openCreate} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
              <Plus className="h-4 w-4" /> Add Message
            </Button>
          )}
        </div>
      </div>

      {isAdmin && (
        <div className="flex items-center gap-3 rounded-md border border-border bg-card px-3 py-2 text-sm">
          <input
            id="show-inactive"
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
            className="h-4 w-4 rounded border-border"
          />
          <label htmlFor="show-inactive" className="text-muted-foreground">
            Show inactive catalog entries
          </label>
        </div>
      )}

      <Card className="border-border bg-card shadow-sm">
        <CardHeader className="py-4 border-b border-border">
          <CardTitle className="text-sm font-semibold text-card-foreground">
            Catalog Messages ({visibleMessages.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading message catalog...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500 text-sm">{error}</div>
          ) : visibleMessages.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">No predefined messages found.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border bg-muted/60">
                  <TableHead>Code</TableHead>
                  <TableHead>English Message</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Emergency</TableHead>
                  <TableHead>Status</TableHead>
                  {isAdmin && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {visibleMessages.map((message) => (
                  <TableRow key={message.id} className="border-b border-border/70 hover:bg-muted/60">
                    <TableCell className="font-mono text-xs text-muted-foreground font-bold">{message.code}</TableCell>
                    <TableCell>
                      <div className="max-w-[440px] text-sm font-medium text-foreground">{message.message_en}</div>
                      {(message.message_fil || message.message_bikol) && (
                        <div className="mt-1 max-w-[440px] text-[11px] text-muted-foreground">
                          {[message.message_fil, message.message_bikol].filter(Boolean).join(' / ')}
                        </div>
                      )}
                    </TableCell>
                    <TableCell>{typeBadge(message.message_type)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {emergencyTypeLabel(message.emergency_type_id)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={message.is_active ? 'success' : 'outline'} className="text-[10px] font-semibold">
                        {message.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Tooltip content="Edit catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEdit(message)}
                              className="h-8 w-8 p-0 text-[#1F91A8] hover:bg-secondary/10"
                            >
                              <Edit3 className="h-4 w-4" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => handleDelete(message)}
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
              <BookOpenText className="h-5 w-5 text-[#1F91A8]" />
              {editingMessage ? 'Edit Catalog Message' : 'Add Catalog Message'}
            </DialogTitle>
            <DialogDescription>Configure text that can be sent or synchronized as compact BLE messages</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            {saveError && <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-300">{saveError}</div>}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Message Code *</label>
                <Input
                  required
                  placeholder="e.g. MEDICAL_ACK"
                  value={formData.code}
                  onChange={(event) => setFormData({ ...formData, code: event.target.value })}
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
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Message Type *</label>
                <Select
                  value={formData.message_type}
                  onChange={(event) => setFormData({ ...formData, message_type: event.target.value as PredefinedMessageType })}
                >
                  <option value="ACK">ACK</option>
                  <option value="STATUS">STATUS</option>
                  <option value="SOS">SOS</option>
                </Select>
              </div>
            </div>

            <div>
              <label className="text-xs text-muted-foreground mb-1 block">English Message *</label>
              <textarea
                required
                rows={3}
                className="flex min-h-[86px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                value={formData.message_en}
                onChange={(event) => setFormData({ ...formData, message_en: event.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Filipino Message</label>
                <textarea
                  rows={3}
                  className="flex min-h-[78px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                  value={formData.message_fil}
                  onChange={(event) => setFormData({ ...formData, message_fil: event.target.value })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Bikol Message</label>
                <textarea
                  rows={3}
                  className="flex min-h-[78px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                  value={formData.message_bikol}
                  onChange={(event) => setFormData({ ...formData, message_bikol: event.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Emergency Type</label>
                <Select
                  value={formData.emergency_type_id}
                  onChange={(event) => setFormData({ ...formData, emergency_type_id: event.target.value })}
                >
                  <option value="">General</option>
                  {emergencyTypes.map((type) => (
                    <option key={type.id} value={String(type.id)}>{type.name}</option>
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
                <span className="text-muted-foreground">Active catalog entry</span>
              </label>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeDialog}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
                {saving ? 'Saving...' : 'Save Message'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};
