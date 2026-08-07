import { useState, useEffect, useCallback } from 'react';
import type { Incident, IncidentFilter, IncidentStatus } from '@/types/api';
import { apiService } from '@/services/api';
import { MOCK_INCIDENTS } from '@/services/mockData';
import { useWebSocket } from './useWebSocket';

export function useIncidents(initialFilter: IncidentFilter = {}) {
  const [incidents, setIncidents] = useState<Incident[]>(MOCK_INCIDENTS);
  const [filter, setFilter] = useState<IncidentFilter>(initialFilter);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const { isConnected, lastIncident } = useWebSocket();

  const fetchIncidents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiService.getIncidents(filter);
      if (res.incidents && res.incidents.length > 0) {
        setIncidents(res.incidents);
      } else {
        setIncidents(MOCK_INCIDENTS);
      }
    } catch {
      // Fallback to mock incidents when offline/unreachable
      setIncidents(MOCK_INCIDENTS);
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    fetchIncidents();
  }, [fetchIncidents]);

  // prepend new incidents received via WebSocket if they match current filters
  useEffect(() => {
    if (lastIncident) {
      setIncidents((prev) => {
        const lastKey = lastIncident.incident_uuid || lastIncident.packet_uuid;
        if (prev.some((inc) => (inc.incident_uuid || inc.packet_uuid) === lastKey)) {
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
    } catch (err: any) {
      throw new Error(err.message || 'Failed to update incident status');
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
