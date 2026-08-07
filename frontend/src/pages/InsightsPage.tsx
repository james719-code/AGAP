import React from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Hop, BarChart3, TrendingUp, RadioTower, PieChart } from 'lucide-react';

export const InsightsPage: React.FC = () => {
  const { incidents } = useIncidents();
  const { responders } = useResponders();

  const total = incidents.length || 1;
  const crimeCount = incidents.filter((i) => i.incident_type === 'CRIME').length;
  const medicalCount = incidents.filter((i) => i.incident_type === 'MEDICAL').length;
  const accidentCount = incidents.filter((i) => i.incident_type === 'ACCIDENT').length;
  const disasterCount = incidents.filter((i) => i.incident_type === 'DISASTER').length;

  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;
  const recoveryRate = Math.round((resolvedCount / total) * 100);

  const avgHops = (
    incidents.reduce((acc, curr) => acc + (curr.hop_count || 1), 0) / total
  ).toFixed(1);

  const distribution = [
    { label: 'CRIME SOS', value: crimeCount, pct: (crimeCount / total) * 100, tone: 'text-destructive', bar: 'bg-destructive' },
    { label: 'MEDICAL SOS', value: medicalCount, pct: (medicalCount / total) * 100, tone: 'text-[#1F91A8]', bar: 'bg-secondary' },
    { label: 'ACCIDENT COLLISION', value: accidentCount, pct: (accidentCount / total) * 100, tone: 'text-[#F2A540]', bar: 'bg-[#F2A540]' },
    { label: 'NATURAL DISASTER', value: disasterCount, pct: (disasterCount / total) * 100, tone: 'text-violet-500', bar: 'bg-violet-500' },
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
            <div className="text-3xl font-extrabold tracking-tight text-foreground tabular-nums mt-1">{incidents.length}</div>
            <p className="text-[11px] text-muted-foreground">All registered distress packets</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5 flex flex-col gap-1">
            <div className="flex items-center justify-between text-muted-foreground text-xs font-semibold uppercase tracking-wider">
              <span>Mesh Recovery Rate</span>
              <TrendingUp className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div className="text-3xl font-extrabold tracking-tight text-emerald-600 dark:text-emerald-400 tabular-nums mt-1">{recoveryRate}%</div>
            <Progress value={recoveryRate} className="h-1.5 mt-2" indicatorClassName="bg-emerald-500" />
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
            <div className="text-3xl font-extrabold tracking-tight text-foreground tabular-nums mt-1">{responders.length}</div>
            <p className="text-[11px] text-muted-foreground">Units on the mesh network</p>
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

        {/* Mesh Transport Architecture Stats */}
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <h3 className="text-base font-bold text-foreground">Dual-Channel Transport Health</h3>
              <Badge variant="success" className="text-[10px] font-medium">OPERATIONAL</Badge>
            </div>

            <div className="space-y-3 text-sm">
              {[
                { name: 'BLE 5.0 Extended Mesh Layer', sub: '255-byte advertising channel', tone: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400', badge: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300', label: 'ACTIVE' },
                { name: 'Legacy GATT Fallback Layer', sub: '31-byte fragmentation protocol', tone: 'bg-secondary/10 text-[#1F91A8]', badge: 'bg-secondary/15 text-[#1F91A8] dark:text-[#4FB4C9]', label: 'READY' },
                { name: 'Cloud Uplink Shortcut Engine', sub: 'Auto-upload upon cellular load', tone: 'bg-[#F2A540]/10 text-[#E8902D]', badge: 'bg-[#F2A540]/15 text-[#B97A1F] dark:text-[#F2A540]', label: 'ONLINE' },
              ].map((row) => (
                <div key={row.name} className="p-3.5 bg-muted rounded-lg border border-border flex items-center justify-between gap-3">
                  <div>
                    <div className="font-bold text-card-foreground">{row.name}</div>
                    <div className="text-[11px] text-muted-foreground">{row.sub}</div>
                  </div>
                  <Badge className={`${row.badge} border-0 text-[10px] font-semibold`}>{row.label}</Badge>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

