import { useState, useEffect, useCallback } from 'react';
import type { IncidentDispatch, RescueUnitMember, ResponderUnit } from '@/types/api';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';

// eslint-disable-next-line react-refresh/only-export-components
export function useResponders(agencyId?: number, pollingIntervalMs = 0) {
  const [responders, setResponders] = useState<ResponderUnit[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { lastResponderLocation, lastResponderUnitUpdate } = useWebSocket();

  const fetchResponders = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const data = await apiService.getResponders(agencyId);
      setResponders(data || []);
    } catch (err) {
      if (!silent) {
        setError(err instanceof Error ? err.message : 'Failed to load responders');
        setResponders([]);
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [agencyId]);

  useEffect(() => {
    queueMicrotask(() => {
      fetchResponders();
    });
  }, [fetchResponders]);

  useEffect(() => {
    if (pollingIntervalMs <= 0) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void fetchResponders(true);
    }, pollingIntervalMs);
    return () => window.clearInterval(timer);
  }, [fetchResponders, pollingIntervalMs]);

  // Merge in live GPS/status broadcasts from other clients (e.g. an Android
  // responder publishing a new position) instead of only updating on our
  // own updateLocation() calls.
  useEffect(() => {
    queueMicrotask(() => {
      if (!lastResponderLocation) return;
      setResponders((prev) => {
        const exists = prev.some((r) => r.id === lastResponderLocation.responderId);
        if (!exists) return prev; // event for a unit outside this filtered list (e.g. different agency)
        return prev.map((r) =>
          r.id === lastResponderLocation.responderId
            ? {
                ...r,
                current_lat: lastResponderLocation.latitude,
                current_lon: lastResponderLocation.longitude,
                status: lastResponderLocation.status ?? r.status,
                last_seen_at: lastResponderLocation.lastSeenAt,
              }
            : r
        );
      });
    });
  }, [lastResponderLocation]);

  useEffect(() => {
    if (!lastResponderUnitUpdate) return;
    if (lastResponderUnitUpdate.kind === 'delete') {
      setResponders((prev) => prev.filter((unit) => unit.id !== lastResponderUnitUpdate.responderId));
      return;
    }

    const unit = lastResponderUnitUpdate.unit;
    if (agencyId !== undefined && unit.agency_id !== agencyId) return;
    setResponders((prev) => {
      const exists = prev.some((candidate) => candidate.id === unit.id);
      return exists
        ? prev.map((candidate) => candidate.id === unit.id ? unit : candidate)
        : [...prev, unit];
    });
  }, [lastResponderUnitUpdate, agencyId]);

  const createResponder = async (responder: Partial<ResponderUnit>) => {
    const created = await apiService.createResponder(responder);
    setResponders((prev) => [...prev, created]);
    return created;
  };

  const updateResponder = async (id: number, responder: Partial<ResponderUnit>) => {
    const updated = await apiService.updateResponder(id, responder);
    setResponders((prev) => prev.map((r) => (r.id === id ? updated : r)));
    return updated;
  };

  const updateLocation = async (id: number, latitude: number, longitude: number, status: string) => {
    await apiService.updateResponderLocation(id, latitude, longitude, status);
    setResponders((prev) =>
      prev.map((r) =>
        r.id === id
          ? { ...r, current_lat: latitude, current_lon: longitude, status, last_seen_at: new Date().toISOString() }
          : r
      )
    );
  };

  const deleteResponder = async (id: number) => {
    await apiService.deleteResponder(id);
    setResponders((prev) => prev.filter((r) => r.id !== id));
  };

  const getRescueUnitMembers = async (id: number): Promise<RescueUnitMember[]> => {
    return apiService.getRescueUnitMembers(id);
  };

  const getRescueUnitDispatches = async (id: number): Promise<IncidentDispatch[]> => {
    return apiService.getRescueUnitDispatches(id);
  };

  const addRescueUnitMember = async (id: number, responderId: number, isLeader = false): Promise<RescueUnitMember> => {
    return apiService.addRescueUnitMember(id, responderId, isLeader);
  };

  const removeRescueUnitMember = async (id: number, responderId: number): Promise<void> => {
    await apiService.removeRescueUnitMember(id, responderId);
  };

  return {
    responders,
    loading,
    error,
    refetch: fetchResponders,
    createResponder,
    updateResponder,
    updateLocation,
    deleteResponder,
    getRescueUnitMembers,
    getRescueUnitDispatches,
    addRescueUnitMember,
    removeRescueUnitMember,
  };
}
