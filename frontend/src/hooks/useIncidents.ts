import { useState, useEffect, useCallback } from 'react';
import type { Incident, IncidentFilter, IncidentStatus } from '@/types/api';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';

export function useIncidents(initialFilter: IncidentFilter = {}) {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [filter, setFilter] = useState<IncidentFilter>(initialFilter);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const { isConnected, lastIncident } = useWebSocket();

  const fetchIncidents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiService.getIncidents(filter);
      setIncidents(res.incidents || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load incidents');
      setIncidents([]);
    } finally {
      setLoading(false);
    }
  }, [filter]);

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
    }
  }, [lastIncident]);

  const updateStatus = async (id: number, status: IncidentStatus) => {
    try {
      await apiService.updateIncidentStatus(id, status);
      setIncidents((prev) =>
        prev.map((inc) => (inc.id === id ? { ...inc, status } : inc))
      );
    } catch (err) {
      throw new Error(err instanceof Error ? err.message : 'Failed to update incident status');
    }
  };

  return {
    incidents,
    filter,
    setFilter,
    loading,
    error,
    refetch: fetchIncidents,
    updateStatus,
    isWsConnected: isConnected,
  };
}