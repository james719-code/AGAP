import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface RegistryPaginationProps {
  total: number;
  page: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

export function RegistryPagination({ total, page, pageSize, onPageChange }: RegistryPaginationProps) {
  if (total === 0) return null;
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <nav aria-label="Record pagination" className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-sm text-muted-foreground">
      <span className="tabular-nums">Showing <strong className="text-foreground">{(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)}</strong> of {total.toLocaleString()}</span>
      {pages > 1 && (
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="icon" aria-label="Previous page" disabled={page <= 1} onClick={() => onPageChange(page - 1)}><ChevronLeft className="size-4" /></Button>
          <span className="whitespace-nowrap text-xs tabular-nums">Page {page} of {pages}</span>
          <Button type="button" variant="outline" size="icon" aria-label="Next page" disabled={page >= pages} onClick={() => onPageChange(page + 1)}><ChevronRight className="size-4" /></Button>
        </div>
      )}
    </nav>
  );
}
