import React, { useState } from 'react';
import { Clock, FileJson, FileSpreadsheet, RadioTower, RotateCw, ShieldCheck, Siren } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { useTelemetryReports } from '@/hooks/useTelemetryReports';
import { apiService } from '@/services/api';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import type { AnalyticsBreakdown, Incident } from '@/types/api';

const minutes = (value: number | null | undefined) => value == null ? 'N/A' : `${value.toFixed(1)}m`;

// Breakdown component – uses the same card style as the rest of the page
function Breakdown({ title, rows, total, progress = false }: { title: string; rows: AnalyticsBreakdown[]; total?: number; progress?: boolean }) {
  return (
    <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-6">
      <div className="mb-4 border-b border-[#E7E3DC] dark:border-[#26292C] pb-3">
        <h3 className="font-bold text-[#0A0A0A] dark:text-white">{title}</h3>
      </div>
      {rows.length === 0 ? (
        <p className="py-6 text-center text-sm text-[#5C5A56] dark:text-[#B9B6B0]">No verified records yet.</p>
      ) : (
        <div className="space-y-3">
          {rows.slice(0, 8).map((row) => {
            const percentage = total && total > 0 ? (row.count * 100) / total : 0;
            return (
              <div key={row.label} className="space-y-1.5">
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="min-w-0 truncate font-semibold text-[#0A0A0A] dark:text-white">{row.label}</span>
                  <span className="shrink-0 tabular-nums text-[#5C5A56] dark:text-[#B9B6B0]">
                    {row.count}
                    {progress ? ` (${Math.round(percentage)}%)` : ''}
                  </span>
                </div>
                {progress && <Progress value={percentage} className="h-2" indicatorClassName="bg-[#1F91A8]" />}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export const TelemetryReportsPage: React.FC = () => {
  const { report, loading, error, refetch } = useTelemetryReports();
  const [exportLoading, setExportLoading] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const loadExportIncidents = async (): Promise<Incident[]> => {
    const pageSize = 200;
    let offset = 0;
    let total = Infinity;
    const incidents: Incident[] = [];
    while (offset < total && incidents.length < 10_000) {
      const result = await apiService.getIncidents({ exclude_demo: true, limit: pageSize, offset });
      const page = result.incidents ?? [];
      incidents.push(...page);
      total = result.total ?? incidents.length;
      if (page.length === 0) break;
      offset += page.length;
    }
    return incidents;
  };

  const handleExport = async (format: 'csv' | 'json') => {
    setExportLoading(true);
    setExportError(null);
    try {
      const incidents = await loadExportIncidents();
      if (format === 'csv') exportIncidentsToCSV(incidents);
      else exportIncidentsToJSON(incidents);
    } catch (exportFailure) {
      setExportError(exportFailure instanceof Error ? exportFailure.message : 'Export failed.');
    } finally {
      setExportLoading(false);
    }
  };

  const overview = [
    { label: 'Operational Incidents', value: report?.total_incidents ?? 0, icon: Siren, note: 'Demo records excluded' },
    { label: 'Active Operations', value: report?.active_incidents ?? 0, icon: ShieldCheck, note: 'Currently open incidents' },
    { label: 'Resolved', value: report?.resolved_incidents ?? 0, icon: ShieldCheck, note: 'Verified resolved incidents' },
    { label: 'Active Rescue Units', value: `${report?.active_responders ?? 0} / ${report?.total_responders ?? 0}`, icon: RadioTower, note: 'Location update within 15 minutes' },
  ];

  const timing = [
    ['Average Acknowledgment', minutes(report?.average_ack_minutes), 'Received to acknowledged'],
    ['Average Dispatch', minutes(report?.average_dispatch_minutes), 'Received to first dispatch'],
    ['Average Arrival', minutes(report?.average_arrival_minutes), 'Dispatch to arrival'],
    ['Average Resolution', minutes(report?.average_response_minutes), 'Received to resolution'],
  ];

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Intelligence</span>
          <h2 className="page-title">Reports</h2>
          <p className="page-description">
            Review incident performance and responder deployments.
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
            onClick={() => void handleExport('csv')}
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <FileSpreadsheet className="h-3.5 w-3.5" />
            <span>CSV</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={exportLoading}
            onClick={() => void handleExport('json')}
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <FileJson className="h-3.5 w-3.5" />
            <span>JSON</span>
          </Button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">
          {error}
        </div>
      )}
      {exportError && (
        <div className="rounded-[2px] border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">
          {exportError}
        </div>
      )}

      {/* Operational Overview */}
      <section className="space-y-4">
        <h3 className="text-lg font-bold text-[#0A0A0A] dark:text-white">Operational overview</h3>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {overview.map(({ label, value, icon: Icon, note }) => (
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
      </section>

      {/* Response Performance */}
      <section className="space-y-4">
        <h3 className="text-lg font-bold text-[#0A0A0A] dark:text-white">Response performance</h3>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {timing.map(([label, value, note]) => (
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

        {/* Breakdown Grid */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Breakdown title="Incident Status" rows={report?.status_distribution ?? []} />
          <Breakdown title="Emergency Types" rows={report?.incident_type_distribution ?? []} />
        </div>
      </section>

      {/* Deployment & Coverage */}
      <section className="space-y-4">
        <h3 className="text-lg font-bold text-[#0A0A0A] dark:text-white">Deployment &amp; coverage</h3>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Breakdown title="Barangay Codes" rows={report?.barangay_distribution ?? []} />
          <Breakdown title="Agency Dispatches" rows={report?.agency_dispatches ?? []} />
          <Breakdown title="Responder Assignments" rows={report?.responder_assignments ?? []} />
          <Breakdown title="Unit Deployments" rows={report?.unit_deployments ?? []} />
        </div>
      </section>

      {/* Data Quality Footer */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="font-bold text-[#0A0A0A] dark:text-white">Data quality</p>
          <p className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Unavailable metrics display N/A. Exports exclude shipped demonstration incidents.
          </p>
        </div>
        <Badge variant={report?.false_alarm_rate == null ? 'outline' : 'info'}>
          {report?.false_alarm_rate == null ? 'FALSE ALARM RATE N/A' : `${report.false_alarm_rate.toFixed(1)}% FALSE ALARMS`}
        </Badge>
      </div>

      {/* Timestamp */}
      {report?.generated_at && (
        <p className="text-right text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
          Report generated {new Date(report.generated_at).toLocaleString()}
        </p>
      )}
    </div>
  );
};
