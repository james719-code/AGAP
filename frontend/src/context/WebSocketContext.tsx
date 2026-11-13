import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import type { Incident, IncidentMessage, IncidentStatus, ResponderUnit } from '@/types/api';
import { useAuth } from '@/context/AuthContext';
import { deduplicateIncidents, incidentIdentityParts } from '@/lib/incidentIdentity';
import { sessionKeys, usesHostedApi } from '@/services/backend';

const WS_URL = import.meta.env.VITE_WS_URL || '/agap/ws';

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

export interface IncidentReportUpdate {
  incidentId: number;
  reportId: number;
  detailState: string;
  updatedAt: string;
}

export type ResponderUnitUpdate =
  | { kind: 'upsert'; unit: ResponderUnit }
  | { kind: 'delete'; responderId: number };

export interface CatalogUpdate {
  catalog: 'agencies' | 'emergency_types' | 'messages';
  action: 'upsert' | 'delete';
  id: number;
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
  lastIncidentLocationUpdate: IncidentLocationUpdate | null;
  lastIncidentReportUpdate: IncidentReportUpdate | null;
  lastResponderUnitUpdate: ResponderUnitUpdate | null;
  lastCatalogUpdate: CatalogUpdate | null;
  lastTelemetryUpdateAt: string | null;
  lastIncidentMessage: IncidentMessage | null;
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
  const [lastIncidentReportUpdate, setLastIncidentReportUpdate] = useState<IncidentReportUpdate | null>(null);
  const [lastResponderUnitUpdate, setLastResponderUnitUpdate] = useState<ResponderUnitUpdate | null>(null);
  const [lastCatalogUpdate, setLastCatalogUpdate] = useState<CatalogUpdate | null>(null);
  const [lastTelemetryUpdateAt, setLastTelemetryUpdateAt] = useState<string | null>(null);
  const [lastIncidentMessage, setLastIncidentMessage] = useState<IncidentMessage | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  const connectRef = useRef<() => void>(() => {});
  const seenIncidentIdentitiesRef = useRef<Set<string>>(new Set());

  const connect = useCallback(() => {
    // Don't attempt a connection until auth has resolved and a token exists
    const token = localStorage.getItem(sessionKeys.access);
    if (!token) {
      return;
    }

    // Guard: don't open a second socket if one is already open/connecting
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return;
    }

    try {
      const wsUrl = new URL(WS_URL, window.location.origin);
      wsUrl.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
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

          if (envelope?.type === 'INCIDENT_MESSAGE_CREATED') {
            const message = payload as IncidentMessage;
            if (message && Number.isFinite(Number(message.id)) && Number.isFinite(Number(message.incident_id))) {
              setLastIncidentMessage(message);
            }
            return;
          }

          if (envelope?.type === 'RESPONDER_LOCATION_UPDATED') {
            const update = parseResponderLocation(payload);
            if (update) {
              setLastResponderLocation(update);
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

          if (envelope?.type === 'INCIDENT_LOCATION_UPDATED') {
            const loc = payload as { incident_id?: number; latitude?: number; longitude?: number };
            if (Number.isFinite(Number(loc.incident_id))) {
              setLastIncidentLocationUpdate({
                incidentId: Number(loc.incident_id),
                latitude: Number(loc.latitude ?? 0),
                longitude: Number(loc.longitude ?? 0),
                updatedAt: new Date().toISOString(),
              });
            }
            return;
          }

          if (envelope?.type === 'RESPONDER_UNIT_UPDATED') {
            const unit = payload as ResponderUnit;
            if (Number.isFinite(Number(unit.id))) {
              setLastResponderUnitUpdate({ kind: 'upsert', unit });
            }
            return;
          }

          if (envelope?.type === 'RESPONDER_UNIT_DELETED') {
            const responderId = Number((payload as { responder_id?: number }).responder_id);
            if (Number.isFinite(responderId)) {
              setLastResponderUnitUpdate({ kind: 'delete', responderId });
            }
            return;
          }

          if (envelope?.type === 'INCIDENT_REPORT_UPDATED') {
            const report = payload as { id?: number; incident_id?: number; detail_state?: string };
            if (Number.isFinite(Number(report.incident_id))) {
              setLastIncidentReportUpdate({
                incidentId: Number(report.incident_id),
                reportId: Number(report.id ?? 0),
                detailState: report.detail_state ?? '',
                updatedAt: new Date().toISOString(),
              });
            }
            return;
          }

          if (envelope?.type === 'CATALOG_UPDATED') {
            const change = payload as { catalog?: string; action?: string; id?: number };
            if (
              (change.catalog === 'agencies' || change.catalog === 'emergency_types' || change.catalog === 'messages')
              && (change.action === 'upsert' || change.action === 'delete')
              && Number.isFinite(Number(change.id))
            ) {
              setLastCatalogUpdate({
                catalog: change.catalog,
                action: change.action,
                id: Number(change.id),
                updatedAt: new Date().toISOString(),
              });
            }
            return;
          }

          if (envelope?.type === 'TELEMETRY_INSIGHTS_UPDATED') {
            setLastTelemetryUpdateAt(new Date().toISOString());
            return;
          }

          if (payload && ((payload as Incident).incident_uuid || (payload as Incident).packet_uuid)) {
            const incident = payload as Incident;
            const identities = incidentIdentityParts(incident);
            const alreadySeen = identities.some((identity) => seenIncidentIdentitiesRef.current.has(identity));
            identities.forEach((identity) => seenIncidentIdentitiesRef.current.add(identity));
            setLiveIncidents((prev) => deduplicateIncidents([incident, ...prev]).slice(0, 50));
            if (!alreadySeen) setLastIncident(incident);
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

    if (usesHostedApi) return;

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
    seenIncidentIdentitiesRef.current.clear();
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
        lastIncidentReportUpdate,
        lastResponderUnitUpdate,
        lastCatalogUpdate,
        lastTelemetryUpdateAt,
        lastIncidentMessage,
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
