import React, { useMemo, useState } from 'react';
import { BookOpenText, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { DeleteRecordDialog } from '@/components/common/DeleteRecordDialog';
import { RegistrySearch } from '@/components/common/RegistrySearch';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
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

  const [deleteTarget, setDeleteTarget] = useState<PredefinedMessage | null>(null);
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(isAdmin);
  const { messages, loading, error, refetch, createMessage, updateMessage, deleteMessage } = useMessageCatalog(isAdmin && showInactive);
  const { emergencyTypes } = useEmergencyTypes(false);

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingMessage, setEditingMessage] = useState<PredefinedMessage | null>(null);
  const [formData, setFormData] = useState<CatalogFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const visibleMessages = useMemo(() => {
    const query = search.trim().toLowerCase();
    return messages.filter((message) =>
      (showInactive || message.is_active) && [message.code, message.message_en, message.message_fil, message.message_bikol, message.message_type].some((value) => value?.toLowerCase().includes(query))
    );
  }, [messages, search, showInactive]);

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
    if (type === 'SOS') return <Badge variant="destructive" className="rounded-md text-[10px] font-semibold">SOS</Badge>;
    if (type === 'STATUS') return <Badge variant="info" className="rounded-md text-[10px] font-semibold">STATUS</Badge>;
    return <Badge variant="secondary" className="rounded-md text-[10px] font-semibold">ACK</Badge>;
  };

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Administration</span>
          <h1 className="page-title">Message Catalog</h1>
          <p className="page-description">
            Manage predefined emergency acknowledgements and responder updates.
          </p>
        </div>

        <div className="page-actions">
          <Tooltip content="Refresh catalog">
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
              <Plus className="h-4 w-4" /> Add Message
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
            <h2 className="font-bold">Message templates</h2>
            <p className="text-xs text-muted-foreground tabular-nums">{loading ? 'Loading…' : `${visibleMessages.length} messages`}</p>
          </div>
          <RegistrySearch
            label="Search messages"
            placeholder="Search code, type, or message"
            value={search}
            onChange={setSearch}
          />
          {isAdmin && (
            <label htmlFor="show-inactive" className="flex items-center gap-2 text-xs text-muted-foreground cursor-pointer select-none">
              <input
                id="show-inactive"
                type="checkbox"
                checked={showInactive}
                onChange={(event) => setShowInactive(event.target.checked)}
                className="h-4 w-4 rounded-md border-border text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
              />
              Show inactive catalog entries
            </label>
          )}
        </div>

        <DataTableViewport label="Message catalog table">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading message catalog...</div>
          ) : error ? (
            <div className="state-message state-message-error" role="alert">
              <p>{error}</p>
              <Button type="button" variant="link" onClick={refetch}>Retry</Button>
            </div>
          ) : visibleMessages.length === 0 ? (
            <div className="state-message">
              <p>{search.trim() ? 'No messages match your search.' : 'No message templates to display.'}</p>
              {search.trim() ? (
                <Button type="button" variant="link" onClick={() => setSearch('')}>Clear search</Button>
              ) : isAdmin && (
                <Button type="button" variant="link" onClick={openCreate}>Add message</Button>
              )}
            </div>
          ) : (
            <table className="data-table">
              <thead className="bg-muted border-b border-border">
                <tr>
                  <th scope="col" className="text-left">Code</th>
                  <th scope="col" className="text-left">English Message</th>
                  <th scope="col" className="text-left">Type</th>
                  <th scope="col" className="text-left">Emergency</th>
                  <th scope="col" className="text-left">Status</th>
                  {isAdmin && <th scope="col" className="text-right">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {visibleMessages.map((message) => (
                  <tr key={message.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-accent transition-colors">
                    <td><span className="identity-chip">{message.code}</span></td>
                    <td className="px-4 py-3">
                      <span className="table-primary">{message.message_en}</span>
                      {(message.message_fil || message.message_bikol) && (
                        <span className="table-secondary-inline" title={[message.message_fil, message.message_bikol].filter(Boolean).join(' / ')}>
                          {[message.message_fil, message.message_bikol].filter(Boolean).join(' / ')}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">{typeBadge(message.message_type)}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {emergencyTypeLabel(message.emergency_type_id)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={message.is_active ? 'success' : 'outline'}
                        className="rounded-md text-[10px] font-semibold"
                      >
                        {message.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                        <div className="flex items-center justify-end gap-1.5">
                          <Tooltip content="Edit catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEdit(message)}
                              aria-label={`Edit ${message.code}`}
                            className="record-action disabled:opacity-50"
                            >
                              <Edit3 className="h-[17px] w-[17px]" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => setDeleteTarget(message)}
                              aria-label={`Delete catalog message ${message.code}`}
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
              <BookOpenText className="h-5 w-5 text-[#1F91A8]" />
              {editingMessage ? 'Edit Catalog Message' : 'Add Catalog Message'}
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              Configure text that can be sent or synchronized as compact BLE messages
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            {saveError && (
              <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-300">
                {saveError}
              </div>
            )}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <label htmlFor="messagecatalogpage-message-code" className="text-xs text-muted-foreground mb-1 block">Message Code *</label>
                <Input id="messagecatalogpage-message-code"
                  required
                  placeholder="e.g. MEDICAL_ACK"
                  value={formData.code}
                  onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="messagecatalogpage-protocol-code" className="text-xs text-muted-foreground mb-1 block">Protocol Code</label>
                <Input id="messagecatalogpage-protocol-code"
                  type="number"
                  min={0}
                  max={255}
                  value={formData.protocol_code}
                  onChange={(event) => setFormData({ ...formData, protocol_code: Number(event.target.value) })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="messagecatalogpage-message-type" className="text-xs text-muted-foreground mb-1 block">Message Type *</label>
                <Select id="messagecatalogpage-message-type"
                  value={formData.message_type}
                  onChange={(event) => setFormData({ ...formData, message_type: event.target.value as PredefinedMessageType })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                >
                  <option value="ACK">ACK</option>
                  <option value="STATUS">STATUS</option>
                  <option value="SOS">SOS</option>
                </Select>
              </div>
            </div>

            <div>
              <label htmlFor="messagecatalogpage-english-message" className="text-xs text-muted-foreground mb-1 block">English Message *</label>
              <textarea id="messagecatalogpage-english-message"
                required
                rows={3}
                className="flex min-h-[86px] w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                value={formData.message_en}
                onChange={(event) => setFormData({ ...formData, message_en: event.target.value })}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="messagecatalogpage-filipino-message" className="text-xs text-muted-foreground mb-1 block">Filipino Message</label>
                <textarea id="messagecatalogpage-filipino-message"
                  rows={3}
                  className="flex min-h-[78px] w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                  value={formData.message_fil}
                  onChange={(event) => setFormData({ ...formData, message_fil: event.target.value })}
                />
              </div>
              <div>
                <label htmlFor="messagecatalogpage-bikol-message" className="text-xs text-muted-foreground mb-1 block">Bikol Message</label>
                <textarea id="messagecatalogpage-bikol-message"
                  rows={3}
                  className="flex min-h-[78px] w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                  value={formData.message_bikol}
                  onChange={(event) => setFormData({ ...formData, message_bikol: event.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="messagecatalogpage-emergency-type" className="text-xs text-muted-foreground mb-1 block">Emergency Type</label>
                <Select id="messagecatalogpage-emergency-type"
                  value={formData.emergency_type_id}
                  onChange={(event) => setFormData({ ...formData, emergency_type_id: event.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                >
                  <option value="">General</option>
                  {emergencyTypes.map((type) => (
                    <option key={type.id} value={String(type.id)}>{type.name}</option>
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
                <span className="text-muted-foreground">Active catalog entry</span>
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
                {saving ? 'Saving...' : 'Save Message'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {deleteTarget && (
        <DeleteRecordDialog
          title="Delete message template?"
          description={`“${deleteTarget.code}” will be permanently deleted.`}
          onDelete={() => deleteMessage(deleteTarget.id)}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
};
