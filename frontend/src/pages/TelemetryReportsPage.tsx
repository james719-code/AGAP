import React from 'react';
import { BarChart3, Clock, FileJson, FileSpreadsheet, Hop, RadioTower, RotateCw, Route, ShieldCheck, Siren } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { useIncidents } from '@/hooks/useIncidents';
import { useTelemetryReports } from '@/hooks/useTelemetryReports';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import type { AnalyticsBreakdown } from '@/types/api';

const labels: Record<string, string> = {
  DIRECT_UPLINK: 'Direct Online Uplink', BLE_CLOUD_SHORTCUT: 'BLE Cloud Shortcut',
  ONLINE: 'Online', BLE_MESH: 'BLE Mesh',
};
const minutes = (value: number | null | undefined) => value == null ? 'N/A' : `${value.toFixed(1)}m`;

function Breakdown({ title, rows, total, progress = false }: { title: string; rows: AnalyticsBreakdown[]; total?: number; progress?: boolean }) {
  return <Card><CardContent className="p-6">
    <div className="mb-4 border-b border-border pb-3"><h3 className="font-bold">{title}</h3></div>
    {rows.length === 0 ? <p className="py-6 text-center text-sm text-muted-foreground">No verified records yet.</p> :
      <div className="space-y-3">{rows.slice(0, 8).map((row) => {
        const percentage = total && total > 0 ? row.count * 100 / total : 0;
        return <div key={row.label} className="space-y-1.5">
          <div className="flex items-center justify-between gap-4 text-sm"><span className="min-w-0 truncate font-semibold">{labels[row.label] ?? row.label}</span><span className="shrink-0 tabular-nums text-muted-foreground">{row.count}{progress ? ` (${Math.round(percentage)}%)` : ''}</span></div>
          {progress && <Progress value={percentage} className="h-2" indicatorClassName="bg-[#1F91A8]" />}
        </div>;
      })}</div>}
  </CardContent></Card>;
}

export const TelemetryReportsPage: React.FC = () => {
  const { insights, report, loading, error, refetch } = useTelemetryReports();
  const { incidents, loading: exportLoading } = useIncidents({ exclude_demo: true }, { fetchAll: true });
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
  const telemetry = [
    { label: 'Telemetry Packets', value: insights?.total_packets ?? 0, icon: BarChart3, note: `${insights?.total_delivery_events ?? 0} delivery events` },
    { label: 'Mesh Delivery Rate', value: insights?.mesh_delivery_rate == null ? 'N/A' : `${insights.mesh_delivery_rate.toFixed(1)}%`, icon: Route, note: `${insights?.delivered_mesh_packets ?? 0} of ${insights?.mesh_packets ?? 0} mesh packets delivered` },
    { label: 'Average Relay Hops', value: insights?.average_relay_hops == null ? 'N/A' : insights.average_relay_hops.toFixed(1), icon: Hop, note: 'Relayed events per mesh packet' },
    { label: 'Duplicate Drops', value: insights?.duplicate_drops ?? 0, icon: ShieldCheck, note: `${insights?.relayed_packets ?? 0} relayed packets via ${insights?.unique_relay_devices ?? 0} devices` },
  ];

  const cards = (items: typeof overview) => <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{items.map(({ label, value, icon: Icon, note }) =>
    <Card key={label}><CardContent className="p-5"><div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground"><span>{label}</span><Icon className="h-4 w-4 text-primary" /></div><div className="mt-2 text-3xl font-extrabold tabular-nums">{loading ? '—' : value}</div><p className="mt-2 text-[11px] text-muted-foreground">{note}</p></CardContent></Card>)}</div>;

  return <div className="space-y-8">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-xs font-semibold uppercase tracking-widest text-primary">Intelligence</p><h2 className="mt-1 text-2xl font-extrabold tracking-tight">Telemetry &amp; Reports</h2><p className="mt-1 text-sm text-muted-foreground">Network reliability, incident performance, and verified operational records</p></div>
      <div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => void refetch()} disabled={loading}><RotateCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Refresh</Button><Button variant="outline" size="sm" disabled={exportLoading} onClick={() => exportIncidentsToCSV(incidents)}><FileSpreadsheet className="mr-2 h-4 w-4" />CSV</Button><Button variant="outline" size="sm" disabled={exportLoading} onClick={() => exportIncidentsToJSON(incidents)}><FileJson className="mr-2 h-4 w-4" />JSON</Button></div>
    </div>
    {error && <div className="rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">{error}</div>}

    <section className="space-y-4"><h3 className="text-lg font-bold">Operational overview</h3>{cards(overview)}</section>
    <section className="space-y-4"><h3 className="text-lg font-bold">Response performance</h3><div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{timing.map(([label, value, note]) => <Card key={label}><CardContent className="p-5"><div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground"><span>{label}</span><Clock className="h-4 w-4 text-primary" /></div><div className="mt-2 text-2xl font-extrabold tabular-nums">{loading ? '—' : value}</div><p className="mt-2 text-[11px] text-muted-foreground">{note}</p></CardContent></Card>)}</div><div className="grid grid-cols-1 gap-6 lg:grid-cols-2"><Breakdown title="Incident Status" rows={report?.status_distribution ?? []} /><Breakdown title="Emergency Types" rows={report?.incident_type_distribution ?? []} /></div></section>
    <section className="space-y-4"><h3 className="text-lg font-bold">Network telemetry</h3>{cards(telemetry)}<div className="grid grid-cols-1 gap-6 lg:grid-cols-2"><Breakdown title="Intake Channels" rows={insights?.channel_distribution ?? []} total={insights?.total_incidents} progress /><Breakdown title="Delivery Paths" rows={insights?.delivery_path_distribution ?? []} total={insights?.total_incidents} progress /></div></section>
    <section className="space-y-4"><h3 className="text-lg font-bold">Deployment &amp; coverage</h3><div className="grid grid-cols-1 gap-6 lg:grid-cols-2"><Breakdown title="Barangay Codes" rows={report?.barangay_distribution ?? []} /><Breakdown title="Agency Dispatches" rows={report?.agency_dispatches ?? []} /><Breakdown title="Responder Assignments" rows={report?.responder_assignments ?? []} /><Breakdown title="Unit Deployments" rows={report?.unit_deployments ?? []} /></div></section>
    <Card><CardContent className="flex flex-wrap items-center justify-between gap-4 p-5"><div><p className="font-bold">Data quality</p><p className="text-sm text-muted-foreground">Unavailable metrics display N/A. Exports exclude shipped demonstration incidents.</p></div><Badge variant={report?.false_alarm_rate == null ? 'outline' : 'info'}>{report?.false_alarm_rate == null ? 'FALSE ALARM RATE N/A' : `${report.false_alarm_rate.toFixed(1)}% FALSE ALARMS`}</Badge></CardContent></Card>
    {(report?.generated_at || insights?.generated_at) && <p className="text-right text-[11px] text-muted-foreground">Backend snapshot generated {new Date(report?.generated_at ?? insights!.generated_at).toLocaleString()}</p>}
  </div>;
};
