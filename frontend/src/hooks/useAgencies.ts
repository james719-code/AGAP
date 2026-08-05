import { useState, useEffect, useCallback } from 'react';
import type { Agency } from '@/types/api';
import { apiService } from '@/services/api';
import { MOCK_AGENCIES } from '@/services/mockData';

export function useAgencies() {
  const [agencies, setAgencies] = useState<Agency[]>(MOCK_AGENCIES);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAgencies = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getAgencies();
      if (data && data.length > 0) {
        setAgencies(data);
      } else {
        setAgencies(MOCK_AGENCIES);
      }
    } catch {
      setAgencies(MOCK_AGENCIES);
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
