import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, ChevronLeft, ChevronRight, MapPin, Radio } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { incidentIdentity } from '@/lib/incidentIdentity';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { cn } from '@/lib/utils';
import { incidentStatusLabel, incidentStatusTone, NEEDS_ASSIGNMENT_STATUSES } from '@/lib/incidentPresentation';
import type { Incident } from '@/types/api';

interface LiveFeedProps {
  incidents: Incident[];
  loading: boolean;
  error: boolean;
  selectedIncidentId?: number;
  onSelectIncident: (id: number) => void;
}

const ITEMS_PER_PAGE = 4;

// Left-edge severity bar mirrors the status pill so priority reads at a glance.
const SEVERITY_BY_TONE: Record<string, string> = {
  'tone-danger': 'sev-critical',
  'tone-warning': 'sev-moderate',
  'tone-info': 'sev-info',
  'tone-success': 'sev-resolved',
};

export function LiveFeed({ incidents, loading, error, selectedIncidentId, onSelectIncident }: LiveFeedProps) {
  const [page, setPage] = useState(1);
  const pageCount = Math.max(1, Math.ceil(incidents.length / ITEMS_PER_PAGE));
  const currentPage = Math.min(page, pageCount);
  const start = (currentPage - 1) * ITEMS_PER_PAGE;

  return (
    <div className="flex flex-1 flex-col">
      {incidents.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center px-5 py-10 text-center">
          <span className="grid size-11 place-items-center rounded-lg bg-muted text-muted-foreground">
            <Radio className="size-5" aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm font-semibold" role="status">
            {loading ? 'Loading incident queue…' : error ? 'Incident queue unavailable' : 'No incidents in the current records'}
          </p>
          {!loading && (
            <Link to="/dashboard/incidents" className="mt-2 text-sm font-semibold text-primary hover:underline">
              Open incident register
            </Link>
          )}
        </div>
      ) : (
        <ul className="flex-1 divide-y divide-border">
          {incidents.slice(start, start + ITEMS_PER_PAGE).map((incident) => {
            const receivedAt = incident.received_at || incident.event_timestamp || incident.created_at;
            const location = formatTargetLocation(incident.target_id, incident.latitude, incident.longitude);
            const selected = incident.id === selectedIncidentId;

            return (
              <li key={incidentIdentity(incident)} className={cn('sev-bar relative flex items-stretch', SEVERITY_BY_TONE[incidentStatusTone(incident.status)] ?? '', selected && 'bg-primary/10')}>
                <button
                  type="button"
                  onClick={() => onSelectIncident(incident.id)}
                  aria-pressed={selected}
                  aria-label={`Show incident #${incident.id} on map`}
                  className="flex min-w-0 flex-1 items-start gap-3 px-4 py-3.5 text-left hover:bg-muted focus-visible:outline-offset-[-3px]"
                >
                  <span className={cn('grid size-10 shrink-0 place-items-center rounded-xl', NEEDS_ASSIGNMENT_STATUSES.has(incident.status) ? 'tone-danger' : 'bg-muted text-muted-foreground')}>
                    <AlertTriangle className="size-4.5" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                      <span className="text-sm font-bold">{incidentStatusLabel(incident.incident_type)}</span>
                      <span className="font-mono text-xs text-muted-foreground tabular-nums">#{incident.id}</span>
                    </span>
                    <span className="mt-1 flex items-start gap-1 text-xs text-muted-foreground">
                      <MapPin className="mt-0.5 size-3 shrink-0" aria-hidden="true" />
                      <span className="min-w-0 truncate">{location.readable || 'Location unavailable'}</span>
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground tabular-nums">
                      <span className={cn('status-pill', incidentStatusTone(incident.status))}>{incidentStatusLabel(incident.status)}</span>
                      {receivedAt && <time dateTime={receivedAt}>{new Date(receivedAt).toLocaleString([], { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}</time>}
                    </span>
                  </span>
                </button>
                <Link to={`/dashboard/incidents?incidentId=${incident.id}`} aria-label={`Open incident #${incident.id}`} className="grid min-w-11 shrink-0 place-items-center border-l border-border text-muted-foreground hover:bg-muted hover:text-primary">
                  <ChevronRight className="size-4" aria-hidden="true" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      {incidents.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border px-4 py-2.5">
          <Link to="/dashboard/incidents" className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline">
            All incidents <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
          {pageCount > 1 && (
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground tabular-nums">
              <Button type="button" variant="ghost" size="icon" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 1} aria-label="Previous activity page">
                <ChevronLeft className="size-4" />
              </Button>
              <span>{currentPage} / {pageCount}</span>
              <Button type="button" variant="ghost" size="icon" onClick={() => setPage(currentPage + 1)} disabled={currentPage === pageCount} aria-label="Next activity page">
                <ChevronRight className="size-4" />
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
