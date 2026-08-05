import React from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Hop, BarChart3, TrendingUp, Cpu } from 'lucide-react';

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

  return (
    <div className="space-y-6 font-sans">
      {/* Header Info */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">Telemetry Insights & Analytics</h2>
            <Badge variant="outline" className="text-[10px] font-mono border-teal-500/30 text-teal-600 dark:text-teal-400 bg-teal-50 dark:bg-teal-950/40">
              LEGAZPI MESH NETWORK
            </Badge>
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Performance metrics, spatial hop distributions, and responder efficiency analytics
          </p>
        </div>
      </div>

      {/* Top Insights Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-5 space-y-2 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-bold uppercase tracking-wider">
            <span>Total Recorded Events</span>
            <BarChart3 className="h-4 w-4 text-teal-500" />
          </div>
          <div className="text-3xl font-extrabold text-slate-900 dark:text-slate-100 font-mono">{incidents.length}</div>
          <p className="text-[11px] text-slate-400">All registered distress packets</p>
        </Card>

        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-5 space-y-2 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-bold uppercase tracking-wider">
            <span>Mesh Recovery Rate</span>
            <TrendingUp className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="text-3xl font-extrabold text-emerald-600 dark:text-emerald-400 font-mono">{recoveryRate}%</div>
          <Progress value={recoveryRate} className="h-1.5 bg-slate-200 dark:bg-slate-800" indicatorClassName="bg-emerald-500" />
        </Card>

        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-5 space-y-2 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-bold uppercase tracking-wider">
            <span>Average Mesh Hops</span>
            <Hop className="h-4 w-4 text-sky-500" />
          </div>
          <div className="text-3xl font-extrabold text-sky-600 dark:text-sky-400 font-mono">{avgHops}</div>
          <p className="text-[11px] text-slate-400">Hops per packet delivery</p>
        </Card>

        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-5 space-y-2 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 text-xs font-bold uppercase tracking-wider">
            <span>Active Field Fleet</span>
            <Cpu className="h-4 w-4 text-amber-500" />
          </div>
          <div className="text-3xl font-extrabold text-amber-600 dark:text-amber-400 font-mono">{responders.length}</div>
          <p className="text-[11px] text-slate-400">Registered target nodes</p>
        </Card>
      </div>

      {/* Main Breakdown Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Breakdown Card */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-6 space-y-4 shadow-sm">
          <div className="flex items-center justify-between pb-3 border-b border-slate-200 dark:border-slate-800">
            <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">Distress Category Breakdown</h3>
            <Badge variant="outline" className="text-[10px] font-mono">INCIDENT SPECTRUM</Badge>
          </div>

          <div className="space-y-4 text-xs font-mono">
            <div className="space-y-1.5">
              <div className="flex justify-between text-slate-700 dark:text-slate-300">
                <span className="text-red-500 font-bold">CRIME EMERGENCY</span>
                <span>{crimeCount} ({Math.round((crimeCount / total) * 100)}%)</span>
              </div>
              <Progress value={(crimeCount / total) * 100} className="h-2" indicatorClassName="bg-red-500" />
            </div>

            <div className="space-y-1.5">
              <div className="flex justify-between text-slate-700 dark:text-slate-300">
                <span className="text-sky-500 font-bold">MEDICAL SOS</span>
                <span>{medicalCount} ({Math.round((medicalCount / total) * 100)}%)</span>
              </div>
              <Progress value={(medicalCount / total) * 100} className="h-2" indicatorClassName="bg-sky-500" />
            </div>

            <div className="space-y-1.5">
              <div className="flex justify-between text-slate-700 dark:text-slate-300">
                <span className="text-amber-500 font-bold">ACCIDENT COLLISION</span>
                <span>{accidentCount} ({Math.round((accidentCount / total) * 100)}%)</span>
              </div>
              <Progress value={(accidentCount / total) * 100} className="h-2" indicatorClassName="bg-amber-500" />
            </div>

            <div className="space-y-1.5">
              <div className="flex justify-between text-slate-700 dark:text-slate-300">
                <span className="text-purple-500 font-bold">NATURAL DISASTER</span>
                <span>{disasterCount} ({Math.round((disasterCount / total) * 100)}%)</span>
              </div>
              <Progress value={(disasterCount / total) * 100} className="h-2" indicatorClassName="bg-purple-500" />
            </div>
          </div>
        </Card>

        {/* Mesh Transport Architecture Stats */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 p-6 space-y-4 shadow-sm">
          <div className="flex items-center justify-between pb-3 border-b border-slate-200 dark:border-slate-800">
            <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">Dual-Channel Transport Health</h3>
            <Badge variant="outline" className="text-[10px] font-mono border-emerald-500/30 text-emerald-600 dark:text-emerald-400">
              OPERATIONAL
            </Badge>
          </div>

          <div className="space-y-3 text-xs text-slate-600 dark:text-slate-300">
            <div className="p-3.5 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between">
              <div>
                <div className="font-bold text-slate-900 dark:text-slate-100">BLE 5.0 Extended Mesh Layer</div>
                <div className="text-[11px] text-slate-400">255-byte advertising channel</div>
              </div>
              <Badge className="bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 border-0">ACTIVE</Badge>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between">
              <div>
                <div className="font-bold text-slate-900 dark:text-slate-100">Legacy GATT Fallback Layer</div>
                <div className="text-[11px] text-slate-400">31-byte fragmentation protocol</div>
              </div>
              <Badge className="bg-sky-500/20 text-sky-700 dark:text-sky-300 border-0">READY</Badge>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between">
              <div>
                <div className="font-bold text-slate-900 dark:text-slate-100">Cloud Uplink Shortcut Engine</div>
                <div className="text-[11px] text-slate-400">Auto-upload upon cellular load</div>
              </div>
              <Badge className="bg-teal-500/20 text-teal-700 dark:text-teal-300 border-0">ONLINE</Badge>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};
