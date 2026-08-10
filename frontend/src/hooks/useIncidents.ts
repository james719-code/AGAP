import { useState, useEffect, useCallback, useRef } from 'react';
import type { Incident, IncidentFilter, IncidentStatus } from '@/types/api';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';

// Backend page size used when a caller asks for every incident matching a
// filter (fetchAll mode) instead of a single page. Kept well below anything
// the server is likely to reject, and looped until `total` is satisfied.
const FETCH_ALL_PAGE_SIZE = 200;
// Hard ceiling on how many pages fetchAll will pull, so a runaway `total`
// (or a filter that never converges) can't turn this into an unbounded loop.
const FETCH_ALL_MAX_PAGES = 50;

export interface UseIncidentsOptions {
  // When true, the hook ignores filter.limit/offset for fetching purposes
  // and instead pages through the backend until it has every incident
  // matching the rest of the filter, exposing them all via `incidents`.
  // Use this for pages that compute aggregates/analytics (Insights,
  // Reports, Dashboard) — NOT for the paginated incidents table, which
  // should keep filter.limit/offset and only show one page at a time.
  fetchAll?: boolean;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useIncidents(initialFilter: IncidentFilter = {}, options: UseIncidentsOptions = {}) {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [filter, setFilter] = useState<IncidentFilter>(initialFilter);
  const [total, setTotal] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { fetchAll = false } = options;

  const { isConnected, lastIncident, lastIncidentStatusChange, lastIncidentLocationUpdate } = useWebSocket();


  // Bump on every fetch so a slow, superseded request can't clobber state
  // from a newer one (e.g. filter changes rapidly while a request is in flight).
  const requestIdRef = useRef(0);

  const fetchIncidents = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      if (!fetchAll) {
        const res = await apiService.getIncidents(filter);
        if (requestId !== requestIdRef.current) return;
        setIncidents(res.incidents || []);
        setTotal(res.total ?? res.incidents?.length ?? 0);
        return;
      }

      // fetchAll: page through everything matching the filter (minus any
      // limit/offset the caller passed, which we override) so aggregate
      // stats reflect the true dataset instead of just the first page.
      const aggregateFilter = { ...filter };
      delete aggregateFilter.limit;
      delete aggregateFilter.offset;
      let offset = 0;
      let collected: Incident[] = [];
      let knownTotal = Infinity;
      let pages = 0;

      while (offset < knownTotal && pages < FETCH_ALL_MAX_PAGES) {
        const res = await apiService.getIncidents({
          ...aggregateFilter,
          limit: FETCH_ALL_PAGE_SIZE,
          offset,
        });
        if (requestId !== requestIdRef.current) return;

        const page = res.incidents || [];
        collected = collected.concat(page);
        knownTotal = res.total ?? collected.length;
        pages += 1;

        if (page.length === 0) break; // no more rows even if total looked bigger
        offset += page.length;
      }

      setIncidents(collected);
      setTotal(knownTotal === Infinity ? collected.length : knownTotal);
    } catch (err) {
      if (requestId !== requestIdRef.current) return;
      setError(err instanceof Error ? err.message : 'Failed to load incidents');
      setIncidents([]);
      setTotal(0);
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, [filter, fetchAll]);

  useEffect(() => {
    fetchIncidents();
  }, [fetchIncidents]);

  useEffect(() => {
    if (lastIncident) {
      setIncidents((prev) => {
        if (prev.some((inc) => inc.id === lastIncident.id)) {
          return prev;
        }
        return [lastIncident, ...prev];
      });
      setTotal((prev) => prev + 1);
    }
  }, [lastIncident]);

  // Keep this list in sync with remote status changes (another dispatcher,
  // a responder accepting/completing a dispatch, etc.) instead of only
  // updating on our own updateStatus() calls or a full refetch.
  useEffect(() => {
    if (!lastIncidentStatusChange) return;
    setIncidents((prev) => {
      const exists = prev.some((inc) => inc.id === lastIncidentStatusChange.incidentId);
      if (!exists) return prev; // status change for an incident outside this filtered/paginated list
      return prev.map((inc) =>
        inc.id === lastIncidentStatusChange.incidentId
          ? { ...inc, status: lastIncidentStatusChange.status }
          : inc
      );
    });
  }, [lastIncidentStatusChange]);

  useEffect(() => {
    if (!lastIncidentLocationUpdate) return;
    setIncidents((prev) => {
      const exists = prev.some((inc) => inc.id === lastIncidentLocationUpdate.incidentId);
      if (!exists) return prev;
      return prev.map((inc) =>
        inc.id === lastIncidentLocationUpdate.incidentId
          ? { ...inc, latitude: lastIncidentLocationUpdate.latitude, longitude: lastIncidentLocationUpdate.longitude }
          : inc
      );
    });
  }, [lastIncidentLocationUpdate]);


  const updateStatus = async (id: number, status: IncidentStatus) => {
    try {
      await apiService.updateIncidentStatus(id, status);
      setIncidents((prev) =>
        prev.map((inc) => (inc.id === id ? { ...inc, status } : inc))
      );
    } catch (err) {
      throw new Error(err instanceof Error ? err.message : 'Failed to update incident status', { cause: err });
    }
  };

  // Pagination helpers for callers using the (non-fetchAll) paginated mode.
  const limit = filter.limit ?? incidents.length ?? 0;
  const offset = filter.offset ?? 0;
  const hasNextPage = !fetchAll && limit > 0 && offset + limit < total;
  const hasPrevPage = !fetchAll && offset > 0;

  const nextPage = () => {
    if (!hasNextPage) return;
    setFilter((prev) => ({ ...prev, offset: (prev.offset ?? 0) + (prev.limit ?? incidents.length) }));
  };

  const prevPage = () => {
    if (!hasPrevPage) return;
    setFilter((prev) => ({ ...prev, offset: Math.max(0, (prev.offset ?? 0) - (prev.limit ?? incidents.length)) }));
  };

  return {
    incidents,
    total,
    filter,
    setFilter,
    loading,
    error,
    refetch: fetchIncidents,
    updateStatus,
    isWsConnected: isConnected,
    hasNextPage,
    hasPrevPage,
    nextPage,
    prevPage,
  };
}