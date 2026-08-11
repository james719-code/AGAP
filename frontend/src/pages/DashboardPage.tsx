import React, { useEffect, useMemo, useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { useWebSocket } from '@/context/WebSocketContext';
import { IncidentMap } from '@/components/map/IncidentMap';
import { LiveFeed } from '@/components/incidents/LiveFeed';
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
  X,
} from 'lucide-react';
import type { Incident } from '@/types/api';
import { deduplicateIncidents } from '@/lib/incidentIdentity';

const ACTIVE_DISPATCH_STATUSES = new Set(['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE']);

export const DashboardPage: React.FC = () => {
  const { incidents, loading, error, refetch: refetchIncidents } = useIncidents(
    {},
    { fetchAll: true, pollingIntervalMs: 10_000 },
  );
  const { responders } = useResponders(undefined, 30_000);
  const { liveIncidents, isConnected } = useWebSocket();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [activeDispatchCount, setActiveDispatchCount] = useState<number>(0);
  const [dispatchCountLoading, setDispatchCountLoading] = useState(false);

  const activeCount = incidents.filter((i) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(i.status)).length;
  const resolvedCount = incidents.filter((i) => i.status === 'RESOLVED').length;

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      const candidateIncidentIds = incidents
        .filter((i) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(i.status))
        .map((i) => i.id);
      if (candidateIncidentIds.length === 0) {
        setActiveDispatchCount(0);
        return;
      }
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
    };
    run();
    return () => { cancelled = true; };
  }, [incidents]);

  const activeResponderCount = responders.filter((r) => isResponderActive(r)).length;
  const responderCount = responders.length;

  const feedIncidents = useMemo(() => {
    return deduplicateIncidents([...liveIncidents, ...incidents])
      .sort((a, b) => new Date(b.received_at || b.event_timestamp || b.created_at).getTime()
        - new Date(a.received_at || a.event_timestamp || a.created_at).getTime())
      .slice(0, 50);
  }, [incidents, liveIncidents]);

  useEffect(() => {
    if (!selectedIncident) return;
    const current = incidents.find((incident) => incident.id === selectedIncident.id);
    if (current && (current.status !== selectedIncident.status || current.updated_at !== selectedIncident.updated_at)) {
      setSelectedIncident(current);
    }
  }, [incidents, selectedIncident]);

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <span className="text-xs font-semibold tracking-[0.14em] uppercase text-[#D15033]">
            Command Dispatch
          </span>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white">
            GIS Overview
          </h2>
          <p className="mt-1 text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
            Real-time emergency incident telemetry
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <Button
            onClick={() => refetchIncidents()}
            variant="outline"
            size="sm"
            className="h-9 px-3 gap-2 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
            disabled={loading}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh Telemetry</span>
          </Button>
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="px-4 py-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-[2px] text-xs text-red-700 dark:text-red-300 font-medium flex items-center justify-between">
          <span>Failed to load incidents: {error}</span>
          <button onClick={() => refetchIncidents()} className="underline underline-offset-2 shrink-0 ml-3">
            Retry
          </button>
        </div>
      )}

      {loading && incidents.length === 0 && !error && (
        <div className="px-4 py-3 bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
          Loading telemetry...
        </div>
      )}

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active SOS */}
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex items-center justify-between transition-all hover:-translate-y-1 hover:shadow-lg">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[#D15033] flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-[#D15033] animate-pulse" /> Active Distress SOS
            </p>
            <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white tabular-nums">
              {activeCount}
            </p>
            <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">Requires immediate triage</p>
          </div>
          <div className="h-12 w-12 rounded-[2px] bg-[#D15033]/10 dark:bg-[#D15033]/15 text-[#D15033] flex items-center justify-center shrink-0">
            <AlertTriangle className="h-6 w-6" />
          </div>
        </div>

        {/* Dispatched Units */}
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex items-center justify-between transition-all hover:-translate-y-1 hover:shadow-lg">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[#1F91A8] flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-[#1F91A8]" /> Dispatched Units
            </p>
            <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white tabular-nums">
              {dispatchCountLoading ? '—' : activeDispatchCount}
            </p>
            <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">Active responder unit assignments</p>
          </div>
          <div className="h-12 w-12 rounded-[2px] bg-[#1F91A8]/10 dark:bg-[#1F91A8]/15 text-[#1F91A8] flex items-center justify-center shrink-0">
            <ShieldAlert className="h-6 w-6" />
          </div>
        </div>

        {/* Resolved Incidents */}
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex items-center justify-between transition-all hover:-translate-y-1 hover:shadow-lg">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-emerald-500" /> Resolved Incidents
            </p>
            <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white tabular-nums">
              {resolvedCount}
            </p>
            <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">Successfully closed</p>
          </div>
          <div className="h-12 w-12 rounded-[2px] bg-emerald-500/10 dark:bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
            <CheckCircle className="h-6 w-6" />
          </div>
        </div>

        {/* Active Responder Fleet */}
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-5 flex items-center justify-between transition-all hover:-translate-y-1 hover:shadow-lg">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[#F2A540] flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-[#F2A540]" /> Active Responder Fleet
            </p>
            <p className="mt-1.5 text-3xl font-extrabold tracking-tight text-[#0A0A0A] dark:text-white tabular-nums">
              {activeResponderCount}
            </p>
            <p className="mt-1 text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">On duty of {responderCount} total units</p>
          </div>
          <div className="h-12 w-12 rounded-[2px] bg-[#F2A540]/10 dark:bg-[#F2A540]/15 text-[#F2A540] flex items-center justify-center shrink-0">
            <Users className="h-6 w-6" />
          </div>
        </div>
      </div>

      {/* Main Grid: Map + Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Map Column */}
        <div className="lg:col-span-2 space-y-2.5">
          {selectedIncident && (
            <div className="px-4 py-3 bg-white dark:bg-[#161A1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] text-xs flex items-center justify-between text-[#0A0A0A] dark:text-white shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                <span className="text-[#1F91A8] font-bold flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-ping" /> INSPECTING VECTOR:
                </span>
                <span className="font-semibold">Incident #{selectedIncident.id}</span>
                <span className="flex items-center gap-1 text-[#5C5A56] dark:text-[#B9B6B0]">
                  <Hop className="h-3.5 w-3.5" /> Hops: {selectedIncident.hop_count}
                </span>
                <span className="flex items-center gap-1 text-[#5C5A56] dark:text-[#B9B6B0]">
                  <Layers className="h-3.5 w-3.5" /> Channel: {selectedIncident.channel}
                </span>
              </div>
              <button
                onClick={() => setSelectedIncident(null)}
                className="flex items-center gap-1 text-[#5C5A56] dark:text-[#B9B6B0] hover:text-[#0A0A0A] dark:hover:text-white text-xs underline underline-offset-2 shrink-0"
              >
                <X className="h-3.5 w-3.5" /> Clear
              </button>
            </div>
          )}

          <div className="rounded-[2px] overflow-hidden border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] h-[420px] lg:h-[560px]">
            <IncidentMap
              incidents={incidents}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(inc) => setSelectedIncident(inc)}
              className="h-full w-full"
            />
          </div>
        </div>

        {/* Feed Column */}
        <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-4 flex flex-col space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-[#E7E3DC] dark:border-[#26292C]">
            <div className="flex items-center gap-2">
              <Radio className={`h-4 w-4 ${isConnected ? 'text-[#1F91A8]' : 'text-[#5C5A56] dark:text-[#9C9994]'}`} />
              <h3 className="text-sm font-bold text-[#0A0A0A] dark:text-white">Mesh Telemetry Live Feed</h3>
              {isConnected && <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-pulse" />}
            </div>
            <span className="text-[10px] font-medium text-[#5C5A56] dark:text-[#9C9994] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] px-2 py-0.5 rounded-[2px]">
              {feedIncidents.length} EVENTS
            </span>
          </div>

          <ScrollArea className="flex-1 pr-1 max-h-[480px] lg:max-h-none">
            <LiveFeed incidents={feedIncidents} isConnected={isConnected} />
          </ScrollArea>
        </div>
      </div>
    </div>
  );
};