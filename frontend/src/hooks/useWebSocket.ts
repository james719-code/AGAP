import { useEffect, useRef, useState, useCallback } from 'react';
import type { Incident } from '@/types/api';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export interface UseWebSocketReturn {
  isConnected: boolean;
  lastIncident: Incident | null;
  liveIncidents: Incident[];
  clearLiveIncidents: () => void;
}

export function useWebSocket(): UseWebSocketReturn {
  const [isConnected, setIsConnected] = useState(false);
  const [lastIncident, setLastIncident] = useState<Incident | null>(null);
  const [liveIncidents, setLiveIncidents] = useState<Incident[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = useCallback(() => {
    try {
      const ws = new WebSocket(WS_URL);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        console.log('[WebSocket] Connected to AGAP dispatch hub');
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          // Backend sends raw Incident objects over WebSocket
          if (data && data.packet_id) {
            const incident = data as Incident;
            setLastIncident(incident);
            setLiveIncidents((prev) => [incident, ...prev.slice(0, 49)]); // Keep last 50
          }
        } catch (e) {
          console.error('[WebSocket] Error parsing message:', e);
        }
      };

      ws.onclose = () => {
        setIsConnected(false);
        console.warn('[WebSocket] Disconnected. Reconnecting in 3s...');
        reconnectTimeoutRef.current = setTimeout(connect, 3000);
      };

      ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error);
        ws.close();
      };
    } catch (err) {
      console.error('[WebSocket] Connection error:', err);
      reconnectTimeoutRef.current = setTimeout(connect, 3000);
    }
  }, []);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [connect]);

  const clearLiveIncidents = useCallback(() => {
    setLiveIncidents([]);
  }, []);

  return {
    isConnected,
    lastIncident,
    liveIncidents,
    clearLiveIncidents,
  };
}
