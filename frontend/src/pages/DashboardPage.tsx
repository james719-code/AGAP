import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useWebSocket } from '@/hooks/useWebSocket';
import { IncidentMap } from '@/components/map/IncidentMap';
import { LiveFeed } from '@/components/incidents/LiveFeed';
import { DemoPresetPanel } from '@/components/demo/DemoPresetPanel';
import { Card, CardContent } from '@/components/ui/card';
import { AlertTriangle, ShieldAlert, CheckCircle, Users, Hop, Layers } from 'lucide-react';
import type { Incident } from '@/types/api';

export const DashboardPage: React.FC = () => {
  const { incidents } = useIncidents();
  const { responders } = useResponders();
  const { liveIncidents, isConnected } = useWebSocket();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);

  const activeCount = incidents.filter((i) => i.status === 'ACTIVE').length;
  const dispatchedCount = incidents.filter((i) => i.status === 'DISPATCHED').length;
  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;
  const responderCount = responders.length;

  return (
    <div className="space-y-6">
      {/* Header Info */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-100">GIS Command Dispatch Center</h2>
        <p className="text-sm text-slate-400">Real-time emergency incident monitoring & spatial P2P mesh telemetry</p>
      </div>

      {/* Demo Scenario Presets Toolbar */}
      <DemoPresetPanel />

      {/* Overview Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-red-900/50 bg-red-950/20">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-red-400 font-semibold uppercase tracking-wider">Active SOS</p>
              <p className="text-3xl font-bold text-slate-100 mt-1">{activeCount}</p>
            </div>
            <div className="p-3 bg-red-500/10 rounded-lg border border-red-500/20">
              <AlertTriangle className="h-6 w-6 text-red-400" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-amber-900/50 bg-amber-950/20">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-amber-400 font-semibold uppercase tracking-wider">Dispatched Units</p>
              <p className="text-3xl font-bold text-slate-100 mt-1">{dispatchedCount}</p>
            </div>
            <div className="p-3 bg-amber-500/10 rounded-lg border border-amber-500/20">
              <ShieldAlert className="h-6 w-6 text-amber-400" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-emerald-900/50 bg-emerald-950/20">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-emerald-400 font-semibold uppercase tracking-wider">Resolved</p>
              <p className="text-3xl font-bold text-slate-100 mt-1">{resolvedCount}</p>
            </div>
            <div className="p-3 bg-emerald-500/10 rounded-lg border border-emerald-500/20">
              <CheckCircle className="h-6 w-6 text-emerald-400" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-sky-900/50 bg-sky-950/20">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs text-sky-400 font-semibold uppercase tracking-wider">Field Responders</p>
              <p className="text-3xl font-bold text-slate-100 mt-1">{responderCount}</p>
            </div>
            <div className="p-3 bg-sky-500/10 rounded-lg border border-sky-500/20">
              <Users className="h-6 w-6 text-sky-400" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Grid: Interactive Map + Live Telemetry Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[540px]">
        <div className="lg:col-span-2 h-full flex flex-col space-y-2">
          {selectedIncident && (
            <div className="px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs font-mono flex items-center justify-between text-slate-300">
              <div className="flex items-center gap-3">
                <span className="text-emerald-400 font-bold">INS SPECTING MESH VECTOR:</span>
                <span>Incident #{selectedIncident.id}</span>
                <span className="flex items-center gap-1 text-sky-400">
                  <Hop className="h-3.5 w-3.5" /> Hops: {selectedIncident.hop_count}
                </span>
                <span className="flex items-center gap-1 text-amber-400">
                  <Layers className="h-3.5 w-3.5" /> Channel: {selectedIncident.channel}
                </span>
              </div>
              <button
                onClick={() => setSelectedIncident(null)}
                className="text-slate-400 hover:text-slate-100 text-xs underline"
              >
                Clear Selection
              </button>
            </div>
          )}

          <div className="flex-1">
            <IncidentMap
              incidents={incidents}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full rounded-xl overflow-hidden border border-slate-800"
            />
          </div>
        </div>

        <div className="h-full">
          <LiveFeed incidents={liveIncidents} isConnected={isConnected} />
        </div>
      </div>
    </div>
  );
};
