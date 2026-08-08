import React, { useEffect, useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useWebSocket } from '@/context/WebSocketContext';
import { IncidentMap } from '@/components/map/IncidentMap';
import { LiveFeed } from '@/components/incidents/LiveFeed';
import { DemoPresetPanel } from '@/components/demo/DemoPresetPanel';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { apiService } from '@/services/api';
import { isResponderActive } from '@/lib/responderStatus';
import {
  AlertTriangle,
  ShieldAlert,
  CheckCircle,
  Users,
  Hop,
  Layers,
  Radio,
  RefreshCw,
  X
} from 'lucide-react';
import type { Incident } from '@/types/api';

// Non-terminal dispatch statuses — a dispatch record still counts as an
// actively deployed unit until it's COMPLETED or CANCELLED.
const ACTIVE_DISPATCH_STATUSES = new Set(['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE']);

export const DashboardPage: React.FC = () => {
  // fetchAll: the stat cards below (Active/Dispatched/Resolved) count across
  // the whole incident set — computing them off a single page understated
  // these numbers as soon as incident volume passed the default page size.
  const { incidents, loading, error, refetch: refetchIncidents } = useIncidents({}, { fetchAll: true });
  const { responders } = useResponders();
  const { liveIncidents, isConnected } = useWebSocket();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [activeDispatchCount, setActiveDispatchCount] = useState<number>(0);
  const [dispatchCountLoading, setDispatchCountLoading] = useState(false);

  // TODO: verify these status strings against the real IncidentStatus enum in domain/telemetry.go
  const activeCount = incidents.filter((i) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(i.status)).length;
  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;

  // "Dispatched Units" must count actual dispatch records, not incidents —
  // one incident can have several units assigned to it, so counting
  // incidents in a dispatched-adjacent status undercounts real unit
  // deployments. Pull the real dispatch records for incidents that could
  // plausibly have an active one, via the existing bulk endpoint.
  useEffect(() => {
    const candidateIncidentIds = incidents
      .filter((i) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(i.status))
      .map((i) => i.id);

    if (candidateIncidentIds.length === 0) {
      setActiveDispatchCount(0);
      return;
    }

    let cancelled = false;
    setDispatchCountLoading(true);
    apiService
      .getIncidentDispatchesBulk(candidateIncidentIds)
      .then((byIncident) => {
        if (cancelled) return;
        let count = 0;
        for (const dispatches of Object.values(byIncident)) {
          count += (dispatches ?? []).filter((d) => ACTIVE_DISPATCH_STATUSES.has(d.status)).length;
        }
        setActiveDispatchCount(count);
      })
      .catch(() => {
        if (!cancelled) setActiveDispatchCount(0);
      })
      .finally(() => {
        if (!cancelled) setDispatchCountLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [incidents]);

  const activeResponderCount = responders.filter((r) => isResponderActive(r)).length;
  const responderCount = responders.length;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">
            Command Dispatch
          </p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">
            GIS Overview
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Real-time emergency incident telemetry &amp; spatial P2P mesh monitoring
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <Badge variant="outline" className="border-primary/30 bg-primary/5 text-primary font-semibold">
            LEGAZPI CITY · REGION V
          </Badge>
          <Button
            onClick={() => refetchIncidents()}
            variant="outline"
            size="sm"
            className="h-9 px-3 gap-2"
            disabled={loading}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh Telemetry</span>
          </Button>
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="px-4 py-3 bg-destructive/10 border border-destructive/30 rounded-lg text-xs text-destructive font-medium flex items-center justify-between">
          <span>Failed to load incidents: {error}</span>
          <button onClick={() => refetchIncidents()} className="underline underline-offset-2 shrink-0 ml-3">
            Retry
          </button>
        </div>
      )}

      {/* Initial loading state */}
      {loading && incidents.length === 0 && !error && (
        <div className="px-4 py-3 bg-muted/30 border border-border rounded-lg text-xs text-muted-foreground">
          Loading telemetry...
        </div>
      )}

      {/* Demo Scenario Presets Toolbar */}
      <DemoPresetPanel />

      {/* Overview Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active SOS Card */}
        <Card className="overflow-hidden">
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-destructive flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-destructive animate-pulse" /> Active Distress SOS
              </p>
              <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-foreground tabular-nums">{activeCount}</p>
              <p className="mt-1 text-[11px] text-muted-foreground">Requires immediate triage</p>
            </div>
            <div className="h-12 w-12 rounded-lg bg-destructive/10 text-destructive flex items-center justify-center shrink-0">
              <AlertTriangle className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        {/* Dispatched Units Card */}
        <Card>
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-[#1F91A8] flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-[#1F91A8]" /> Dispatched Units
              </p>
              <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-foreground tabular-nums">
                {dispatchCountLoading ? '—' : activeDispatchCount}
              </p>
              <p className="mt-1 text-[11px] text-muted-foreground">Active responder unit assignments</p>
            </div>
            <div className="h-12 w-12 rounded-lg bg-[#1F91A8]/10 text-[#1F91A8] flex items-center justify-center shrink-0">
              <ShieldAlert className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        {/* Resolved Incidents Card */}
        <Card>
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-emerald-500" /> Resolved Incidents
              </p>
              <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-foreground tabular-nums">{resolvedCount}</p>
              <p className="mt-1 text-[11px] text-muted-foreground">Successfully closed</p>
            </div>
            <div className="h-12 w-12 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
              <CheckCircle className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        {/* Responder Fleet Card */}
        <Card>
          <CardContent className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-[#F2A540] flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-[#F2A540]" /> Active Responder Fleet
              </p>
              <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-foreground tabular-nums">{activeResponderCount}</p>
              <p className="mt-1 text-[11px] text-muted-foreground">On duty of {responderCount} total units</p>
            </div>
            <div className="h-12 w-12 rounded-lg bg-[#F2A540]/10 text-[#F2A540] flex items-center justify-center shrink-0">
              <Users className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Grid: Interactive Map + Live Telemetry Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Interactive Map Column */}
        <div className="lg:col-span-2 space-y-2.5">
          {selectedIncident && (
            <div className="px-4 py-3 bg-card border border-border rounded-lg text-xs flex items-center justify-between text-foreground shadow-sm">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                <span className="text-[#1F91A8] font-bold flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-ping" /> INSPECTING VECTOR:
                </span>
                <span className="font-semibold">Incident #{selectedIncident.id}</span>
                <span className="flex items-center gap-1 text-muted-foreground">
                  <Hop className="h-3.5 w-3.5" /> Hops: {selectedIncident.hop_count}
                </span>
                <span className="flex items-center gap-1 text-muted-foreground">
                  <Layers className="h-3.5 w-3.5" /> Channel: {selectedIncident.channel}
                </span>
              </div>
              <button
                onClick={() => setSelectedIncident(null)}
                className="flex items-center gap-1 text-muted-foreground hover:text-foreground text-xs underline underline-offset-2 shrink-0"
              >
                <X className="h-3.5 w-3.5" /> Clear
              </button>
            </div>
          )}

          <div className="rounded-lg overflow-hidden border border-border shadow-sm relative bg-muted/30 h-[420px] lg:h-[560px]">
            <IncidentMap
              incidents={incidents}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full"
            />
          </div>
        </div>

        {/* Telemetry Stream Column */}
        <div className="bg-card border border-border rounded-lg p-4 flex flex-col space-y-3 shadow-sm">
          <div className="flex items-center justify-between pb-2 border-b border-border">
            <div className="flex items-center gap-2">
              <Radio className={`h-4 w-4 ${isConnected ? 'text-[#1F91A8]' : 'text-muted-foreground'}`} />
              <h3 className="text-sm font-bold text-foreground">Mesh Telemetry Live Feed</h3>
              {isConnected && <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-pulse" />}
            </div>
            <Badge variant="outline" className="text-[10px] font-medium">
              {liveIncidents.length} PACKETS
            </Badge>
          </div>

          <ScrollArea className="flex-1 pr-1 max-h-[480px] lg:max-h-none">
            <LiveFeed incidents={liveIncidents} isConnected={isConnected} />
          </ScrollArea>
        </div>
      </div>
    </div>
  );
};