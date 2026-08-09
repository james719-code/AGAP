import React from 'react';
import { BarChart3, Hop, RadioTower, RotateCw, Route, ShieldCheck, Waypoints } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { useTelemetryInsights } from '@/hooks/useTelemetryInsights';
import type { AnalyticsBreakdown } from '@/types/api';

const pathLabels: Record<string, string> = {
  DIRECT_UPLINK: 'Direct Online Uplink',
  BLE_CLOUD_SHORTCUT: 'BLE Cloud Shortcut',
};

const channelLabels: Record<string, string> = {
  ONLINE: 'Online',
  BLE_MESH: 'BLE Mesh',
};

function Breakdown({ rows, total, labels = {} }: { rows: AnalyticsBreakdown[]; total: number; labels?: Record<string, string> }) {
  if (rows.length === 0) return <p className="py-8 text-center text-sm text-muted-foreground">No operational telemetry recorded yet.</p>;
  return (
    <div className="space-y-4">
      {rows.map((row) => {
        const percentage = total > 0 ? (row.count / total) * 100 : 0;
        return (
          <div key={row.label} className="space-y-1.5">
            <div className="flex justify-between gap-3 text-sm">
              <span className="font-semibold text-foreground">{labels[row.label] ?? row.label}</span>
              <span className="tabular-nums text-muted-foreground">{row.count} ({Math.round(percentage)}%)</span>
            </div>
            <Progress value={percentage} className="h-2" indicatorClassName="bg-[#1F91A8]" />
          </div>
        );
      })}
    </div>
  );
}

export const InsightsPage: React.FC = () => {
  const { insights, loading, error, refetch } = useTelemetryInsights();
  const deliveryRate = insights?.mesh_delivery_rate ?? null;

  const cards = [
    { label: 'Operational Incidents', value: insights?.total_incidents, icon: ShieldCheck, detail: 'Demo incidents excluded' },
    { label: 'Telemetry Packets', value: insights?.total_packets, icon: BarChart3, detail: `${insights?.total_delivery_events ?? 0} recorded delivery events` },
    { label: 'Mesh Delivery Rate', value: deliveryRate === null ? 'N/A' : `${deliveryRate.toFixed(1)}%`, icon: Route, detail: deliveryRate === null ? 'No real BLE mesh packets recorded' : `${insights?.delivered_mesh_packets ?? 0} of ${insights?.mesh_packets ?? 0} packets reached an uplink or target` },
    { label: 'Average Relay Hops', value: insights?.average_relay_hops == null ? 'N/A' : insights.average_relay_hops.toFixed(1), icon: Hop, detail: 'RELAYED events per BLE mesh packet' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Intelligence</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Telemetry Insights &amp; Analytics</h2>
          <p className="mt-1 text-sm text-muted-foreground">Server-calculated metrics from operational packets and mesh delivery events</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => void refetch()} disabled={loading}>
          <RotateCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </Button>
      </div>

      {error && <div className="rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-300">{error}</div>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map(({ label, value, icon: Icon, detail }) => (
          <Card key={label}>
            <CardContent className="flex flex-col gap-1 p-5">
              <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <span>{label}</span><Icon className="h-4 w-4 text-[#1F91A8]" />
              </div>
              <div className="mt-1 text-3xl font-extrabold tabular-nums tracking-tight text-foreground">{loading ? '—' : (value ?? 0)}</div>
              <p className="text-[11px] text-muted-foreground">{detail}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card><CardContent className="p-4"><p className="text-xs uppercase text-muted-foreground">Relayed Packets</p><p className="mt-1 text-2xl font-bold tabular-nums">{insights?.relayed_packets ?? 0}</p></CardContent></Card>
        <Card><CardContent className="p-4"><p className="text-xs uppercase text-muted-foreground">Unique Relay Devices</p><p className="mt-1 text-2xl font-bold tabular-nums">{insights?.unique_relay_devices ?? 0}</p></CardContent></Card>
        <Card><CardContent className="p-4"><p className="text-xs uppercase text-muted-foreground">Duplicate Drops</p><p className="mt-1 text-2xl font-bold tabular-nums">{insights?.duplicate_drops ?? 0}</p></CardContent></Card>
        <Card><CardContent className="p-4"><div className="flex items-center justify-between"><p className="text-xs uppercase text-muted-foreground">Active Responders</p><RadioTower className="h-4 w-4 text-violet-500" /></div><p className="mt-1 text-2xl font-bold tabular-nums">{insights?.active_responders ?? 0}<span className="text-sm font-normal text-muted-foreground"> / {insights?.total_responders ?? 0}</span></p></CardContent></Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card><CardContent className="space-y-4 p-6"><div className="flex items-center justify-between border-b border-border pb-3"><h3 className="flex items-center gap-2 font-bold"><Waypoints className="h-4 w-4 text-[#1F91A8]" />Incident Types</h3><Badge variant="outline">{insights?.total_incidents ?? 0} TOTAL</Badge></div><Breakdown rows={insights?.incident_type_distribution ?? []} total={insights?.total_incidents ?? 0} /></CardContent></Card>
        <Card><CardContent className="space-y-4 p-6"><div className="border-b border-border pb-3"><h3 className="font-bold">Intake Channels</h3></div><Breakdown rows={insights?.channel_distribution ?? []} total={insights?.total_incidents ?? 0} labels={channelLabels} /></CardContent></Card>
        <Card><CardContent className="space-y-4 p-6"><div className="border-b border-border pb-3"><h3 className="font-bold">Delivery Paths</h3></div><Breakdown rows={insights?.delivery_path_distribution ?? []} total={insights?.total_incidents ?? 0} labels={pathLabels} /></CardContent></Card>
      </div>

      {insights?.generated_at && <p className="text-right text-[11px] text-muted-foreground">Calculated by backend at {new Date(insights.generated_at).toLocaleString()}</p>}
    </div>
  );
};
