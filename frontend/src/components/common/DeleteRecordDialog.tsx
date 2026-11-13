import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface DeleteRecordDialogProps {
  title: string;
  description: string;
  onDelete: () => Promise<unknown>;
  onClose: () => void;
}

export function DeleteRecordDialog({ title, description, onDelete, onClose }: DeleteRecordDialogProps) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setPending(true);
    setError(null);
    try {
      await onDelete();
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not delete this record.');
      setPending(false);
    }
  }

  return (
    <Dialog open role="alertdialog" onOpenChange={(open) => { if (!open && !pending) onClose(); }} contentClassName="max-w-md">
      <DialogHeader>
        <DialogTitle>{title}</DialogTitle>
        <DialogDescription>{description}</DialogDescription>
      </DialogHeader>
      {error && <p className="rounded-md bg-destructive/10 p-3 text-sm text-destructive" role="alert">{error}</p>}
      <DialogFooter className="gap-2">
        <Button type="button" variant="outline" onClick={onClose} disabled={pending}>Cancel</Button>
        <Button type="button" variant="destructive" onClick={confirm} disabled={pending}>{pending ? 'Deleting…' : 'Delete'}</Button>
      </DialogFooter>
    </Dialog>
  );
}
