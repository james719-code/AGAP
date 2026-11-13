import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { apiService } from '@/services/api';
import type { AuditLog } from '@/types/api';

const entityTypes = [
  ['USER', 'User'],
  ['INCIDENT', 'Incident'],
  ['DISPATCH', 'Dispatch'],
  ['RESCUE_UNIT', 'Responder unit'],
] as const;
const emptyFilters = { entityType: '', entityId: '', action: '', dateFrom: '', dateTo: '' };
const RESULT_LIMIT = 100;

export const AuditLogsPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [filters, setFilters] = useState(emptyFilters);
  const [appliedFilters, setAppliedFilters] = useState(emptyFilters);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
  const requestId = useRef(0);

  const fetchLogs = useCallback(async (query: typeof emptyFilters) => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getAuditLogs(
        query.entityType || undefined,
        query.entityId.trim() || undefined,
        RESULT_LIMIT,
        query.action.trim() || undefined,
        query.dateFrom || undefined,
        query.dateTo || undefined,
      );
      if (id === requestId.current) setLogs(data);
    } catch (err) {
      if (id === requestId.current) setError(err instanceof Error ? err.message : 'Failed to load audit logs');
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void fetchLogs(emptyFilters));
  }, [fetchLogs]);

  function applyFilters(query: typeof emptyFilters) {
    setAppliedFilters(query);
    void fetchLogs(query);
  }

  const invalidRange = Boolean(filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo);
  const hasFilters = Object.values(appliedFilters).some(Boolean);
  const hasDraft = Object.values(filters).some(Boolean);

  return (
    <div className="page-stack">
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Records</span>
          <h1 className="page-title">Audit Logs</h1>
          <p className="page-description">Review recorded changes to accounts, incidents, dispatches, and responder units.</p>
        </div>
        <Button type="button" variant="outline" className="h-control gap-2 bg-card" onClick={() => void fetchLogs(appliedFilters)} disabled={loading}>
          <RefreshCw className="size-4" /> Refresh
        </Button>
      </div>

      <section className="surface-panel" aria-labelledby="audit-title">
        <form
          className="border-b border-border bg-muted/50 p-4"
          onSubmit={(event) => { event.preventDefault(); if (!invalidRange) applyFilters(filters); }}
        >
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <label className="grid min-w-0 gap-1.5 text-sm font-semibold">
              Entity
              <Select value={filters.entityType} onChange={(event) => setFilters({ ...filters, entityType: event.target.value })} className="h-control bg-card">
                <option value="">All entities</option>
                {entityTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </Select>
            </label>
            <label className="grid min-w-0 gap-1.5 text-sm font-semibold">
              Entity ID
              <Input value={filters.entityId} onChange={(event) => setFilters({ ...filters, entityId: event.target.value })} placeholder="e.g. 42" className="h-control bg-card" />
            </label>
            <label className="grid min-w-0 gap-1.5 text-sm font-semibold">
              Action
              <Input value={filters.action} onChange={(event) => setFilters({ ...filters, action: event.target.value })} placeholder="Action name" className="h-control bg-card" />
            </label>
            <label className="grid min-w-0 gap-1.5 text-sm font-semibold">
              From
              <Input type="date" value={filters.dateFrom} onChange={(event) => setFilters({ ...filters, dateFrom: event.target.value })} className="h-control min-w-0 bg-card" />
            </label>
            <label className="grid min-w-0 gap-1.5 text-sm font-semibold">
              To
              <Input type="date" value={filters.dateTo} onChange={(event) => setFilters({ ...filters, dateTo: event.target.value })} aria-invalid={invalidRange} className="h-control min-w-0 bg-card" />
            </label>
          </div>
          {invalidRange && <p className="mt-2 text-sm text-destructive" role="alert">The end date must be on or after the start date.</p>}
          <div className="mt-4 flex flex-wrap gap-2">
            <Button type="submit" className="h-control gap-2" disabled={invalidRange || loading}>
              <Search className="size-4" /> Apply filters
            </Button>
            {(hasFilters || hasDraft) && (
              <Button type="button" variant="ghost" className="h-control" onClick={() => { setFilters(emptyFilters); applyFilters(emptyFilters); }}>
                Clear filters
              </Button>
            )}
          </div>
        </form>

        <div className="surface-header">
          <div>
            <h2 id="audit-title" className="font-bold">Latest events</h2>
            <p className="text-xs text-muted-foreground">Showing up to {RESULT_LIMIT} recent matches.</p>
          </div>
          {!loading && !error && <Badge variant="outline" className="tabular-nums">{logs.length} events</Badge>}
        </div>

        <div aria-busy={loading}>
          {loading ? (
            <p className="state-message" role="status">Loading audit events…</p>
          ) : error ? (
            <div className="state-message state-message-error" role="alert">
              <p>{error}</p>
              <Button type="button" variant="link" onClick={() => void fetchLogs(appliedFilters)}>Retry</Button>
            </div>
          ) : logs.length === 0 ? (
            <p className="state-message">{hasFilters ? 'No audit events match these filters.' : 'No audit events recorded yet.'}</p>
          ) : (
            <div className="table-scroll">
              <table className="data-table text-left">
                <thead>
                  <tr>
                    <th scope="col">Time</th>
                    <th scope="col">Action</th>
                    <th scope="col">Entity</th>
                    <th scope="col">Actor</th>
                    <th scope="col" className="text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id}>
                      <td className="text-muted-foreground"><time dateTime={log.created_at}>{new Date(log.created_at).toLocaleString()}</time></td>
                      <td className="font-semibold">{log.action}</td>
                      <td><Badge variant="info">{log.entity_type} #{log.entity_id}</Badge></td>
                      <td><span className="identity-chip">{log.actor_id ? 'USR-' + log.actor_id : 'System'}</span></td>
                      <td className="text-right">
                        <Button type="button" variant="outline" size="sm" aria-label={`View audit event ${log.id}`} onClick={() => setSelectedLog(log)}>View</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      {selectedLog && (
        <Dialog open onOpenChange={(open) => { if (!open) setSelectedLog(null); }} contentClassName="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Audit event #{selectedLog.id}</DialogTitle>
            <DialogDescription>{selectedLog.action} · {selectedLog.entity_type} #{selectedLog.entity_id}</DialogDescription>
          </DialogHeader>
          <dl className="grid gap-3 sm:grid-cols-2">
            {[
              ['Previous value', selectedLog.old_value],
              ['New value', selectedLog.new_value],
            ].map(([label, value]) => (
              <div key={label} className="min-w-0 rounded-md border border-border bg-muted p-3">
                <dt className="text-sm font-semibold">{label}</dt>
                <dd className="mt-2 font-mono text-xs leading-relaxed wrap-anywhere whitespace-pre-wrap text-muted-foreground">{value || 'Not recorded'}</dd>
              </div>
            ))}
          </dl>
        </Dialog>
      )}
    </div>
  );
};
