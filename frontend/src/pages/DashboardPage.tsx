import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useWebSocket } from '@/hooks/useWebSocket';
import { IncidentMap } from '@/components/map/IncidentMap';
import { LiveFeed } from '@/components/incidents/LiveFeed';
import { DemoPresetPanel } from '@/components/demo/DemoPresetPanel';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ScrollArea } from '@/components/ui/scroll-area';
import { AlertTriangle, ShieldAlert, CheckCircle, Users, Hop, Layers, Radio, RefreshCw } from 'lucide-react';
import type { Incident } from '@/types/api';

export const DashboardPage: React.FC = () => {
  const { incidents, refetch: refetchIncidents } = useIncidents();
  const { responders } = useResponders();
  const { liveIncidents, isConnected } = useWebSocket();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);

  const activeCount = incidents.filter((i) => i.status === 'ACTIVE').length;
  const dispatchedCount = incidents.filter((i) => i.status === 'DISPATCHED').length;
  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;
  const responderCount = responders.length;

  return (
    <div className="space-y-6 font-sans">
      {/* Header Info */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-slate-100">GIS Command Dispatch Center</h2>
            <Badge variant="outline" className="text-[10px] font-mono border-emerald-500/30 text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40">
              LEGAZPI CITY REGION V
            </Badge>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
            Real-time emergency incident telemetry & spatial P2P mesh monitoring
          </p>
        </div>

        <Button onClick={() => refetchIncidents()} variant="outline" size="sm" className="h-9 px-3 border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100 self-start sm:self-auto flex items-center gap-2">
          <RefreshCw className="h-3.5 w-3.5" />
          <span>Refresh Telemetry</span>
        </Button>
      </div>

      <Separator />

      {/* Demo Scenario Presets Toolbar */}
      <DemoPresetPanel />

      {/* Overview Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active SOS Card */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 shadow-sm relative overflow-hidden">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-red-600 dark:text-red-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-red-500 animate-pulse" /> Active Distress SOS
              </p>
              <p className="text-3xl font-extrabold text-slate-900 dark:text-slate-100 mt-1.5 font-mono">{activeCount}</p>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">Requires immediate triage</p>
            </div>
            <div className="p-3.5 bg-red-500/10 rounded-xl border border-red-500/20 shadow-inner">
              <AlertTriangle className="h-6 w-6 text-red-500 dark:text-red-400 animate-bounce" />
            </div>
          </CardContent>
        </Card>

        {/* Dispatched Units Card */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 shadow-sm relative overflow-hidden">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-amber-600 dark:text-amber-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
                Dispatched Responders
              </p>
              <p className="text-3xl font-extrabold text-slate-900 dark:text-slate-100 mt-1.5 font-mono">{dispatchedCount}</p>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">En route to Target IDs</p>
            </div>
            <div className="p-3.5 bg-amber-500/10 rounded-xl border border-amber-500/20 shadow-inner">
              <ShieldAlert className="h-6 w-6 text-amber-500 dark:text-amber-400" />
            </div>
          </CardContent>
        </Card>

        {/* Resolved Incidents Card */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 shadow-sm relative overflow-hidden">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-emerald-600 dark:text-emerald-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
                Resolved Alerts
              </p>
              <p className="text-3xl font-extrabold text-slate-900 dark:text-slate-100 mt-1.5 font-mono">{resolvedCount}</p>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">Successfully cleared</p>
            </div>
            <div className="p-3.5 bg-emerald-500/10 rounded-xl border border-emerald-500/20 shadow-inner">
              <CheckCircle className="h-6 w-6 text-emerald-500 dark:text-emerald-400" />
            </div>
          </CardContent>
        </Card>

        {/* Field Responders Card */}
        <Card className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 shadow-sm relative overflow-hidden">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-sky-600 dark:text-sky-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
                Active Responder Fleet
              </p>
              <p className="text-3xl font-extrabold text-slate-900 dark:text-slate-100 mt-1.5 font-mono">{responderCount}</p>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">Mesh listening nodes</p>
            </div>
            <div className="p-3.5 bg-sky-500/10 rounded-xl border border-sky-500/20 shadow-inner">
              <Users className="h-6 w-6 text-sky-500 dark:text-sky-400" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Grid: Interactive Map + Live Telemetry Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[560px]">
        {/* Interactive Map Column */}
        <div className="lg:col-span-2 h-full flex flex-col space-y-2.5">
          {selectedIncident && (
            <div className="px-3.5 py-2.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl text-xs font-mono flex items-center justify-between text-slate-800 dark:text-slate-300 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="text-emerald-600 dark:text-emerald-400 font-bold flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-emerald-400 animate-ping" /> INSPECTING VECTOR:
                </span>
                <span>Incident #{selectedIncident.id}</span>
                <span className="flex items-center gap-1 text-sky-600 dark:text-sky-400">
                  <Hop className="h-3.5 w-3.5" /> Hops: {selectedIncident.hop_count}
                </span>
                <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                  <Layers className="h-3.5 w-3.5" /> Channel: {selectedIncident.channel}
                </span>
              </div>
              <button
                onClick={() => setSelectedIncident(null)}
                className="text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 text-xs underline font-sans"
              >
                Clear Selection
              </button>
            </div>
          )}

          <div className="flex-1 rounded-2xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-2xl relative">
            <IncidentMap
              incidents={incidents}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full"
            />
          </div>
        </div>

        {/* Telemetry Stream Column */}
        <div className="h-full bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 flex flex-col space-y-3 shadow-sm">
          <div className="flex items-center justify-between pb-2 border-b border-slate-200 dark:border-slate-800">
            <div className="flex items-center gap-2">
              <Radio className={`h-4 w-4 ${isConnected ? 'text-sky-500 dark:text-sky-400 animate-pulse' : 'text-slate-400'}`} />
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-200">Mesh Telemetry Live Feed</h3>
            </div>
            <Badge variant="outline" className="text-[10px] font-mono border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">
              {liveIncidents.length} PACKETS
            </Badge>
          </div>

          <ScrollArea className="flex-1 pr-1">
            <LiveFeed incidents={liveIncidents} isConnected={isConnected} />
          </ScrollArea>
        </div>
      </div>
    </div>
  );
};
