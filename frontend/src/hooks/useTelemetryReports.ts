import { useCallback, useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';
import type { OperationalReport, TelemetryInsights } from '@/types/api';

export function useTelemetryReports() {
  const [insights, setInsights] = useState<TelemetryInsights | null>(null);
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate, lastTelemetryUpdateAt } = useWebSocket();

  const refetch = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const [nextInsights, nextReport] = await Promise.all([
        apiService.getTelemetryInsights(),
        apiService.getOperationalReport(),
      ]);
      setInsights(nextInsights);
      setReport(nextReport);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load telemetry and reports');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { queueMicrotask(() => void refetch()); }, [refetch]);
  useEffect(() => {
    if (lastIncident || lastIncidentStatusChange || lastResponderUnitUpdate || lastTelemetryUpdateAt) {
      queueMicrotask(() => void refetch(true));
    }
  }, [lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate, lastTelemetryUpdateAt, refetch]);

  return { insights, report, loading, error, refetch };
}
