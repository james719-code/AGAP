import React, { useMemo, useState } from 'react';
import { BookOpenText, Edit3, Plus, RotateCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
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

  const [showInactive, setShowInactive] = useState(isAdmin);
  const { messages, loading, error, refetch, createMessage, updateMessage, deleteMessage } = useMessageCatalog(isAdmin && showInactive);
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
    if (type === 'SOS') return <Badge variant="destructive" className="rounded-[2px] text-[10px] font-semibold">SOS</Badge>;
    if (type === 'STATUS') return <Badge variant="info" className="rounded-[2px] text-[10px] font-semibold">STATUS</Badge>;
    return <Badge variant="secondary" className="rounded-[2px] text-[10px] font-semibold">ACK</Badge>;
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
    <div className="page-stack">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-[#D15033]">BLE Mesh</span>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white">
            Predefined Message Catalog
          </h2>
          <p className="mt-1 text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Maintain encoded emergency acknowledgements and responder status phrases
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Tooltip content="Refresh catalog">
            <Button
              variant="outline"
              size="sm"
              onClick={refetch}
              className="h-9 w-9 p-0 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
            >
              <RotateCw className="h-4 w-4" />
            </Button>
          </Tooltip>
          {isAdmin && (
            <Button
              onClick={openCreate}
              className="bg-[#D15033] hover:bg-[#B5432A] text-white flex items-center gap-2 font-semibold rounded-[2px]"
            >
              <Plus className="h-4 w-4" /> Add Message
            </Button>
          )}
        </div>
      </div>

      {/* Show Inactive Toggle */}
      {isAdmin && (
        <div className="table-toolbar text-sm">
          <input
            id="show-inactive"
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
            className="h-4 w-4 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
          />
          <label htmlFor="show-inactive" className="text-[#5C5A56] dark:text-[#B9B6B0]">
            Show inactive catalog entries
          </label>
        </div>
      )}

      {/* Main Card */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        <div className="py-3 px-4 border-b border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white">
            Catalog Messages ({visibleMessages.length})
          </h3>
        </div>

        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">Loading message catalog...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-600 dark:text-red-400 text-sm">{error}</div>
          ) : visibleMessages.length === 0 ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">No predefined messages found.</div>
          ) : (
            <table className="data-table">
              <thead className="bg-[#F7F5F2] dark:bg-[#181C1D] border-b border-[#E7E3DC] dark:border-[#26292C]">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Code</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">English Message</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Type</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Emergency</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Status</th>
                  {isAdmin && <th className="text-right px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {visibleMessages.map((message) => (
                  <tr key={message.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors">
                    <td><span className="identity-chip">{message.code}</span></td>
                    <td className="px-4 py-3">
                      <div className="max-w-[440px] text-sm font-medium text-[#0A0A0A] dark:text-white">{message.message_en}</div>
                      {(message.message_fil || message.message_bikol) && (
                        <div className="mt-1 max-w-[440px] text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                          {[message.message_fil, message.message_bikol].filter(Boolean).join(' / ')}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">{typeBadge(message.message_type)}</td>
                    <td className="px-4 py-3 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
                      {emergencyTypeLabel(message.emergency_type_id)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={message.is_active ? 'success' : 'outline'}
                        className="rounded-[2px] text-[10px] font-semibold"
                      >
                        {message.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                        <div className="flex items-center justify-end gap-1">
                          <Tooltip content="Edit catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEdit(message)}
                              className="table-action-icon"
                            >
                              <Edit3 className="h-4 w-4" />
                            </Button>
                          </Tooltip>
                          <Tooltip content="Delete catalog message">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => handleDelete(message)}
                              className="table-action-icon text-red-600 dark:text-red-400"
                            >
                              <Trash2 className="h-4 w-4" />
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
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-2xl w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  <BookOpenText className="h-5 w-5 text-[#1F91A8]" />
                  {editingMessage ? 'Edit Catalog Message' : 'Add Catalog Message'}
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  Configure text that can be sent or synchronized as compact BLE messages
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
                {saveError && (
                  <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-300">
                    {saveError}
                  </div>
                )}

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Message Code *</label>
                    <Input
                      required
                      placeholder="e.g. MEDICAL_ACK"
                      value={formData.code}
                      onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Protocol Code</label>
                    <Input
                      type="number"
                      min={0}
                      max={255}
                      value={formData.protocol_code}
                      onChange={(event) => setFormData({ ...formData, protocol_code: Number(event.target.value) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Message Type *</label>
                    <Select
                      value={formData.message_type}
                      onChange={(event) => setFormData({ ...formData, message_type: event.target.value as PredefinedMessageType })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="ACK">ACK</option>
                      <option value="STATUS">STATUS</option>
                      <option value="SOS">SOS</option>
                    </Select>
                  </div>
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">English Message *</label>
                  <textarea
                    required
                    rows={3}
                    className="flex min-h-[86px] w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] px-3 py-2 text-sm text-[#0A0A0A] dark:text-[#F2F0EC] placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                    value={formData.message_en}
                    onChange={(event) => setFormData({ ...formData, message_en: event.target.value })}
                  />
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Filipino Message</label>
                    <textarea
                      rows={3}
                      className="flex min-h-[78px] w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] px-3 py-2 text-sm text-[#0A0A0A] dark:text-[#F2F0EC] placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                      value={formData.message_fil}
                      onChange={(event) => setFormData({ ...formData, message_fil: event.target.value })}
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Bikol Message</label>
                    <textarea
                      rows={3}
                      className="flex min-h-[78px] w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] px-3 py-2 text-sm text-[#0A0A0A] dark:text-[#F2F0EC] placeholder:text-[#6B6864] dark:placeholder:text-[#9C9994] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#D15033] disabled:cursor-not-allowed disabled:opacity-50"
                      value={formData.message_bikol}
                      onChange={(event) => setFormData({ ...formData, message_bikol: event.target.value })}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Emergency Type</label>
                    <Select
                      value={formData.emergency_type_id}
                      onChange={(event) => setFormData({ ...formData, emergency_type_id: event.target.value })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="">General</option>
                      {emergencyTypes.map((type) => (
                        <option key={type.id} value={String(type.id)}>{type.name}</option>
                      ))}
                    </Select>
                  </div>
                  <label className="flex items-center gap-3 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] px-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      checked={formData.is_active}
                      onChange={(event) => setFormData({ ...formData, is_active: event.target.checked })}
                      className="h-4 w-4 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-1 focus:ring-[#1F91A8]"
                    />
                    <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Active catalog entry</span>
                  </label>
                </div>

                <DialogFooter className="pt-3 border-t border-[#E7E3DC] dark:border-[#26292C] flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={closeDialog}
                    className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    disabled={saving}
                    className="rounded-[2px] bg-[#D15033] hover:bg-[#B5432A] text-white"
                  >
                    {saving ? 'Saving...' : 'Save Message'}
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
