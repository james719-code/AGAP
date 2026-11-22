import React, { useState } from 'react';
import { FileJson, FileSpreadsheet, RadioTower, RotateCw, ShieldCheck, Siren } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { useTelemetryReports } from '@/hooks/useTelemetryReports';
import { apiService } from '@/services/api';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import type { AnalyticsBreakdown, Incident } from '@/types/api';
import { incidentStatusLabel } from '@/lib/incidentPresentation';

const minutes = (value: number | null | undefined) => value == null ? 'N/A' : value.toFixed(1) + ' min';

function Breakdown({ title, rows }: { title: string; rows: AnalyticsBreakdown[] }) {
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  const rankedRows = [...rows].sort((a, b) => b.count - a.count);

  return (
    <section className="surface-panel">
      <div className="surface-header">
        <h3 className="font-bold">{title}</h3>
        {rows.length > 8 && <span className="text-xs text-muted-foreground">Top 8 groups</span>}
      </div>
      {rows.length === 0 ? (
        <p className="state-message">No verified records yet.</p>
      ) : (
        <ul className="space-y-3 px-4 py-4">
          {rankedRows.slice(0, 8).map(({ label, count }) => {
            const percentage = total > 0 ? (count * 100) / total : 0;
            return (
              <li key={label} className="space-y-1.5">
                <div className="flex items-start justify-between gap-4 text-sm">
                  <span className="min-w-0 font-semibold wrap-anywhere">{label === label.toUpperCase() ? incidentStatusLabel(label) : label}</span>
                  <span className="shrink-0 tabular-nums text-muted-foreground">{count.toLocaleString()} · {Math.round(percentage)}%</span>
                </div>
                <Progress value={percentage} aria-hidden="true" className="h-1.5" />
              </li>
            );
          })}
        </ul>
      )}
    </section>
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
    { label: 'Operational Incidents', value: report?.total_incidents ?? null, icon: Siren, note: 'Demo records excluded' },
    { label: 'Active Operations', value: report?.active_incidents ?? null, icon: ShieldCheck, note: 'Currently open incidents' },
    { label: 'Resolved', value: report?.resolved_incidents ?? null, icon: ShieldCheck, note: 'Verified resolved incidents' },
    { label: 'Active Rescue Units', value: report ? report.active_responders + ' / ' + report.total_responders : null, icon: RadioTower, note: 'Location update within 15 minutes' },
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
          <span className="page-eyebrow">Records</span>
          <h1 className="page-title">Reports</h1>
          <p className="page-description">
            Incident performance and responder deployments{report?.generated_at ? <> · generated <time dateTime={report.generated_at} className="font-semibold text-foreground">{new Date(report.generated_at).toLocaleString()}</time></> : null}.
          </p>
        </div>
        <div className="page-actions">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void refetch()}
            disabled={loading}
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <RotateCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={exportLoading}
            onClick={() => void handleExport('csv')}
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <FileSpreadsheet className="h-3.5 w-3.5" />
            <span>Export CSV</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={exportLoading}
            onClick={() => void handleExport('json')}
            className="h-9 px-3 gap-2 rounded-md border-border bg-card text-foreground hover:bg-accent"
          >
            <FileJson className="h-3.5 w-3.5" />
            <span>Export JSON</span>
          </Button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div role="alert" className="rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">
          {error}
        </div>
      )}
      {exportError && (
        <div role="alert" className="rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">
          {exportError}
        </div>
      )}

      {loading && !report && <p role="status" className="surface-panel state-message">Loading the operational report…</p>}
      {exportLoading && <p role="status" className="tone-info rounded-xl border border-border px-4 py-3 text-sm">Preparing the incident export. This may take a moment…</p>}
      {report && (
        <>
          {/* Operational Overview: one connected strip */}
          <section aria-label="Operational overview" className="surface-panel grid grid-cols-2 xl:grid-cols-4">
            {overview.map(({ label, value, note }, index) => (
              <div
                key={label}
                data-tone={['critical', 'moderate', 'resolved', 'info'][index]}
                className={`kpi-cell ${index % 2 === 1 ? 'border-l' : ''} ${index >= 2 ? 'border-t xl:border-t-0' : ''} ${index === 2 ? 'xl:border-l' : ''}`}
              >
                <p className="flex items-center gap-2 text-[0.8125rem] font-semibold text-muted-foreground"><span className="kpi-swatch" aria-hidden="true" />{label}</p>
                <p className="metric-figure mt-3 text-[2.25rem] sm:text-[2.5rem]">{value?.toLocaleString() ?? '—'}</p>
                <p className="mt-2 text-xs text-muted-foreground">{note}</p>
              </div>
            ))}
          </section>

          {/* Response pipeline: how long each stage takes on average */}
          <section className="surface-panel p-5 sm:p-6" aria-labelledby="pipeline-title">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 id="pipeline-title" className="text-base font-bold">Response pipeline</h2>
              <span className="text-xs text-muted-foreground">Average time between stages</span>
            </div>
            <ol className="response-pipeline mt-6">
              {[
                { stage: 'Received', time: null, note: 'Alert reaches dispatch' },
                { stage: 'Acknowledged', time: timing[0][1], note: timing[0][2] },
                { stage: 'Dispatched', time: timing[1][1], note: timing[1][2] },
                { stage: 'On scene', time: timing[2][1], note: timing[2][2] },
                { stage: 'Resolved', time: timing[3][1], note: timing[3][2] },
              ].map(({ stage, time, note }, index) => (
                <li key={stage} className="response-stage">
                  <span className="response-node" aria-hidden="true">{index + 1}</span>
                  <p className="mt-3 text-sm font-semibold">{stage}</p>
                  {time != null ? (
                    <p className="metric-figure mt-1 text-2xl">{time}</p>
                  ) : (
                    <p className="mt-1 font-mono text-2xl font-bold text-muted-foreground">0:00</p>
                  )}
                  <p className="mt-1 text-xs text-muted-foreground">{note}</p>
                </li>
              ))}
            </ol>
          </section>

          <section className="space-y-3">
            <h2 className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.14em] text-muted-foreground">Distribution</h2>
            {/* Breakdown Grid */}
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <Breakdown title="Incident Status" rows={report?.status_distribution ?? []} />
              <Breakdown title="Emergency Types" rows={report?.incident_type_distribution ?? []} />
            </div>
          </section>

          {/* Deployment & Coverage */}
          <section className="space-y-3">
            <h2 className="font-mono text-[0.6875rem] font-semibold uppercase tracking-[0.14em] text-muted-foreground">Deployment &amp; coverage</h2>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <Breakdown title="Barangay Codes" rows={report?.barangay_distribution ?? []} />
              <Breakdown title="Agency Dispatches" rows={report?.agency_dispatches ?? []} />
              <Breakdown title="Responder Assignments" rows={report?.responder_assignments ?? []} />
              <Breakdown title="Unit Deployments" rows={report?.unit_deployments ?? []} />
            </div>
          </section>

          {/* Data Quality Footer */}
          <div className="surface-panel flex flex-wrap items-center justify-between gap-4 p-5">
            <div>
              <p className="font-bold text-foreground">Data quality</p>
              <p className="text-sm text-muted-foreground">
                Unavailable metrics display N/A. Exports exclude shipped demonstration incidents.
              </p>
            </div>
            <Badge variant={report?.false_alarm_rate == null ? 'outline' : 'info'}>
              {report?.false_alarm_rate == null ? 'FALSE ALARM RATE N/A' : `${report.false_alarm_rate.toFixed(1)}% FALSE ALARMS`}
            </Badge>
          </div>

        </>
      )}
    </div>
  );
};
