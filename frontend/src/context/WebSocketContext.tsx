import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import type { Incident, IncidentStatus } from '@/types/api';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

interface WebSocketEnvelope<T = unknown> {
  type: string;
  payload: T;
}

const INCIDENT_STATUSES = new Set<IncidentStatus>([
  'CREATED', 'QUEUED', 'RECEIVED', 'ACKNOWLEDGED', 'DISPATCHED',
  'EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED', 'FALSE_ALARM',
]);

function isIncidentStatus(value: string | undefined): value is IncidentStatus {
  return value !== undefined && INCIDENT_STATUSES.has(value as IncidentStatus);
}

interface WebSocketContextType {
  isConnected: boolean;
  lastIncident: Incident | null;
  liveIncidents: Incident[];
  clearLiveIncidents: () => void;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isConnected, setIsConnected] = useState(false);
  const [lastIncident, setLastIncident] = useState<Incident | null>(null);
  const [liveIncidents, setLiveIncidents] = useState<Incident[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const connect = useCallback(() => {
    // Guard: don't open a second socket if one is already open/connecting
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return;
    }

    try {
      const token = localStorage.getItem('agap_auth_token');
      const wsUrl = new URL(WS_URL);
      if (token) wsUrl.searchParams.set('token', token);
      const ws = new WebSocket(wsUrl.toString());
      wsRef.current = ws;

      ws.onopen = () => {
        if (!mountedRef.current) return;
        setIsConnected(true);
      };

      ws.onmessage = (event) => {
        if (!mountedRef.current) return;
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
            setLiveIncidents((prev) => [incident, ...prev.slice(0, 49)]);
          }
        } catch (e) {
          console.error('[WebSocket] Error parsing message:', e);
        }
      };

      ws.onclose = () => {
        if (!mountedRef.current) return;
        setIsConnected(false);
        reconnectTimeoutRef.current = setTimeout(connect, 3000);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch (err) {
      console.error('[WebSocket] Connection error:', err);
      if (mountedRef.current) {
        reconnectTimeoutRef.current = setTimeout(connect, 3000);
      }
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    connect();
    return () => {
      mountedRef.current = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null; // prevent reconnect firing on intentional close
        wsRef.current.close();
      }
    };
  }, [connect]);

  const clearLiveIncidents = useCallback(() => {
    setLiveIncidents([]);
  }, []);

  return (
    <WebSocketContext.Provider value={{ isConnected, lastIncident, liveIncidents, clearLiveIncidents }}>
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = (): WebSocketContextType => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
};