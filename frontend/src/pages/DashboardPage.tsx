import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Bluetooth, CheckCircle2, MapPin, RefreshCw, Siren, Wifi, X } from 'lucide-react';
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
import type { IncidentType } from '@/types/api';

const ACTIVE_DISPATCH_STATUSES = new Set(['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE']);
const CLOSED_STATUSES = new Set(['RESOLVED', 'CANCELLED', 'FALSE_ALARM']);

// Matches the incident marker colors on the map.
const TYPE_META: Record<IncidentType, { label: string; color: string }> = {
  MEDICAL: { label: 'Medical', color: '#176e7e' },
  FIRE: { label: 'Fire', color: '#ac4c14' },
  ACCIDENT: { label: 'Accident', color: '#a86f00' },
  CRIME: { label: 'Crime', color: '#a82e20' },
  DISASTER: { label: 'Disaster', color: '#7643a5' },
};

function relativeMinutes(iso?: string) {
  if (!iso) return null;
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins} min`;
  const hours = Math.floor(mins / 60);
  return hours < 48 ? `${hours} h` : `${Math.floor(hours / 24)} d`;
}

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
  const openIncidents = useMemo(() => mapIncidents.filter((incident) => !CLOSED_STATUSES.has(incident.status)), [mapIncidents]);
  const awaiting = mapIncidents.filter((incident) => NEEDS_ASSIGNMENT_STATUSES.has(incident.status));
  const awaitingDispatch = awaiting.length;
  const oldestAwaiting = awaiting.at(-1);
  const selectedIncident = mapIncidents.find((incident) => incident.id === selectedIncidentId);

  const typeBreakdown = useMemo(() => {
    const counts = new Map<IncidentType, number>();
    for (const incident of openIncidents) counts.set(incident.incident_type, (counts.get(incident.incident_type) ?? 0) + 1);
    const max = Math.max(1, ...counts.values());
    return (Object.keys(TYPE_META) as IncidentType[])
      .map((type) => ({ type, count: counts.get(type) ?? 0, ratio: (counts.get(type) ?? 0) / max }))
      .sort((a, b) => b.count - a.count);
  }, [openIncidents]);
  const meshCount = openIncidents.filter((incident) => incident.channel === 'BLE_MESH').length;
  const onlineCount = openIncidents.length - meshCount;
  const meshShare = openIncidents.length ? Math.round((meshCount / openIncidents.length) * 100) : 0;

  const hasIncidentData = incidents.length > 0 || (!loading && !error);
  const busy = loading || reportLoading;
  const liveOk = !error && (isConnected || usesHostedApi);
  const metrics = [
    {
      label: 'Active incidents',
      value: report?.active_incidents ?? (hasIncidentData ? openIncidents.length : null),
      note: report ? 'Open across the network' : 'From latest records',
      to: '/dashboard/incidents',
      tone: 'critical',
    },
    {
      label: 'Units dispatched',
      value: hasIncidentData ? activeDispatchCount : null,
      note: 'Assignments in progress',
      to: '/dashboard/team',
      tone: 'moderate',
    },
    {
      label: 'Responders on duty',
      value: report?.active_responders,
      note: report ? `of ${report.total_responders.toLocaleString()} registered` : 'Fleet totals unavailable',
      to: '/dashboard/team',
      tone: 'info',
    },
    {
      label: 'Resolved',
      value: report?.resolved_incidents ?? (hasIncidentData ? incidents.filter((incident) => incident.status === 'RESOLVED').length : null),
      note: report ? 'Closed after response' : 'From latest records',
      to: '/dashboard/archive',
      tone: 'resolved',
    },
  ];

  function refresh() {
    void refetchIncidents();
    void refetchReport();
  }

  return (
    <div className="page-stack">
      <div className="page-header border-b-0 pb-0">
        <div>
          <span className="page-eyebrow">Dispatch</span>
          <h1 className="page-title">Operations overview</h1>
        </div>
        <div className="page-actions">
          <Button type="button" onClick={refresh} variant="outline" disabled={busy} className="h-control gap-2">
            <RefreshCw className={cn('size-4', busy && 'motion-safe:animate-spin')} />
            Refresh
          </Button>
          <Link to="/dashboard/map" className={buttonVariants({ className: 'h-control gap-2' })}>
            <MapPin className="size-4" /> Live map
          </Link>
        </div>
      </div>

      {(error || reportError) && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <span>{error ? 'Could not load incidents. ' + error : 'Network totals are unavailable. ' + reportError}</span>
          <Button type="button" variant="ghost" size="sm" onClick={refresh}>Retry</Button>
        </div>
      )}

      {/* Situation banner: the one thing a dispatcher must act on. */}
      {!error && (
        <section
          aria-live="polite"
          aria-label="Dispatch status"
          className={cn(
            'flex flex-col gap-4 rounded-2xl border p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6',
            awaitingDispatch > 0 ? 'situation-banner-alert' : 'situation-banner-clear',
          )}
        >
          <div className="flex items-center gap-4">
            <span className="situation-icon grid size-12 shrink-0 place-items-center rounded-2xl">
              {awaitingDispatch > 0 ? <Siren className="size-6" aria-hidden="true" /> : <CheckCircle2 className="size-6" aria-hidden="true" />}
            </span>
            <div>
              <p className="flex items-center gap-2.5 text-lg font-bold sm:text-xl">
                {awaitingDispatch > 0 && <span className="live-dot" aria-hidden="true" />}
                {loading && !mapIncidents.length
                  ? 'Loading incident queue…'
                  : awaitingDispatch > 0
                    ? `${awaitingDispatch} ${awaitingDispatch === 1 ? 'incident needs' : 'incidents need'} a responder`
                    : 'Every open incident has a response'}
              </p>
              <p className="mt-0.5 text-sm text-muted-foreground">
                {awaitingDispatch > 0 && oldestAwaiting
                  ? `Oldest waiting ${relativeMinutes(oldestAwaiting.received_at || oldestAwaiting.created_at)} · incident #${oldestAwaiting.id}`
                  : 'New distress alerts will appear here and sound the siren if enabled.'}
              </p>
            </div>
          </div>
          {awaitingDispatch > 0 && (
            <Link to="/dashboard/incidents" className="situation-cta inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl px-5 text-sm font-semibold">
              Triage queue <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          )}
        </section>
      )}

      {/* KPI strip */}
      <section aria-label="Operational totals" aria-busy={busy} className="surface-panel grid grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ label, value, note, to, tone }, index) => (
          <Link
            key={label}
            to={to}
            data-tone={tone}
            className={cn('kpi-cell group', index % 2 === 1 && 'border-l', index >= 2 && 'border-t xl:border-t-0', index === 2 && 'xl:border-l')}
          >
            <p className="flex items-center gap-2 text-[0.8125rem] font-semibold text-muted-foreground">
              <span className="kpi-swatch" aria-hidden="true" />{label}
            </p>
            <p className="metric-figure mt-3 text-[2.25rem] sm:text-[2.75rem]">{value?.toLocaleString() ?? (busy ? '…' : '—')}</p>
            <p className="mt-2 flex items-center justify-between gap-2 text-xs text-muted-foreground">
              {note}
              <ArrowRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true" />
            </p>
          </Link>
        ))}
      </section>

      {/* Map-first dispatch view */}
      <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1.65fr)_minmax(22rem,1fr)]">
        <section className="surface-panel flex min-w-0 flex-col" aria-labelledby="overview-map-title">
          <div className="surface-header">
            <div className="flex items-center gap-3">
              <h2 id="overview-map-title" className="text-base font-bold">Incident map</h2>
              <span className={cn('status-pill gap-1.5 before:hidden', error ? 'tone-warning' : liveOk ? 'tone-success' : '')}>
                <span className={cn('live-dot', error ? 'live-dot-warn' : liveOk ? 'live-dot-ok' : 'live-dot-idle')} aria-hidden="true" />
                {error ? 'Data unavailable' : usesHostedApi ? 'Auto-refresh' : isConnected ? 'Live' : 'Disconnected'}
              </span>
            </div>
            <Link to="/dashboard/map" className="inline-flex shrink-0 items-center gap-1.5 text-sm font-semibold text-primary hover:underline">
              Full map <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </div>
          <div className="relative isolate h-96 min-h-96 flex-1 xl:h-[34rem]">
            <IncidentMap
              incidents={mapIncidents}
              scrollWheelZoom={false}
              selectedIncidentId={selectedIncident?.id}
              onSelectIncident={(incident) => setSelectedIncidentId(incident.id)}
              className="h-full w-full"
            />
            {selectedIncident && (
              <div className="absolute inset-x-3 bottom-3 z-[500] flex items-center justify-between gap-3 rounded-xl border border-border bg-card/95 px-4 py-3 text-sm shadow-lg backdrop-blur sm:inset-x-auto sm:left-3 sm:max-w-sm">
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
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3">
            <MapLegend />
            <span className="font-mono text-xs text-muted-foreground tabular-nums">{mapIncidents.length} records</span>
          </div>
        </section>

        <section className="surface-panel flex min-w-0 flex-col" aria-labelledby="overview-feed-title">
          <div className="surface-header">
            <div>
              <h2 id="overview-feed-title" className="text-base font-bold">Incident queue</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Unassigned first, then active responses</p>
            </div>
            <span className={cn('status-pill font-mono', awaitingDispatch > 0 ? 'tone-danger' : '')} aria-label={`${awaitingDispatch} awaiting dispatch`}>{awaitingDispatch} waiting</span>
          </div>
          <LiveFeed
            incidents={feedIncidents}
            loading={loading}
            error={Boolean(error)}
            selectedIncidentId={selectedIncident?.id}
            onSelectIncident={setSelectedIncidentId}
          />
        </section>
      </div>

      {/* Situational breakdown */}
      <div className="grid gap-5 lg:grid-cols-2">
        <section className="surface-panel p-5 sm:p-6" aria-labelledby="type-breakdown-title">
          <div className="flex items-baseline justify-between gap-3">
            <h2 id="type-breakdown-title" className="text-base font-bold">Open incidents by type</h2>
            <span className="font-mono text-xs text-muted-foreground">{openIncidents.length} open</span>
          </div>
          <ul className="mt-5 space-y-3.5">
            {typeBreakdown.map(({ type, count, ratio }) => (
              <li key={type} className="grid grid-cols-[6rem_minmax(0,1fr)_2.5rem] items-center gap-3 text-sm">
                <span className="font-medium">{TYPE_META[type].label}</span>
                <span className="h-2.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                  <span className="block h-full rounded-full transition-[width] duration-500" style={{ width: `${Math.max(ratio * 100, count ? 4 : 0)}%`, background: TYPE_META[type].color }} />
                </span>
                <span className="text-right font-mono font-semibold tabular-nums">{count}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="surface-panel p-5 sm:p-6" aria-labelledby="channel-title">
          <div className="flex items-baseline justify-between gap-3">
            <h2 id="channel-title" className="text-base font-bold">How alerts arrived</h2>
            <span className="font-mono text-xs text-muted-foreground">open incidents</span>
          </div>
          <div className="mt-5 flex h-3 overflow-hidden rounded-full bg-muted" aria-hidden="true">
            <span className="h-full bg-[var(--sev-info)]" style={{ width: `${openIncidents.length ? 100 - meshShare : 0}%` }} />
            <span className="h-full bg-[var(--color-brand-amber)]" style={{ width: `${meshShare}%` }} />
          </div>
          <dl className="mt-5 grid grid-cols-2 gap-4">
            <div className="rounded-xl bg-muted/60 p-4">
              <dt className="flex items-center gap-2 text-sm text-muted-foreground"><Wifi className="size-4 text-[var(--sev-info)]" aria-hidden="true" /> Direct online</dt>
              <dd className="metric-figure mt-2 text-3xl">{onlineCount}</dd>
            </div>
            <div className="rounded-xl bg-muted/60 p-4">
              <dt className="flex items-center gap-2 text-sm text-muted-foreground"><Bluetooth className="size-4 text-[var(--color-brand-amber)]" aria-hidden="true" /> Offline mesh relay</dt>
              <dd className="metric-figure mt-2 text-3xl">{meshCount}</dd>
            </div>
          </dl>
          <p className="mt-4 text-xs text-muted-foreground">{meshShare}% of open alerts reached dispatch only because nearby phones relayed them.</p>
        </section>
      </div>
    </div>
  );
};
