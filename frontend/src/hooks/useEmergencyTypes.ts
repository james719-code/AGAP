import { useCallback, useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import type { EmergencyType } from '@/types/api';

// eslint-disable-next-line react-refresh/only-export-components
export function useEmergencyTypes(includeInactive = true) {
  const [emergencyTypes, setEmergencyTypes] = useState<EmergencyType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchEmergencyTypes = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getEmergencyTypes(includeInactive);
      setEmergencyTypes(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load emergency types');
    } finally {
      setLoading(false);
    }
  }, [includeInactive]);

  useEffect(() => {
    queueMicrotask(() => {
      fetchEmergencyTypes();
    });
  }, [fetchEmergencyTypes]);

  const createEmergencyType = async (emergencyType: Partial<EmergencyType>) => {
    const created = await apiService.createEmergencyType(emergencyType);
    setEmergencyTypes((prev) => [...prev, created].sort((a, b) => a.default_priority - b.default_priority || a.name.localeCompare(b.name)));
    return created;
  };

  const updateEmergencyType = async (id: number, emergencyType: Partial<EmergencyType>) => {
    const updated = await apiService.updateEmergencyType(id, emergencyType);
    setEmergencyTypes((prev) => prev.map((item) => (item.id === id ? updated : item)));
    return updated;
  };

  const deleteEmergencyType = async (id: number) => {
    await apiService.deleteEmergencyType(id);
    setEmergencyTypes((prev) => prev.filter((item) => item.id !== id));
  };

  return {
    emergencyTypes,
    loading,
    error,
    refetch: fetchEmergencyTypes,
    createEmergencyType,
    updateEmergencyType,
    deleteEmergencyType,
  };
}