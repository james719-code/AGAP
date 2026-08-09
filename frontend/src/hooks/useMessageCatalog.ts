import { useCallback, useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import type { PredefinedMessage } from '@/types/api';

export function useMessageCatalog(includeInactive = true) {
  const [messages, setMessages] = useState<PredefinedMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMessages = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getMessageCatalog(includeInactive);
      setMessages(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load message catalog');
    } finally {
      setLoading(false);
    }
  }, [includeInactive]);

  useEffect(() => {
    fetchMessages();
  }, [fetchMessages]);

  const createMessage = async (message: Partial<PredefinedMessage>) => {
    const created = await apiService.createMessageCatalogEntry(message);
    setMessages((prev) => [...prev, created].sort((a, b) => a.id - b.id));
    return created;
  };

  const updateMessage = async (id: number, message: Partial<PredefinedMessage>) => {
    const updated = await apiService.updateMessageCatalogEntry(id, message);
    setMessages((prev) => prev.map((item) => (item.id === id ? updated : item)));
    return updated;
  };

  const deleteMessage = async (id: number) => {
    await apiService.deleteMessageCatalogEntry(id);
    setMessages((prev) => prev.filter((item) => item.id !== id));
  };

  return {
    messages,
    loading,
    error,
    refetch: fetchMessages,
    createMessage,
    updateMessage,
    deleteMessage,
  };
}
