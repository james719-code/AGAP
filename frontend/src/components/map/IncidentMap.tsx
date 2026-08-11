import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Tooltip } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, ResponderUnit } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { useTheme } from '@/context/ThemeContext';
import { formatTargetLocation } from '@/lib/targetIdFormatter';

interface IncidentMapProps {
  incidents?: Incident[];
  responders?: ResponderUnit[];
  showIncidents?: boolean;
  showResponders?: boolean;
  selectedIncidentId?: number | null;
  onSelectIncident?: (incident: Incident) => void;
  className?: string;
}

// Custom HTML markers per emergency type – using brand palette
const createCustomIcon = (type: string, status: string, isSelected: boolean) => {
  let colorClass = 'bg-[#D15033] shadow-[#D15033]/50'; // CRIME (primary)
  if (type === 'ACCIDENT') colorClass = 'bg-[#F2A540] shadow-[#F2A540]/50'; // amber
  if (type === 'MEDICAL') colorClass = 'bg-[#1F91A8] shadow-[#1F91A8]/50'; // teal
  if (type === 'DISASTER') colorClass = 'bg-purple-500 shadow-purple-500/50'; // keep distinct
  if (type === 'FIRE') colorClass = 'bg-orange-600 shadow-orange-600/50';

  const opacityClass = status === 'RESOLVED' ? 'opacity-40' : 'animate-ping';
  const ringClass = isSelected ? 'ring-4 ring-white ring-offset-2 ring-offset-[#0A0A0A] scale-125' : '';

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
  const colorClass = status === 'OFFLINE' ? 'bg-[#6B6864]' : 'bg-emerald-500 shadow-emerald-500/50';
  return L.divIcon({
    className: 'custom-responder-marker',
    html: `
      <div class="relative flex items-center justify-center w-8 h-8 rounded-full bg-[#0A0A0A] border-2 border-emerald-400 shadow-lg">
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
  className = 'h-[480px] w-full',
}) => {
  const { theme } = useTheme();
  const mapRef = React.useRef<L.Map | null>(null);
  // Center on Legazpi City, Philippines
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

  const tileUrl =
    theme === 'dark'
      ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
      : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';

  const selectedIncident = incidents.find((i) => i.id === selectedIncidentId);

  React.useEffect(() => {
    if (selectedIncident && Number.isFinite(selectedIncident.latitude) && Number.isFinite(selectedIncident.longitude)) {
      mapRef.current?.flyTo([selectedIncident.latitude, selectedIncident.longitude], 16, { duration: 0.6 });
    }
  }, [selectedIncident]);
  const noActiveIncidents = showIncidents && incidents.length === 0;

  return (
    <div className={`relative isolate ${className}`}>
      {noActiveIncidents && (
        <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none">
          <span className="rounded-full border border-border bg-card/95 px-3 py-1.5 text-xs font-semibold text-muted-foreground shadow-md">
            No active incidents
          </span>
        </div>
      )}

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

        {showIncidents &&
          incidents.map((inc) => {
            if (!inc.latitude || !inc.longitude) return null;
            const isSelected = inc.id === selectedIncidentId;
            const isP2P = inc.channel === 'BLE_MESH' || inc.delivery_path === 'BLE_CLOUD_SHORTCUT';
            const sourceLabel = isP2P ? 'Bluetooth mesh' : 'Online';
            const targetLocation = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude);

            return (
              <Marker
                key={inc.id || inc.incident_uuid || inc.packet_uuid}
                position={[inc.latitude, inc.longitude]}
                icon={createCustomIcon(inc.incident_type, inc.status, isSelected)}
                eventHandlers={{
                  click: () => onSelectIncident && onSelectIncident(inc),
                }}
              >
                <Tooltip direction="top" offset={[0, -10]} opacity={0.95}>
                  <div className="text-xs p-1 space-y-1 font-sans">
                    <div className="flex items-center gap-1.5 font-bold">
                      <span className={`px-1.5 py-0.5 text-[9px] rounded-[2px] font-mono ${isP2P ? 'bg-[#F2A540]/20 text-[#F2A540] border border-[#F2A540]/30' : 'bg-[#1F91A8]/20 text-[#1F91A8] border border-[#1F91A8]/30'}`}>
                        {sourceLabel}
                      </span>
                      <span>{inc.incident_type} #{inc.id}</span>
                    </div>
                    <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                      Status: <strong className="text-[#0A0A0A] dark:text-white">{inc.status}</strong> • Hops: {inc.hop_count}
                    </div>
                    <div className="font-mono text-[10px] text-[#5C5A56] dark:text-[#B9B6B0] truncate max-w-[200px]">
                      {targetLocation.readable}
                    </div>
                  </div>
                </Tooltip>

                <Popup className="text-[#0A0A0A] dark:text-[#F2F0EC] font-sans">
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
                        <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Source:</span>
                        <span className={`font-bold ${isP2P ? 'text-[#F2A540]' : 'text-[#1F91A8]'}`}>
                          {sourceLabel}
                        </span>
                      </div>

                      <p className="font-semibold text-[#0A0A0A] dark:text-white">Location</p>
                      <p className="break-all font-mono text-[10px] text-[#5C5A56] dark:text-[#B9B6B0]">
                        {targetLocation.readable}
                      </p>
                      <p className="text-[9px] text-[#5C5A56] dark:text-[#B9B6B0]">{targetLocation.subText}</p>

                      <div className="flex justify-between text-[#5C5A56] dark:text-[#B9B6B0] pt-1 font-mono text-[11px]">
                        <span>Relay hops: {inc.hop_count}</span>
                        <span>Channel: {inc.channel}</span>
                      </div>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}

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
                      <span>🚒</span> {resp.unit_callsign}
                    </div>
                    <div className="text-[11px]">
                      Status: <strong className="text-[#0A0A0A] dark:text-white">{resp.status}</strong>
                    </div>
                    <div className="text-[10px] text-[#5C5A56] dark:text-[#B9B6B0] font-mono">
                      Target: {resp.target_id || `Agency #${resp.agency_id}`}
                    </div>
                  </div>
                </Tooltip>

                <Popup className="text-[#0A0A0A] dark:text-[#F2F0EC] font-sans">
                  <div className="space-y-1.5 p-1 min-w-[180px]">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-sm text-[#0A0A0A] dark:text-white">{resp.unit_callsign}</span>
                      <Badge
                        variant={resp.status === 'OFFLINE' ? 'outline' : resp.status === 'IDLE' ? 'success' : 'info'}
                        className="text-[10px]"
                      >
                        {resp.status}
                      </Badge>
                    </div>
                    <div className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] space-y-1">
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
