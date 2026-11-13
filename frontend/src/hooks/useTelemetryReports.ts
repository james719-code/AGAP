import { useCallback, useEffect, useRef, useState } from 'react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';
import type { OperationalReport } from '@/types/api';
import { usesHostedApi } from '@/services/backend';
import { useVisibleRefresh } from '@/hooks/useVisibleRefresh';

export function useTelemetryReports() {
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate } = useWebSocket();
  const refreshTimerRef = useRef<number | null>(null);
  const requestIdRef = useRef(0);

  const refetch = useCallback(async (silent = false) => {
    const requestId = ++requestIdRef.current;
    if (!silent) setLoading(true);
    try {
      const nextReport = await apiService.getOperationalReport();
      if (requestId !== requestIdRef.current) return;
      setReport(nextReport);
      setError(null);
    } catch (err) {
      if (requestId !== requestIdRef.current) return;
      setError(err instanceof Error ? err.message : 'Failed to load telemetry and reports');
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => { queueMicrotask(() => void refetch()); }, [refetch]);
  useVisibleRefresh(refetch, usesHostedApi ? 60_000 : 0);
  useEffect(() => {
    if (!lastIncident && !lastIncidentStatusChange && !lastResponderUnitUpdate) return;
    if (refreshTimerRef.current !== null) window.clearTimeout(refreshTimerRef.current);
    refreshTimerRef.current = window.setTimeout(() => void refetch(true), 500);
    return () => {
      if (refreshTimerRef.current !== null) window.clearTimeout(refreshTimerRef.current);
    };
  }, [lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate, refetch]);

  return { report, loading, error, refetch };
}
