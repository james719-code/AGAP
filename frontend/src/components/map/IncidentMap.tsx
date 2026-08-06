import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, TargetID } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { useTheme } from '@/context/ThemeContext';
import { formatTargetLocation } from '@/lib/targetIdFormatter';


interface IncidentMapProps {
  incidents: Incident[];
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
    packet_id: 'pkt-050512-port-901',
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
    status: 'ACTIVE',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 902,
    packet_id: 'pkt-050512-daraga-902',
    packet_hash: '3f7b2c91d8e40562',
    sender_device_id: 'node-daraga-02',
    target_id: makeTargetId('05-05-12-02-01::HIGHWAY-PATROL-1'),
    incident_type: 'ACCIDENT',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1492,
    longitude: 123.7185,
    altitude: 15,
    gps_accuracy: 3.1,
    hop_count: 3,
    ttl: 10,
    status: 'DISPATCHED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 903,
    packet_id: 'pkt-050512-albay-903',
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
    status: 'ACTIVE',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 904,
    packet_id: 'pkt-050512-mayon-904',
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
    status: 'ACTIVE',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 905,
    packet_id: 'pkt-050512-penaranda-905',
    packet_hash: '5e6f7a8b9c0d1e2f',
    sender_device_id: 'node-park-05',
    target_id: makeTargetId('05-05-12-01-02::PENARANDA-STATION'),
    incident_type: 'CRIME',
    channel: 'BLE_MESH',
    delivery_path: 'BLE_CLOUD_SHORTCUT',
    latitude: 13.1398,
    longitude: 123.7445,
    altitude: 8,
    gps_accuracy: 2.0,
    hop_count: 1,
    ttl: 10,
    status: 'RESOLVED',
    event_timestamp: new Date().toISOString(),
    received_at: new Date().toISOString(),
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  },
  {
    id: 906,
    packet_id: 'pkt-050512-embarcadero-906',
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

export const IncidentMap: React.FC<IncidentMapProps> = ({
  incidents = [],
  selectedIncidentId,
  onSelectIncident,
  className = 'h-[480px] w-full rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-xl',
}) => {
  const { theme } = useTheme();
  // Center map on Legazpi City, Philippines (13.1391, 123.7438)
  const center: [number, number] = [13.1391, 123.7438];

  // If live incidents exist, display live incidents; otherwise fall back to seed data
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
    // Legazpi DRRMO Central Command Station target
    const commandLat = 13.1391;
    const commandLon = 123.7438;

    meshHopPath.push([originLat, originLon]);

    // Insert intermediate relay hops based on hop count
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
      <MapContainer center={center} zoom={13} scrollWheelZoom={true} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          url={tileUrl}
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
        />

        {/* Mesh Relay Vectors for selected incident */}
        {meshHopPath.length > 1 && (
          <Polyline
            positions={meshHopPath}
            pathOptions={{
              color: theme === 'dark' ? '#38bdf8' : '#0284c7',
              weight: 3,
              dashArray: '6, 8',
            }}
          />
        )}

        {displayIncidents.map((inc) => {

          if (!inc.latitude || !inc.longitude) return null;
          const isSelected = inc.id === selectedIncidentId;
          const loc = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude);

          return (
            <Marker
              key={inc.id || inc.packet_id}
              position={[inc.latitude, inc.longitude]}
              icon={createCustomIcon(inc.incident_type, inc.status, isSelected)}
              eventHandlers={{
                click: () => onSelectIncident && onSelectIncident(inc),
              }}
            >
              <Popup className="text-slate-900 font-sans">
                <div className="space-y-2 p-1.5 min-w-[240px]">
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

                  <div className="text-xs space-y-1.5 pt-1">
                    <p className="font-bold text-slate-800 text-xs">Target Address:</p>
                    <div className="bg-slate-100 dark:bg-slate-800 p-2 rounded-lg border border-slate-200 dark:border-slate-700">
                      <p className="font-bold text-slate-900 dark:text-slate-100 text-xs leading-snug">
                        {loc.readable}
                      </p>
                      <p className="font-mono text-[10px] text-slate-500 dark:text-slate-400 mt-1 truncate" title={loc.psgcFull}>
                        {loc.subText}
                      </p>
                    </div>

                    <div className="flex justify-between text-slate-600 dark:text-slate-400 pt-1 font-mono text-[11px]">
                      <span>P2P Hops: {inc.hop_count}</span>
                      <span>Channel: {inc.channel}</span>
                    </div>
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
