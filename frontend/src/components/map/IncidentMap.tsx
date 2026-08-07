import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Tooltip, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, ResponderUnit, TargetID } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { useTheme } from '@/context/ThemeContext';

interface IncidentMapProps {
  incidents?: Incident[];
  responders?: ResponderUnit[];
  showIncidents?: boolean;
  showResponders?: boolean;
  selectedIncidentId?: number | null;
  onSelectIncident?: (incident: Incident) => void;
  className?: string;
}

const makeTargetId = (raw: string): TargetID => ({
  raw,
  region: '05',
  province: '05',
  muni: '12',
  barangay: '01',
  unit: '01',
  uuid: '00000000-0000-0000-0000-000000000000',
});

export const DEFAULT_PLACEHOLDER_INCIDENTS: Incident[] = [
  {
    id: 901,
    incident_uuid: 'pkt-050512-port-901',
    packet_uuid: 'pkt-050512-port-901',
    packet_hash: '9a8f4c21e0b57182',
    sender_device_id: 'node-port-01',
    target_id: makeTargetId('05-05-12-01-04::LEGAZPI-PORT-PRECINCT'),
    incident_type: 'CRIME',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1458,
    longitude: 123.7542,
    altitude: 10,
    gps_accuracy: 2.5,
    hop_count: 2,
    ttl: 10,
    status: 'RECEIVED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 902,
    incident_uuid: 'pkt-050512-daraga-902',
    packet_uuid: 'pkt-050512-daraga-902',
    packet_hash: '3f7b2c91d8e40562',
    sender_device_id: 'node-daraga-02',
    target_id: makeTargetId('05-05-12-02-01::HIGHWAY-PATROL-1'),
    incident_type: 'ACCIDENT',
    channel: 'ONLINE',
    delivery_path: 'DIRECT_UPLINK',
    latitude: 13.1492,
    longitude: 123.7185,
    altitude: 15,
    gps_accuracy: 3.1,
    hop_count: 0,
    ttl: 10,
    status: 'DISPATCHED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 903,
    incident_uuid: 'pkt-050512-albay-903',
    packet_uuid: 'pkt-050512-albay-903',
    packet_hash: '7c8d9e01f2a34567',
    sender_device_id: 'node-er-03',
    target_id: makeTargetId('05-05-12-01-08::ALBAY-DOCTORS-MED'),
    incident_type: 'MEDICAL',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1365,
    longitude: 123.7389,
    altitude: 12,
    gps_accuracy: 1.8,
    hop_count: 1,
    ttl: 10,
    status: 'RECEIVED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 904,
    incident_uuid: 'pkt-050512-mayon-904',
    packet_uuid: 'pkt-050512-mayon-904',
    packet_hash: '1b2c3d4e5f6a7b8c',
    sender_device_id: 'node-volcano-04',
    target_id: makeTargetId('05-05-12-03-02::DRRMO-RESCUE-HQ'),
    incident_type: 'DISASTER',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1610,
    longitude: 123.7610,
    altitude: 45,
    gps_accuracy: 4.0,
    hop_count: 4,
    ttl: 10,
    status: 'RECEIVED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 905,
    incident_uuid: 'pkt-050512-penaranda-905',
    packet_uuid: 'pkt-050512-penaranda-905',
    packet_hash: '5e6f7a8b9c0d1e2f',
    sender_device_id: 'node-park-05',
    target_id: makeTargetId('05-05-12-01-02::PENARANDA-STATION'),
    incident_type: 'CRIME',
    channel: 'ONLINE',
    delivery_path: 'DIRECT_UPLINK',
    latitude: 13.1398,
    longitude: 123.7445,
    altitude: 8,
    gps_accuracy: 2.0,
    hop_count: 0,
    ttl: 10,
    status: 'RESOLVED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 906,
    incident_uuid: 'pkt-050512-embarcadero-906',
    packet_uuid: 'pkt-050512-embarcadero-906',
    packet_hash: '8f9a0b1c2d3e4f5a',
    sender_device_id: 'node-coast-06',
    target_id: makeTargetId('05-05-12-04-01::COAST-GUARD-LEG'),
    incident_type: 'MEDICAL',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1425,
    longitude: 123.7510,
    altitude: 5,
    gps_accuracy: 1.5,
    hop_count: 2,
    ttl: 10,
    status: 'DISPATCHED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
];

// Custom HTML markers per emergency type
const createCustomIcon = (type: string, status: string, isSelected: boolean) => {
  let colorClass = 'bg-red-500 shadow-red-500/50';
  if (type === 'ACCIDENT') colorClass = 'bg-amber-500 shadow-amber-500/50';
  if (type === 'MEDICAL') colorClass = 'bg-sky-500 shadow-sky-500/50';
  if (type === 'DISASTER') colorClass = 'bg-purple-500 shadow-purple-500/50';

  const opacityClass = status === 'RESOLVED' ? 'opacity-40' : 'animate-ping';
  const ringClass = isSelected ? 'ring-4 ring-white ring-offset-2 ring-offset-slate-900 scale-125' : '';

  return L.divIcon({
    className: 'custom-leaflet-marker',
    html: `
      <div class="relative flex items-center justify-center w-7 h-7 ${ringClass} transition-transform">
        <div class="absolute inline-flex h-full w-full rounded-full ${colorClass} opacity-75 ${opacityClass}"></div>
        <div class="relative inline-flex rounded-full h-4 w-4 ${colorClass} border-2 border-white shadow-lg"></div>
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
};

const createResponderIcon = (status: string) => {
  const colorClass = status === 'OFFLINE' ? 'bg-slate-400' : 'bg-emerald-500 shadow-emerald-500/50';
  return L.divIcon({
    className: 'custom-responder-marker',
    html: `
      <div class="relative flex items-center justify-center w-8 h-8 rounded-full bg-slate-900 border-2 border-emerald-400 shadow-lg">
        <div class="w-3.5 h-3.5 rounded-full ${colorClass}"></div>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
};

export const IncidentMap: React.FC<IncidentMapProps> = ({
  incidents = [],
  responders = [],
  showIncidents = true,
  showResponders = true,
  selectedIncidentId,
  onSelectIncident,
  className = 'h-[480px] w-full rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-xl',
}) => {
  const { theme } = useTheme();
  const mapRef = React.useRef<L.Map | null>(null);
  // Center map on Legazpi City, Philippines (13.1391, 123.7438)
  const center: [number, number] = [13.1391, 123.7438];

  React.useEffect(() => {
    const timer = setTimeout(() => mapRef.current?.invalidateSize(), 120);
    const onResize = () => mapRef.current?.invalidateSize();
    window.addEventListener('resize', onResize);
    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', onResize);
    };
  }, []);

  const displayIncidents = incidents.length > 0 ? [...incidents] : [...DEFAULT_PLACEHOLDER_INCIDENTS];

  // Tile layer URL: use CartoDB dark/light styled tiles for slick GIS map aesthetics
  const tileUrl =
    theme === 'dark'
      ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
      : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';

  const selectedIncident = displayIncidents.find((i) => i.id === selectedIncidentId);

  // Generate simulated P2P mesh hop polyline vector for selected incident
  const meshHopPath: [number, number][] = [];
  if (selectedIncident && selectedIncident.latitude && selectedIncident.longitude) {
    const originLat = selectedIncident.latitude;
    const originLon = selectedIncident.longitude;
    const commandLat = 13.1391;
    const commandLon = 123.7438;

    meshHopPath.push([originLat, originLon]);

    const hops = selectedIncident.hop_count || 1;
    for (let h = 1; h < hops; h++) {
      const ratio = h / hops;
      const stepLat = originLat + (commandLat - originLat) * ratio + (Math.random() - 0.5) * 0.005;
      const stepLon = originLon + (commandLon - originLon) * ratio + (Math.random() - 0.5) * 0.005;
      meshHopPath.push([stepLat, stepLon]);
    }
    meshHopPath.push([commandLat, commandLon]);
  }

  return (
    <div className={className}>
      <MapContainer
        ref={mapRef}
        center={center}
        zoom={13}
        scrollWheelZoom={true}
        style={{ height: '100%', width: '100%' }}
      >
        <TileLayer
          url={tileUrl}
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
        />

        {/* Mesh Relay Vectors for selected incident */}
        {showIncidents && meshHopPath.length > 1 && (
          <Polyline
            positions={meshHopPath}
            pathOptions={{
              color: theme === 'dark' ? '#4FB4C9' : '#1F91A8',
              weight: 3,
              dashArray: '6, 8',
            }}
          />
        )}

        {/* Incident Markers */}
        {showIncidents &&
          displayIncidents.map((inc) => {
            if (!inc.latitude || !inc.longitude) return null;
            const isSelected = inc.id === selectedIncidentId;
            const isP2P = inc.channel === 'BLE_MESH' || inc.delivery_path === 'BLE_CLOUD_SHORTCUT';
            const sourceLabel = isP2P ? 'P2P RECEIVED (BLE MESH)' : 'DATABASE ONLINE';
            const targetAddressStr = typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A';

            return (
              <Marker
                key={inc.id || inc.incident_uuid || inc.packet_uuid}
                position={[inc.latitude, inc.longitude]}
                icon={createCustomIcon(inc.incident_type, inc.status, isSelected)}
                eventHandlers={{
                  click: () => onSelectIncident && onSelectIncident(inc),
                }}
              >
                {/* Always-visible hover Tooltip */}
                <Tooltip direction="top" offset={[0, -10]} opacity={0.95}>
                  <div className="text-xs p-1 space-y-1 font-sans">
                    <div className="flex items-center gap-1.5 font-bold">
                      <span className={`px-1.5 py-0.5 text-[9px] rounded font-mono ${isP2P ? 'bg-amber-500/20 text-amber-500 border border-amber-500/30' : 'bg-emerald-500/20 text-emerald-500 border border-emerald-500/30'}`}>
                        {sourceLabel}
                      </span>
                      <span>{inc.incident_type} #{inc.id}</span>
                    </div>
                    <div className="text-[11px] text-muted-foreground">
                      Status: <strong className="text-foreground">{inc.status}</strong> • Hops: {inc.hop_count}
                    </div>
                    <div className="font-mono text-[10px] text-muted-foreground truncate max-w-[200px]">
                      Target: {targetAddressStr}
                    </div>
                  </div>
                </Tooltip>

                {/* Click Popup Details */}
                <Popup className="text-foreground font-sans">
                  <div className="space-y-2 p-1 min-w-[220px]">
                    <div className="flex items-center justify-between gap-2">
                      <Badge
                        variant={
                          inc.incident_type === 'CRIME'
                            ? 'destructive'
                            : inc.incident_type === 'MEDICAL'
                            ? 'info'
                            : inc.incident_type === 'DISASTER'
                            ? 'purple'
                            : 'warning'
                        }
                      >
                        {inc.incident_type}
                      </Badge>
                      <Badge variant="outline">{inc.status}</Badge>
                    </div>

                    <div className="text-xs space-y-1.5">
                      <div className="flex items-center justify-between text-[10px] font-mono">
                        <span className="text-muted-foreground">Source:</span>
                        <span className={`font-bold ${isP2P ? 'text-amber-500' : 'text-emerald-500'}`}>
                          {sourceLabel}
                        </span>
                      </div>

                      <p className="font-semibold text-foreground">Target Address:</p>
                      <p className="font-mono text-[10px] text-muted-foreground bg-muted p-1.5 rounded border border-border break-all">
                        {targetAddressStr}
                      </p>

                      <div className="flex justify-between text-muted-foreground pt-1 font-mono text-[11px]">
                        <span>P2P Hops: {inc.hop_count}</span>
                        <span>Channel: {inc.channel}</span>
                      </div>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}

        {/* Responder Unit Markers */}
        {showResponders &&
          responders.map((resp) => {
            if (!resp.current_lat || !resp.current_lon) return null;

            return (
              <Marker
                key={resp.id}
                position={[resp.current_lat, resp.current_lon]}
                icon={createResponderIcon(resp.status)}
              >
                <Tooltip direction="top" offset={[0, -12]} opacity={0.95}>
                  <div className="text-xs p-1 space-y-0.5 font-sans">
                    <div className="font-bold text-emerald-500 flex items-center gap-1">
                      <span>\uD83D\uDE92</span> {resp.unit_callsign}
                    </div>
                    <div className="text-[11px]">
                      Status: <strong className="text-foreground">{resp.status}</strong>
                    </div>
                    <div className="text-[10px] text-muted-foreground font-mono">
                      Target: {resp.target_id || `Agency #${resp.agency_id}`}
                    </div>
                  </div>
                </Tooltip>

                <Popup className="text-foreground font-sans">
                  <div className="space-y-1.5 p-1 min-w-[180px]">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-sm text-foreground">{resp.unit_callsign}</span>
                      <Badge variant="outline" className="text-emerald-500 border-emerald-500/40">{resp.status}</Badge>
                    </div>
                    <div className="text-xs text-muted-foreground space-y-1">
                      <p>Agency ID: #{resp.agency_id}</p>
                      <p className="font-mono text-[10px]">Target: {resp.target_id}</p>
                      <p className="text-[10px]">GPS: {resp.current_lat.toFixed(4)}, {resp.current_lon.toFixed(4)}</p>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}
      </MapContainer>
    </div>
  );
};

