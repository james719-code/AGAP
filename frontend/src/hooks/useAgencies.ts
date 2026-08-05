import { useState, useEffect, useCallback } from 'react';
import type { Agency } from '@/types/api';
import { apiService } from '@/services/api';

export function useAgencies() {
  const [agencies, setAgencies] = useState<Agency[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAgencies = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getAgencies();
      setAgencies(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch agencies');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAgencies();
  }, [fetchAgencies]);

  const createAgency = async (agency: Partial<Agency>) => {
    const created = await apiService.createAgency(agency);
    setAgencies((prev) => [...prev, created]);
    return created;
  };

  const updateAgency = async (id: number, agency: Partial<Agency>) => {
    const updated = await apiService.updateAgency(id, agency);
    setAgencies((prev) => prev.map((a) => (a.id === id ? updated : a)));
    return updated;
  };

  const deleteAgency = async (id: number) => {
    await apiService.deleteAgency(id);
    setAgencies((prev) => prev.filter((a) => a.id !== id));
  };

  return {
    agencies,
    loading,
    error,
    refetch: fetchAgencies,
    createAgency,
    updateAgency,
    deleteAgency,
  };
}
