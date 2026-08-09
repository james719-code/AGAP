import { useCallback, useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';
import type { TelemetryInsights } from '@/types/api';

export function useTelemetryInsights() {
  const [insights, setInsights] = useState<TelemetryInsights | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastIncident, lastTelemetryUpdateAt } = useWebSocket();

  const refetch = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await apiService.getTelemetryInsights();
      setInsights(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load telemetry insights');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { void refetch(); }, [refetch]);
  useEffect(() => {
    if (lastIncident || lastTelemetryUpdateAt) void refetch(true);
  }, [lastIncident, lastTelemetryUpdateAt, refetch]);

  return { insights, loading, error, refetch };
}
