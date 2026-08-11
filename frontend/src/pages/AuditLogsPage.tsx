import React, { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
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
    queueMicrotask(() => {
      fetchLogs();
    });
  }, [fetchLogs]);

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Administration</span>
          <h2 className="page-title">Audit Logs</h2>
          <p className="page-description">Review account, dispatch, and incident activity.</p>
        </div>
      </div>

      {/* Audit Logs Card */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        {/* Toolbar: all filter controls live in one shaded strip, welded to the
            results below instead of floating above the card as a loose row. */}
        <div className="flex flex-col lg:flex-row lg:items-center gap-2 px-4 py-3 bg-[#F7F5F2] dark:bg-[#181C1D] border-b border-[#E7E3DC] dark:border-[#26292C]">
          <Select
            value={entityType}
            onChange={(event) => setEntityType(event.target.value)}
            className="h-9 sm:w-[170px] rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
          >
            {entityTypes.map((type) => (
              <option key={type || 'ALL'} value={type}>{type || 'ALL ENTITIES'}</option>
            ))}
          </Select>

          <div className="relative sm:w-36">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[#6B6864] dark:text-[#9C9994]" />
            <Input
              value={entityId}
              onChange={(event) => setEntityId(event.target.value)}
              placeholder="Entity ID"
              className="h-9 pl-8 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
            />
          </div>

          <Input
            value={action}
            onChange={(event) => setAction(event.target.value)}
            placeholder="Action"
            className="h-9 sm:w-48 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
          />

          <Input
            type="date"
            value={dateFrom}
            onChange={(event) => setDateFrom(event.target.value)}
            className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
            aria-label="Audit start date"
          />
          <Input
            type="date"
            value={dateTo}
            onChange={(event) => setDateTo(event.target.value)}
            className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
            aria-label="Audit end date"
          />

          <Button
            variant="outline"
            size="sm"
            onClick={fetchLogs}
            disabled={loading}
            className="h-9 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] lg:ml-auto"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} /> Refresh
          </Button>
        </div>

        <div className="flex items-center justify-between border-b border-[#E7E3DC] dark:border-[#26292C] px-4 py-2.5">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-[#D15033]" />
            <span className="text-sm font-semibold text-[#0A0A0A] dark:text-white">Latest Events</span>
          </div>
          <Badge variant={error ? 'destructive' : 'outline'} className="rounded-[2px] text-[10px] font-semibold">
            {error ? 'ERROR' : `${logs.length} ROWS`}
          </Badge>
        </div>

        {error ? (
          <div className="p-5 text-sm text-red-600 dark:text-red-400">{error}</div>
        ) : logs.length === 0 ? (
          <div className="p-8 text-center text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            {loading ? 'Loading audit events...' : 'No audit events found.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table text-left">
              <thead className="bg-[#F7F5F2] dark:bg-[#181C1D] border-b border-[#E7E3DC] dark:border-[#26292C] text-xs uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994]">
                <tr>
                  <th className="px-4 py-3 font-semibold">Time</th>
                  <th className="px-4 py-3 font-semibold">Action</th>
                  <th className="px-4 py-3 font-semibold">Entity</th>
                  <th className="px-4 py-3 font-semibold">Actor</th>
                  <th className="px-4 py-3 font-semibold">Old</th>
                  <th className="px-4 py-3 font-semibold">New</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors">
                    <td className="px-4 py-3 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">{new Date(log.created_at).toLocaleString()}</td>
                    <td className="px-4 py-3 font-semibold text-[#0A0A0A] dark:text-white">{log.action}</td>
                    <td className="px-4 py-3">
                      <Badge variant="info" className="text-[10px]">
                        {log.entity_type} #{log.entity_id}
                      </Badge>
                    </td>
                    <td><span className="identity-chip">{log.actor_id ? `USR-${log.actor_id}` : 'SYSTEM'}</span></td>
                    <td className="max-w-60 truncate px-4 py-3 text-[#5C5A56] dark:text-[#B9B6B0]">{log.old_value || 'N/A'}</td>
                    <td className="max-w-60 truncate px-4 py-3 text-[#0A0A0A] dark:text-white">{log.new_value || 'N/A'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
