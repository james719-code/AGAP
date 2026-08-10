import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import type { Incident, IncidentStatus } from '@/types/api';
import { useAuth } from '@/context/AuthContext';

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

// Normalized shape for a responder-location broadcast, regardless of the
// exact field names the backend sends on the wire.
export interface ResponderLocationUpdate {
  responderId: number;
  latitude: number;
  longitude: number;
  status?: string;
  lastSeenAt: string;
}

// Normalized shape for an incident status change, so any hook (not just the
// WebSocket-aware ones) can subscribe to a single, stable event shape.
export interface IncidentStatusChange {
  incidentId: number;
  status: IncidentStatus;
  updatedAt: string;
}

export interface IncidentLocationUpdate {
  incidentId: number;
  latitude: number;
  longitude: number;
  updatedAt: string;
}

interface RawResponderLocationPayload {
  responder_id?: number | string;
  id?: number | string;
  unit_id?: number | string;
  latitude?: number;
  longitude?: number;
  current_lat?: number;
  current_lon?: number;
  status?: string;
  last_seen_at?: string;
  updated_at?: string;
}

function parseResponderLocation(payload: unknown): ResponderLocationUpdate | null {
  if (!payload || typeof payload !== 'object') return null;
  const p = payload as RawResponderLocationPayload;

  const rawId = p.responder_id ?? p.id ?? p.unit_id;
  const responderId = rawId !== undefined ? Number(rawId) : NaN;
  const latitude = p.latitude ?? p.current_lat;
  const longitude = p.longitude ?? p.current_lon;

  if (!Number.isFinite(responderId) || typeof latitude !== 'number' || typeof longitude !== 'number') {
    return null;
  }

  return {
    responderId,
    latitude,
    longitude,
    status: p.status,
    lastSeenAt: p.last_seen_at ?? p.updated_at ?? new Date().toISOString(),
  };
}

interface WebSocketContextType {
  isConnected: boolean;
  lastIncident: Incident | null;
  liveIncidents: Incident[];
  clearLiveIncidents: () => void;
  /** Most recent responder GPS/status broadcast; changes on every event so consumers can useEffect on it. */
  lastResponderLocation: ResponderLocationUpdate | null;
  /** Most recent incident status change broadcast, normalized for any page/hook to consume. */
  lastIncidentStatusChange: IncidentStatusChange | null;
  /** Most recent incident location update broadcast. */
  lastIncidentLocationUpdate: IncidentLocationUpdate | null;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  const [isConnected, setIsConnected] = useState(false);
  const [lastIncident, setLastIncident] = useState<Incident | null>(null);
  const [liveIncidents, setLiveIncidents] = useState<Incident[]>([]);
  const [lastResponderLocation, setLastResponderLocation] = useState<ResponderLocationUpdate | null>(null);
  const [lastIncidentStatusChange, setLastIncidentStatusChange] = useState<IncidentStatusChange | null>(null);
  const [lastIncidentLocationUpdate, setLastIncidentLocationUpdate] = useState<IncidentLocationUpdate | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  const connectRef = useRef<() => void>(() => {});

  const connect = useCallback(() => {
    // Don't attempt a connection until auth has resolved and a token exists
    const token = localStorage.getItem('agap_auth_token');
    if (!token) {
      return;
    }

    // Guard: don't open a second socket if one is already open/connecting
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return;
    }

    try {
      const wsUrl = new URL(WS_URL);
      wsUrl.searchParams.set('token', token);
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

          if (envelope?.type === 'RESPONDER_LOCATION_UPDATED') {
            const update = parseResponderLocation(payload);
            if (update) {
              setLastResponderLocation(update);
            }
            return;
          }

          if (envelope?.type === 'INCIDENT_LOCATION_UPDATED') {
            const p = payload as { incident_id?: number | string; latitude?: number; longitude?: number; updated_at?: string };
            const incidentId = Number(p.incident_id);
            if (Number.isFinite(incidentId) && typeof p.latitude === 'number' && typeof p.longitude === 'number') {
              const locUpdate: IncidentLocationUpdate = {
                incidentId,
                latitude: p.latitude,
                longitude: p.longitude,
                updatedAt: p.updated_at ?? new Date().toISOString(),
              };
              setLastIncidentLocationUpdate(locUpdate);
              setLiveIncidents((prev) =>
                prev.map((incident) =>
                  incident.id === incidentId ? { ...incident, latitude: p.latitude!, longitude: p.longitude! } : incident
                )
              );
            }
            return;
          }

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
              // Emit a normalized, stable event so any hook (e.g. useIncidents)
              // can subscribe without depending on liveIncidents at all.
              setLastIncidentStatusChange({
                incidentId: Number(incidentId),
                status,
                updatedAt: new Date().toISOString(),
              });
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
        reconnectTimeoutRef.current = setTimeout(() => connectRef.current(), 3000);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch (err) {
      console.error('[WebSocket] Connection error:', err);
      if (mountedRef.current) {
        reconnectTimeoutRef.current = setTimeout(() => connectRef.current(), 3000);
      }
    }
  }, []);

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  useEffect(() => {
    mountedRef.current = true;

    if (loading) {
      // Auth state not resolved yet — wait rather than attempting a connection
      return;
    }

    if (!isAuthenticated) {
      // Logged out (or never logged in): make sure any existing socket is torn down
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null; // prevent reconnect firing on intentional close
        wsRef.current.close();
        wsRef.current = null;
      }
      queueMicrotask(() => setIsConnected(false));
      return;
    }

    connect();
    return () => {
      mountedRef.current = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null; // prevent reconnect firing on intentional close
        wsRef.current.close();
      }
    };
  }, [connect, isAuthenticated, loading]);

  const clearLiveIncidents = useCallback(() => {
    setLiveIncidents([]);
  }, []);

  return (
    <WebSocketContext.Provider
      value={{
        isConnected,
        lastIncident,
        liveIncidents,
        clearLiveIncidents,
        lastResponderLocation,
        lastIncidentStatusChange,
        lastIncidentLocationUpdate,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useWebSocket = (): WebSocketContextType => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
};