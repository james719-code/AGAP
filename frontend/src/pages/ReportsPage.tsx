import React from 'react';
import { Clock, FileJson, FileSpreadsheet, RotateCw, ShieldCheck, Siren, Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useIncidents } from '@/hooks/useIncidents';
import { useOperationalReport } from '@/hooks/useOperationalReport';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import type { AnalyticsBreakdown } from '@/types/api';

const minutes = (value: number | null | undefined) => value == null ? 'N/A' : `${value.toFixed(1)}m`;

function SummaryCard({ title, badge, rows }: { title: string; badge: string; rows: AnalyticsBreakdown[] }) {
  return (
    <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-6">
      <div className="mb-4 flex items-center justify-between border-b border-[#E7E3DC] dark:border-[#26292C] pb-3">
        <h3 className="font-bold text-[#0A0A0A] dark:text-white">{title}</h3>
        <Badge variant="outline" className="rounded-[2px] text-[10px] font-semibold">{badge}</Badge>
      </div>
      <div className="space-y-3">
        {rows.length === 0 ? (
          <p className="py-5 text-center text-sm text-[#5C5A56] dark:text-[#B9B6B0]">No verified records</p>
        ) : (
          rows.slice(0, 8).map((row) => (
            <div key={row.label} className="flex items-center justify-between gap-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] px-3 py-2">
              <span className="min-w-0 truncate text-sm font-semibold text-[#0A0A0A] dark:text-white">{row.label}</span>
              <span className="shrink-0 tabular-nums text-[#5C5A56] dark:text-[#B9B6B0]">{row.count}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export const ReportsPage: React.FC = () => {
  const { report, loading, error, refetch } = useOperationalReport();
  const { incidents, loading: exportLoading } = useIncidents({ exclude_demo: true }, { fetchAll: true });

  const topCards = [
    { label: 'Operational Incidents', value: report?.total_incidents ?? 0, icon: Siren, note: 'Demo records excluded' },
    { label: 'Active Operations', value: report?.active_incidents ?? 0, icon: ShieldCheck, note: 'Currently open incidents' },
    { label: 'Resolved', value: report?.resolved_incidents ?? 0, icon: ShieldCheck, note: 'Verified resolved incidents' },
    { label: 'Active Responders', value: `${report?.active_responders ?? 0} / ${report?.total_responders ?? 0}`, icon: Users, note: 'Location update within 15 minutes' },
  ];

  const timeCards = [
    { label: 'Average Acknowledgment', value: minutes(report?.average_ack_minutes), note: 'Received to ACKNOWLEDGED history' },
    { label: 'Average Dispatch', value: minutes(report?.average_dispatch_minutes), note: 'Received to first actual dispatch' },
    { label: 'Average Arrival', value: minutes(report?.average_arrival_minutes), note: 'Dispatch to recorded arrival' },
    { label: 'Average Resolution', value: minutes(report?.average_response_minutes), note: 'Received to resolved timestamp' },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-[#D15033]">Reports</span>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white">Operational Reports</h2>
          <p className="mt-1 text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Backend-verified incident, response, and deployment records
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void refetch()}
            disabled={loading}
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <RotateCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={exportLoading}
            onClick={() => exportIncidentsToCSV(incidents)}
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <FileSpreadsheet className="h-3.5 w-3.5" />
            <span>CSV</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={exportLoading}
            onClick={() => exportIncidentsToJSON(incidents)}
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <FileJson className="h-3.5 w-3.5" />
            <span>JSON</span>
          </Button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-400">
          {error}
        </div>
      )}

      {/* Top Stat Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {topCards.map(({ label, value, icon: Icon, note }) => (
          <div
            key={label}
            className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex items-center justify-between transition-all hover:-translate-y-1 hover:shadow-lg"
          >
            <div>
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
                {label}
              </p>
              <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white tabular-nums">
                {loading ? '—' : value}
              </p>
              <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">{note}</p>
            </div>
            <div className="h-12 w-12 rounded-[2px] bg-[#D15033]/10 dark:bg-[#D15033]/15 text-[#D15033] flex items-center justify-center shrink-0">
              <Icon className="h-6 w-6" />
            </div>
          </div>
        ))}
      </div>

      {/* Response Times */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {timeCards.map(({ label, value, note }) => (
          <div
            key={label}
            className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 transition-all hover:-translate-y-1 hover:shadow-lg"
          >
            <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-[0.14em] text-[#6B6864] dark:text-[#9C9994]">
              <span>{label}</span>
              <Clock className="h-4 w-4 text-[#D15033]" />
            </div>
            <div className="mt-2 text-2xl font-extrabold tabular-nums text-[#0A0A0A] dark:text-white">
              {loading ? '—' : value}
            </div>
            <p className="mt-2 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">{note}</p>
          </div>
        ))}
      </div>

      {/* Breakdown Cards */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <SummaryCard title="Incident Status" badge="REAL STATUS" rows={report?.status_distribution ?? []} />
        <SummaryCard title="Emergency Types" badge="INCIDENTS" rows={report?.incident_type_distribution ?? []} />
        <SummaryCard title="Barangay Codes" badge="TOP AREAS" rows={report?.barangay_distribution ?? []} />
        <SummaryCard title="Agency Dispatches" badge="ACTUAL DISPATCHES" rows={report?.agency_dispatches ?? []} />
        <SummaryCard title="Responder Assignments" badge="ACTUAL ASSIGNMENTS" rows={report?.responder_assignments ?? []} />
        <SummaryCard title="Unit Deployments" badge="ACTUAL UNITS" rows={report?.unit_deployments ?? []} />
      </div>

      {/* Data Quality Footer */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="font-bold text-[#0A0A0A] dark:text-white">Data quality</p>
          <p className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Unavailable timestamps display N/A. Exports use operational incidents and exclude shipped demonstrations.
          </p>
        </div>
        <Badge variant={report?.false_alarm_rate == null ? 'outline' : 'info'} className="rounded-[2px]">
          {report?.false_alarm_rate == null ? 'FALSE ALARM RATE N/A' : `${report.false_alarm_rate.toFixed(1)}% FALSE ALARMS`}
        </Badge>
      </div>

      {/* Timestamp */}
      {report?.generated_at && (
        <p className="text-right text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
          Calculated by backend at {new Date(report.generated_at).toLocaleString()}
        </p>
      )}
    </div>
  );
};
