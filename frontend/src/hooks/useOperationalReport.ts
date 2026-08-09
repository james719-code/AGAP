import { useCallback, useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';
import type { OperationalReport } from '@/types/api';

export function useOperationalReport() {
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate } = useWebSocket();
  const refetch = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      setReport(await apiService.getOperationalReport());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load operational report');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);
  useEffect(() => { void refetch(); }, [refetch]);
  useEffect(() => {
    if (lastIncident || lastIncidentStatusChange || lastResponderUnitUpdate) void refetch(true);
  }, [lastIncident, lastIncidentStatusChange, lastResponderUnitUpdate, refetch]);
  return { report, loading, error, refetch };
}
