import React, { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { apiService } from '@/services/api';
import type { AuditLog } from '@/types/api';

const entityTypes = ['', 'USER', 'INCIDENT', 'DISPATCH', 'RESCUE_UNIT'];

export const AuditLogsPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [entityType, setEntityType] = useState('');
  const [entityId, setEntityId] = useState('');
  const [action, setAction] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getAuditLogs(
        entityType || undefined,
        entityId || undefined,
        100,
        action || undefined,
        dateFrom || undefined,
        dateTo || undefined
      );
      setLogs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  }, [action, dateFrom, dateTo, entityId, entityType]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Audit Logs</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">System Audit Trail</h2>
          <p className="mt-1 text-sm text-muted-foreground">Administrative account, dispatch, and incident lifecycle activity</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <select
            value={entityType}
            onChange={(event) => setEntityType(event.target.value)}
            className="h-9 rounded-md border border-border bg-background px-3 text-sm text-foreground"
          >
            {entityTypes.map((type) => (
              <option key={type || 'ALL'} value={type}>{type || 'ALL ENTITIES'}</option>
            ))}
          </select>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <input
              value={entityId}
              onChange={(event) => setEntityId(event.target.value)}
              placeholder="Entity ID"
              className="h-9 w-full rounded-md border border-border bg-background pl-9 pr-3 text-sm text-foreground sm:w-36"
            />
          </div>
          <input
            value={action}
            onChange={(event) => setAction(event.target.value)}
            placeholder="Action"
            className="h-9 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground sm:w-48"
          />
          <input
            type="date"
            value={dateFrom}
            onChange={(event) => setDateFrom(event.target.value)}
            className="h-9 rounded-md border border-border bg-background px-3 text-sm text-foreground"
            aria-label="Audit start date"
          />
          <input
            type="date"
            value={dateTo}
            onChange={(event) => setDateTo(event.target.value)}
            className="h-9 rounded-md border border-border bg-background px-3 text-sm text-foreground"
            aria-label="Audit end date"
          />
          <Button variant="outline" size="sm" onClick={fetchLogs} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-primary" />
              <span className="text-sm font-bold text-foreground">Latest Events</span>
            </div>
            <Badge variant={error ? 'destructive' : 'outline'} className="text-[10px]">
              {error ? 'ERROR' : `${logs.length} ROWS`}
            </Badge>
          </div>

          {error ? (
            <div className="p-5 text-sm text-destructive">{error}</div>
          ) : logs.length === 0 ? (
            <div className="p-8 text-center text-sm text-muted-foreground">
              {loading ? 'Loading audit events...' : 'No audit events found.'}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-border bg-muted/70 text-xs uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Time</th>
                    <th className="px-4 py-3">Action</th>
                    <th className="px-4 py-3">Entity</th>
                    <th className="px-4 py-3">Actor</th>
                    <th className="px-4 py-3">Old</th>
                    <th className="px-4 py-3">New</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id} className="border-b border-border/70 hover:bg-muted/50">
                      <td className="px-4 py-3 text-xs text-muted-foreground">{new Date(log.created_at).toLocaleString()}</td>
                      <td className="px-4 py-3 font-semibold text-foreground">{log.action}</td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className="text-[10px]">{log.entity_type} #{log.entity_id}</Badge>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{log.actor_id ? `#${log.actor_id}` : 'SYSTEM'}</td>
                      <td className="max-w-60 truncate px-4 py-3 text-muted-foreground">{log.old_value || 'N/A'}</td>
                      <td className="max-w-60 truncate px-4 py-3 text-foreground">{log.new_value || 'N/A'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};
