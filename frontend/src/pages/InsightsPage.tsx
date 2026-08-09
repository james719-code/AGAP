import React from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { isResponderActive } from '@/lib/responderStatus';
import { Hop, BarChart3, TrendingUp, RadioTower, PieChart, Waypoints } from 'lucide-react';

export const InsightsPage: React.FC = () => {
  // fetchAll: this page computes aggregates (type distribution, recovery
  // rate, avg hops) that must reflect every incident, not just one page.
  const { incidents, total: totalCount, loading: incidentsLoading } = useIncidents({}, { fetchAll: true });
  const { responders } = useResponders();

  const total = totalCount || 1;
  const crimeCount = incidents.filter((i) => i.incident_type === 'CRIME').length;
  const medicalCount = incidents.filter((i) => i.incident_type === 'MEDICAL').length;
  const accidentCount = incidents.filter((i) => i.incident_type === 'ACCIDENT').length;
  const disasterCount = incidents.filter((i) => i.incident_type === 'DISASTER').length;
  const fireCount = incidents.filter((i) => i.incident_type === 'FIRE').length;

  // Mesh Recovery Rate = % of BLE_MESH-channel incidents that were actually
  // relayed (hop_count > 0), i.e. delivery via the mesh network itself
  // rather than a direct/first-hop uplink. Previously this was resolved /
  // total, which measured incident resolution, not mesh delivery at all —
  // unrelated metrics that happened to share a percentage shape.
  const bleMeshIncidents = incidents.filter((i) => i.channel === 'BLE_MESH');
  const bleMeshRelayed = bleMeshIncidents.filter((i) => (i.hop_count ?? 0) > 0);
  const hasBleMeshData = bleMeshIncidents.length > 0;
  const recoveryRate = hasBleMeshData
    ? Math.round((bleMeshRelayed.length / bleMeshIncidents.length) * 100)
    : null;

  // hop_count can legitimately be 0 (delivered on the first hop / direct
  // uplink). `|| 1` silently treated every such incident as if it had taken
  // 1 hop, inflating the average. `?? 0` only substitutes for missing data.
  const avgHops = (
    incidents.reduce((acc, curr) => acc + (curr.hop_count ?? 0), 0) / total
  ).toFixed(1);

  // "Active Responders" should mean actually on duty, not just "exists as a
  // record" — a responder marked OFFLINE, or one that stopped reporting its
  // location a while ago, isn't part of the operational count.
  const activeResponders = responders.filter((r) => isResponderActive(r));

  const distribution = [
    { label: 'CRIME SOS', value: crimeCount, pct: (crimeCount / total) * 100, tone: 'text-destructive', bar: 'bg-destructive' },
    { label: 'MEDICAL SOS', value: medicalCount, pct: (medicalCount / total) * 100, tone: 'text-[#1F91A8]', bar: 'bg-secondary' },
    { label: 'ACCIDENT COLLISION', value: accidentCount, pct: (accidentCount / total) * 100, tone: 'text-[#F2A540]', bar: 'bg-[#F2A540]' },
    { label: 'NATURAL DISASTER', value: disasterCount, pct: (disasterCount / total) * 100, tone: 'text-violet-500', bar: 'bg-violet-500' },
    { label: 'FIRE', value: fireCount, pct: (fireCount / total) * 100, tone: 'text-orange-600', bar: 'bg-orange-600' },
  ];

  // Real, derived-from-data channel breakdown — replaces the previous
  // hardcoded "ACTIVE/READY/ONLINE" cards, which had no backend signal
  // behind them at all (health_handler.go only reports DB ping/uptime;
  // there's no BLE/GATT/cloud-uplink status anywhere in the system). These
  // numbers come straight from the incidents actually recorded.
  const bleMeshCount = bleMeshIncidents.length;
  const cloudShortcutCount = incidents.filter((i) => i.delivery_path === 'BLE_CLOUD_SHORTCUT').length;
  const directOnlineCount = incidents.filter((i) => i.channel === 'ONLINE' && i.delivery_path !== 'BLE_CLOUD_SHORTCUT').length;
  const channelBreakdown = [
    {
      name: 'BLE Mesh Relay',
      sub: `${bleMeshCount} incident${bleMeshCount === 1 ? '' : 's'} received over BLE mesh`,
      count: bleMeshCount,
      pct: total > 0 ? Math.round((bleMeshCount / total) * 100) : 0,
    },
    {
      name: 'BLE Cloud Shortcut',
      sub: `${cloudShortcutCount} incident${cloudShortcutCount === 1 ? '' : 's'} auto-uploaded via cellular`,
      count: cloudShortcutCount,
      pct: total > 0 ? Math.round((cloudShortcutCount / total) * 100) : 0,
    },
    {
      name: 'Direct Online Uplink',
      sub: `${directOnlineCount} incident${directOnlineCount === 1 ? '' : 's'} submitted directly online`,
      count: directOnlineCount,
      pct: total > 0 ? Math.round((directOnlineCount / total) * 100) : 0,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-widest text-primary">Intelligence</p>
        <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Telemetry Insights &amp; Analytics</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Performance metrics, spatial hop distributions, and responder efficiency analytics
        </p>
      </div>

      {/* Top Insight Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-5 flex flex-col gap-1">
            <div className="flex items-center justify-between text-muted-foreground text-xs font-semibold uppercase tracking-wider">
              <span>Total Recorded Events</span>
              <BarChart3 className="h-4 w-4 text-[#1F91A8]" />
            </div>
            <div className="text-3xl font-extrabold tracking-tight text-foreground tabular-nums mt-1">
              {incidentsLoading ? '—' : totalCount}
            </div>
            <p className="text-[11px] text-muted-foreground">All registered distress packets</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5 flex flex-col gap-1">
            <div className="flex items-center justify-between text-muted-foreground text-xs font-semibold uppercase tracking-wider">
              <span>Mesh Recovery Rate</span>
              <TrendingUp className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div className="text-3xl font-extrabold tracking-tight text-emerald-600 dark:text-emerald-400 tabular-nums mt-1">
              {recoveryRate === null ? 'N/A' : `${recoveryRate}%`}
            </div>
            <Progress value={recoveryRate ?? 0} className="h-1.5 mt-2" indicatorClassName="bg-emerald-500" />
            <p className="text-[11px] text-muted-foreground">
              {hasBleMeshData ? `${bleMeshRelayed.length} of ${bleMeshIncidents.length} BLE mesh incidents relayed` : 'No BLE mesh incidents recorded yet'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5 flex flex-col gap-1">
            <div className="flex items-center justify-between text-muted-foreground text-xs font-semibold uppercase tracking-wider">
              <span>Average Mesh Hops</span>
              <Hop className="h-4 w-4 text-[#F2A540]" />
            </div>
            <div className="text-3xl font-extrabold tracking-tight text-foreground tabular-nums mt-1">{avgHops}</div>
            <p className="text-[11px] text-muted-foreground">Relay nodes per delivery path</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5 flex flex-col gap-1">
            <div className="flex items-center justify-between text-muted-foreground text-xs font-semibold uppercase tracking-wider">
              <span>Active Responders</span>
              <RadioTower className="h-4 w-4 text-violet-500" />
            </div>
            <div className="text-3xl font-extrabold tracking-tight text-foreground tabular-nums mt-1">{activeResponders.length}</div>
            <p className="text-[11px] text-muted-foreground">On duty of {responders.length} total units</p>
          </CardContent>
        </Card>
      </div>

      {/* Detailed panels */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Incident Type Distribution */}
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <PieChart className="h-4 w-4 text-[#1F91A8]" /> Incident Type Distribution
              </h3>
              <Badge variant="outline" className="text-[10px] font-medium">{total} TOTAL</Badge>
            </div>

            <div className="space-y-4">
              {distribution.map((d) => (
                <div key={d.label} className="space-y-1.5">
                  <div className="flex justify-between text-sm">
                    <span className={`font-bold ${d.tone}`}>{d.label}</span>
                    <span className="text-muted-foreground tabular-nums">{d.value} ({Math.round(d.pct)}%)</span>
                  </div>
                  <Progress value={d.pct} className="h-2" indicatorClassName={d.bar} />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Channel Breakdown — derived from actual recorded incidents.
            No backend endpoint reports live BLE/GATT/cloud transport
            status (health_handler.go only exposes DB ping + uptime), so
            this shows real counts instead of implying a live health check
            that doesn't exist. */}
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <Waypoints className="h-4 w-4 text-[#1F91A8]" /> Delivery Channel Breakdown
              </h3>
              <Badge variant="outline" className="text-[10px] font-medium">FROM RECORDED TELEMETRY</Badge>
            </div>

            <div className="space-y-3 text-sm">
              {channelBreakdown.map((row) => (
                <div key={row.name} className="p-3.5 bg-muted rounded-lg border border-border flex items-center justify-between gap-3">
                  <div>
                    <div className="font-bold text-card-foreground">{row.name}</div>
                    <div className="text-[11px] text-muted-foreground">{row.sub}</div>
                  </div>
                  <Badge variant="outline" className="text-[10px] font-semibold tabular-nums">
                    {row.count} ({row.pct}%)
                  </Badge>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};