import { useEffect, useRef, useState, useCallback } from 'react';
import type { Incident, IncidentStatus } from '@/types/api';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8088/ws';

interface WebSocketEnvelope<T = unknown> {
  type: string;
  payload: T;
}

const INCIDENT_STATUSES = new Set<IncidentStatus>([
  'CREATED',
  'QUEUED',
  'RECEIVED',
  'ACKNOWLEDGED',
  'DISPATCHED',
  'EN_ROUTE',
  'ON_SCENE',
  'RESOLVED',
  'CANCELLED',
  'FALSE_ALARM',
]);

function isIncidentStatus(value: string | undefined): value is IncidentStatus {
  return value !== undefined && INCIDENT_STATUSES.has(value as IncidentStatus);
}

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
      const token = localStorage.getItem('agap_auth_token');
      const wsUrl = new URL(WS_URL);
      if (token) wsUrl.searchParams.set('token', token);
      const ws = new WebSocket(wsUrl.toString());
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        console.log('[WebSocket] Connected to AGAP dispatch hub');
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          const envelope = data as WebSocketEnvelope;
          const payload = envelope?.payload ?? data;
          if (envelope?.type === 'INCIDENT_STATUS_UPDATED') {
            const incidentId = String((payload as { incident_id?: number | string }).incident_id ?? '');
            const status = (payload as { status?: string; new_status?: string }).status
              ?? (payload as { new_status?: string }).new_status;
            if (incidentId && isIncidentStatus(status)) {
              setLiveIncidents((prev) =>
                prev.map((incident) =>
                  String(incident.id) === incidentId ? { ...incident, status } : incident
                )
              );
            }
            return;
          }
          if (payload && ((payload as Incident).incident_uuid || (payload as Incident).packet_uuid)) {
            const incident = payload as Incident;
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
