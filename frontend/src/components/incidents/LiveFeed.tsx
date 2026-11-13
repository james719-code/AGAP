import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight, Radio } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { incidentIdentity } from '@/lib/incidentIdentity';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { cn } from '@/lib/utils';
import type { Incident } from '@/types/api';

interface LiveFeedProps {
  incidents: Incident[];
  loading: boolean;
  error: boolean;
  selectedIncidentId?: number;
  onSelectIncident: (id: number) => void;
}

const ITEMS_PER_PAGE = 4;

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
            {loading ? 'Loading recent incidents…' : error ? 'Incident activity unavailable' : 'No recent incident activity'}
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
              <li key={incidentIdentity(incident)}>
                <button
                  type="button"
                  onClick={() => onSelectIncident(incident.id)}
                  aria-pressed={selected}
                  className={cn('block w-full px-4 py-3.5 text-left hover:bg-muted focus-visible:outline-offset-[-3px]', selected && 'bg-primary/10')}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-bold capitalize">{incident.incident_type.replaceAll('_', ' ').toLowerCase()} #{incident.id}</span>
                    <Badge variant="outline" className="shrink-0">{incident.status.replaceAll('_', ' ')}</Badge>
                  </span>
                  <span className="mt-1 block truncate text-sm text-muted-foreground">{location.readable || 'Location unavailable'}</span>
                  <span className="mt-1.5 flex flex-wrap gap-x-2 text-xs text-muted-foreground tabular-nums">
                    {receivedAt && <time dateTime={receivedAt}>{new Date(receivedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</time>}
                    <span>{incident.channel} · {incident.hop_count} hops</span>
                  </span>
                </button>
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
