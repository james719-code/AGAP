import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, CheckCircle, MapPin, Radio, RefreshCw, ShieldAlert, Users, X } from 'lucide-react';
import { useIncidents } from '@/hooks/useIncidents';
import { useTelemetryReports } from '@/hooks/useTelemetryReports';
import { useWebSocket } from '@/context/WebSocketContext';
import { IncidentMap, MapLegend } from '@/components/map/IncidentMap';
import { LiveFeed } from '@/components/incidents/LiveFeed';
import { Button, buttonVariants } from '@/components/ui/button';
import { apiService } from '@/services/api';
import { hostedRefreshMs, usesHostedApi } from '@/services/backend';
import { deduplicateIncidents } from '@/lib/incidentIdentity';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { cn } from '@/lib/utils';
import { incidentQueueRank, NEEDS_ASSIGNMENT_STATUSES, incidentStatusLabel } from '@/lib/incidentPresentation';

const ACTIVE_DISPATCH_STATUSES = new Set(['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE']);
const CLOSED_STATUSES = new Set(['RESOLVED', 'CANCELLED', 'FALSE_ALARM']);

export const DashboardPage: React.FC = () => {
  const { incidents, loading, error, refetch: refetchIncidents } = useIncidents(
    { limit: 100, offset: 0 },
    { pollingIntervalMs: usesHostedApi ? hostedRefreshMs : 60_000 },
  );
  const { report, loading: reportLoading, error: reportError, refetch: refetchReport } = useTelemetryReports();
  const { liveIncidents, isConnected } = useWebSocket();
  const [selectedIncidentId, setSelectedIncidentId] = useState<number>();
  const [activeDispatchCount, setActiveDispatchCount] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    const candidateIds = incidents
      .filter((incident) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(incident.status))
      .map((incident) => incident.id);

    async function loadDispatchCount() {
      setActiveDispatchCount(null);
      if (!candidateIds.length) {
        setActiveDispatchCount(0);
        return;
      }

      try {
        const byIncident = await apiService.getIncidentDispatchesBulk(candidateIds);
        if (cancelled) return;
        const count = Object.values(byIncident).reduce(
          (total, dispatches) => total + (dispatches ?? []).filter((dispatch) => ACTIVE_DISPATCH_STATUSES.has(dispatch.status)).length,
          0,
        );
        setActiveDispatchCount(count);
      } catch {
        if (!cancelled) setActiveDispatchCount(null);
      }
    }

    queueMicrotask(() => { if (!cancelled) void loadDispatchCount(); });
    return () => { cancelled = true; };
  }, [incidents]);

  const mapIncidents = useMemo(() => deduplicateIncidents([...incidents, ...liveIncidents])
    .sort((a, b) => new Date(b.received_at || b.event_timestamp || b.created_at).getTime()
      - new Date(a.received_at || a.event_timestamp || a.created_at).getTime()), [incidents, liveIncidents]);
  const feedIncidents = useMemo(() => [...mapIncidents].sort((a, b) =>
    incidentQueueRank(a.status) - incidentQueueRank(b.status)), [mapIncidents]);
  const awaitingDispatch = mapIncidents.filter((incident) => NEEDS_ASSIGNMENT_STATUSES.has(incident.status)).length;
  const selectedIncident = mapIncidents.find((incident) => incident.id === selectedIncidentId);

  const hasIncidentData = incidents.length > 0 || (!loading && !error);
  const metrics = [
    {
      label: 'Active incidents',
      value: report?.active_incidents ?? (hasIncidentData ? incidents.filter((incident) => !CLOSED_STATUSES.has(incident.status)).length : null),
      note: report ? 'Open across the network' : 'From the latest incident records',
      icon: AlertTriangle,
      to: '/dashboard/incidents',
      urgent: true,
    },
    {
      label: 'Active dispatches',
      value: hasIncidentData ? activeDispatchCount : null,
      note: 'Assignments on recent incidents',
      icon: ShieldAlert,
      to: '/dashboard/team',
    },
    {
      label: 'Resolved incidents',
      value: report?.resolved_incidents ?? (hasIncidentData ? incidents.filter((incident) => incident.status === 'RESOLVED').length : null),
      note: report ? 'Closed after response' : 'From the latest incident records',
      icon: CheckCircle,
      to: '/dashboard/archive',
    },
    {
      label: 'Responders on duty',
      value: report?.active_responders,
      note: report ? 'Of ' + report.total_responders.toLocaleString() + ' registered units' : 'Fleet totals unavailable',
      icon: Users,
      to: '/dashboard/team',
    },
  ];

  function refresh() {
    void refetchIncidents();
    void refetchReport();
  }

  return (
    <div className="page-stack">
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Command center / Dispatch</span>
          <h1 className="page-title">Operations overview</h1>
          <p className="page-description">Monitor incidents, responder assignments, and activity across the AGAP network.</p>
        </div>
        <div className="page-actions">
          <Button type="button" onClick={refresh} variant="outline" disabled={loading || reportLoading} className="h-control gap-2 bg-card">
            <RefreshCw className={cn('size-4', (loading || reportLoading) && 'motion-safe:animate-spin')} />
            Refresh data
          </Button>
          <Link to="/dashboard/map" className={buttonVariants({ className: 'h-control gap-2' })}>
            <MapPin className="size-4" /> Open live map
          </Link>
        </div>
      </div>

      {(error || reportError) && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{error ? 'Could not load incidents. ' + error : 'Network totals are unavailable. ' + reportError}</span>
          <Button type="button" variant="ghost" size="sm" onClick={refresh}>Retry</Button>
        </div>
      )}

      <section aria-label="Operational totals" className="surface-panel grid grid-cols-2 xl:grid-cols-4" aria-busy={loading || reportLoading}>
        {metrics.map(({ label, value, note, icon: Icon, to, urgent }) => (
          <Link key={label} to={to} className="overview-metric group">
            <div className="flex items-start justify-between gap-2">
              <span className={cn('grid size-8 shrink-0 place-items-center rounded-lg', urgent && value != null && value > 0 ? 'tone-danger' : 'bg-muted text-muted-foreground')}>
                <Icon className="size-4" aria-hidden="true" />
              </span>
              <ArrowRight className="size-4 text-muted-foreground group-hover:text-primary" aria-hidden="true" />
            </div>
            <p className="mt-2 text-sm font-semibold text-foreground">{label}</p>
            <p className="mt-1.5 text-[1.75rem] font-bold leading-none tracking-tight tabular-nums sm:text-[2rem]">{value?.toLocaleString() ?? '—'}</p>
            <p className="mt-2 text-xs text-pretty text-muted-foreground">{note}</p>
          </Link>
        ))}
      </section>

      <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <section className="surface-panel flex min-w-0 flex-col" aria-labelledby="overview-feed-title">
          <div className="surface-header flex-wrap">
            <div>
              <h2 id="overview-feed-title" className="text-lg font-bold">Incident queue</h2>
              <p className="mt-1 text-xs text-muted-foreground">Awaiting dispatch first, then response activity</p>
            </div>
            <span className={cn('status-pill', error ? 'tone-warning' : awaitingDispatch > 0 ? 'tone-danger' : '')}>
              {error ? 'Data unavailable' : loading && !mapIncidents.length ? 'Loading' : `${awaitingDispatch} awaiting dispatch`}
            </span>
          </div>
          <LiveFeed
            incidents={feedIncidents}
            loading={loading}
            error={Boolean(error)}
            selectedIncidentId={selectedIncident?.id}
            onSelectIncident={setSelectedIncidentId}
          />
        </section>
        <section className="surface-panel flex min-w-0 flex-col" aria-labelledby="overview-map-title">
          <div className="surface-header">
            <div>
              <h2 id="overview-map-title" className="text-lg font-bold">Incident map</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Select a marker or an incident in the feed.</p>
            </div>
            <Link to="/dashboard/map" className="inline-flex shrink-0 items-center gap-1.5 text-sm font-semibold text-primary hover:underline">
              Full map <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </div>
          <div className="isolate h-80 min-h-80 flex-1 xl:h-[27rem]">
            <IncidentMap
              incidents={mapIncidents}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(incident) => setSelectedIncidentId(incident.id)}
              className="h-full w-full"
            />
          </div>
          {selectedIncident && (
            <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-2.5 text-sm">
              <div className="min-w-0">
                <Link to={'/dashboard/incidents?incidentId=' + selectedIncident.id} className="font-semibold text-primary hover:underline">
                  {incidentStatusLabel(selectedIncident.incident_type)} · #{selectedIncident.id}
                </Link>
                <p className="truncate text-xs text-muted-foreground">
                  {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
                </p>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={() => setSelectedIncidentId(undefined)} aria-label="Clear selected incident">
                <X className="size-4" />
              </Button>
            </div>
          )}
          <div className="border-t border-border px-4 py-3"><MapLegend /></div>
          <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border px-4 py-3 text-xs text-muted-foreground">
            <span className="tabular-nums">{mapIncidents.length} incident records loaded</span>
            <span className={cn('status-pill', error ? 'tone-warning' : isConnected ? 'tone-success' : '')}>
              <Radio className="size-3.5" aria-hidden="true" />
              {error ? 'Data unavailable' : usesHostedApi ? 'Auto-refresh' : isConnected ? 'Live feed' : 'Disconnected'}
            </span>
          </div>
        </section>
      </div>
    </div>
  );
};
