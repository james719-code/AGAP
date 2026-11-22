import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
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

function actionTone(action: string) {
  const verb = action.toUpperCase();
  if (/(DELETE|REJECT|SUSPEND|CANCEL)/.test(verb)) return 'tone-danger';
  if (/(CREATE|VERIFY|APPROVE|RESOLVE)/.test(verb)) return 'tone-success';
  if (/(DISPATCH|ASSIGN)/.test(verb)) return 'tone-warning';
  return 'tone-info';
}

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
          <div className="audit-filters grid items-end gap-2 sm:grid-cols-2 xl:grid-cols-[1.2fr_0.7fr_1.2fr_1fr_1fr_auto]">
            <label className="grid min-w-0 gap-1 text-[0.6875rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              Entity
              <Select value={filters.entityType} onChange={(event) => setFilters({ ...filters, entityType: event.target.value })} className="h-control bg-background normal-case tracking-normal text-foreground">
                <option value="">All entities</option>
                {entityTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </Select>
            </label>
            <label className="grid min-w-0 gap-1 text-[0.6875rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              Entity ID
              <Input value={filters.entityId} onChange={(event) => setFilters({ ...filters, entityId: event.target.value })} placeholder="e.g. 42" className="h-control bg-background normal-case tracking-normal text-foreground" />
            </label>
            <label className="grid min-w-0 gap-1 text-[0.6875rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              Action
              <Input value={filters.action} onChange={(event) => setFilters({ ...filters, action: event.target.value })} placeholder="Action name" className="h-control bg-background normal-case tracking-normal text-foreground" />
            </label>
            <label className="grid min-w-0 gap-1 text-[0.6875rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              From
              <Input type="date" value={filters.dateFrom} onChange={(event) => setFilters({ ...filters, dateFrom: event.target.value })} className="h-control min-w-0 bg-background normal-case tracking-normal text-foreground" />
            </label>
            <label className="grid min-w-0 gap-1 text-[0.6875rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              To
              <Input type="date" value={filters.dateTo} onChange={(event) => setFilters({ ...filters, dateTo: event.target.value })} aria-invalid={invalidRange} className="h-control min-w-0 bg-background normal-case tracking-normal text-foreground" />
            </label>
            <div className="flex items-center gap-2">
            <Button type="submit" className="h-control gap-2" disabled={invalidRange || loading}>
              <Search className="size-4" /> Apply
            </Button>
            {(hasFilters || hasDraft) && (
              <Button type="button" variant="ghost" className="h-control" onClick={() => { setFilters(emptyFilters); applyFilters(emptyFilters); }}>
                Clear
              </Button>
            )}
            </div>
          </div>
          {invalidRange && <p className="mt-2 text-sm text-destructive" role="alert">The end date must be on or after the start date.</p>}
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
            <div className="state-message"><p>{hasFilters ? 'No audit events match these filters.' : 'No audit events recorded yet.'}</p>{hasFilters && <Button type="button" variant="link" onClick={() => { setFilters(emptyFilters); applyFilters(emptyFilters); }}>Clear filters</Button>}</div>
          ) : (
            <DataTableViewport label="Audit events table">
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
                      <td className="whitespace-nowrap">
                        <time dateTime={log.created_at} className="leading-tight">
                          <span className="block font-medium">{new Date(log.created_at).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })}</span>
                          <span className="block font-mono text-[0.6875rem] text-muted-foreground">{new Date(log.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })}</span>
                        </time>
                      </td>
                      <td><span className={`status-pill font-mono ${actionTone(log.action)}`}>{log.action}</span></td>
                      <td><span className="technical-chip">{log.entity_type} #{log.entity_id}</span></td>
                      <td><span className="identity-chip">{log.actor_id ? 'USR-' + log.actor_id : 'System'}</span></td>
                      <td className="text-right">
                        <Button type="button" variant="outline" size="sm" aria-label={`View audit event ${log.id}`} onClick={() => setSelectedLog(log)}>View</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </DataTableViewport>
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
