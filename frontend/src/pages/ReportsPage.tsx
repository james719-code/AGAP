import React from 'react';
import { Clock, FileJson, FileSpreadsheet, RotateCw, ShieldCheck, Siren, Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useIncidents } from '@/hooks/useIncidents';
import { useOperationalReport } from '@/hooks/useOperationalReport';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import type { AnalyticsBreakdown } from '@/types/api';

const minutes = (value: number | null | undefined) => value == null ? 'N/A' : `${value.toFixed(1)}m`;

function SummaryCard({ title, badge, rows }: { title: string; badge: string; rows: AnalyticsBreakdown[] }) {
  return (
    <Card><CardContent className="p-6">
      <div className="mb-4 flex items-center justify-between border-b border-border pb-3"><h3 className="font-bold">{title}</h3><Badge variant="outline" className="text-[10px]">{badge}</Badge></div>
      <div className="space-y-3">
        {rows.length === 0 ? <p className="py-5 text-center text-sm text-muted-foreground">No verified records</p> : rows.slice(0, 8).map((row) => (
          <div key={row.label} className="flex items-center justify-between gap-4 rounded-md border border-border bg-muted/50 px-3 py-2"><span className="min-w-0 truncate text-sm font-semibold">{row.label}</span><span className="shrink-0 tabular-nums text-muted-foreground">{row.count}</span></div>
        ))}
      </div>
    </CardContent></Card>
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

  return <div className="space-y-6">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-xs font-semibold uppercase tracking-widest text-primary">Reports</p><h2 className="mt-1 text-2xl font-extrabold tracking-tight">Operational Reports</h2><p className="mt-1 text-sm text-muted-foreground">Backend-verified incident, response, and deployment records</p></div>
      <div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => void refetch()} disabled={loading}><RotateCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Refresh</Button><Button variant="outline" size="sm" disabled={exportLoading} onClick={() => exportIncidentsToCSV(incidents)}><FileSpreadsheet className="mr-2 h-4 w-4" />CSV</Button><Button variant="outline" size="sm" disabled={exportLoading} onClick={() => exportIncidentsToJSON(incidents)}><FileJson className="mr-2 h-4 w-4" />JSON</Button></div>
    </div>
    {error && <div className="rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600">{error}</div>}
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{topCards.map(({ label, value, icon: Icon, note }) => <Card key={label}><CardContent className="p-5"><div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground"><span>{label}</span><Icon className="h-4 w-4 text-primary" /></div><div className="mt-2 text-3xl font-extrabold tabular-nums">{loading ? '—' : value}</div><p className="mt-2 text-[11px] text-muted-foreground">{note}</p></CardContent></Card>)}</div>
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{timeCards.map(({ label, value, note }) => <Card key={label}><CardContent className="p-5"><div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground"><span>{label}</span><Clock className="h-4 w-4 text-primary" /></div><div className="mt-2 text-2xl font-extrabold tabular-nums">{loading ? '—' : value}</div><p className="mt-2 text-[11px] text-muted-foreground">{note}</p></CardContent></Card>)}</div>
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2"><SummaryCard title="Incident Status" badge="REAL STATUS" rows={report?.status_distribution ?? []} /><SummaryCard title="Emergency Types" badge="INCIDENTS" rows={report?.incident_type_distribution ?? []} /><SummaryCard title="Barangay Codes" badge="TOP AREAS" rows={report?.barangay_distribution ?? []} /><SummaryCard title="Agency Dispatches" badge="ACTUAL DISPATCHES" rows={report?.agency_dispatches ?? []} /><SummaryCard title="Responder Assignments" badge="ACTUAL ASSIGNMENTS" rows={report?.responder_assignments ?? []} /><SummaryCard title="Unit Deployments" badge="ACTUAL UNITS" rows={report?.unit_deployments ?? []} /></div>
    <Card><CardContent className="flex flex-wrap items-center justify-between gap-4 p-5"><div><p className="font-bold">Data quality</p><p className="text-sm text-muted-foreground">Unavailable timestamps display N/A. Exports use operational incidents and exclude shipped demonstrations.</p></div><Badge variant={report?.false_alarm_rate == null ? 'outline' : 'info'}>{report?.false_alarm_rate == null ? 'FALSE ALARM RATE N/A' : `${report.false_alarm_rate.toFixed(1)}% FALSE ALARMS`}</Badge></CardContent></Card>
    {report?.generated_at && <p className="text-right text-[11px] text-muted-foreground">Calculated by backend at {new Date(report.generated_at).toLocaleString()}</p>}
  </div>;
};
