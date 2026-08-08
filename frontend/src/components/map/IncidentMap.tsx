import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Tooltip, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, ResponderUnit } from '@/types/api';
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
  // Center map on Legazpi City, Philippines
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

  // Tile layer URL: use CartoDB dark/light styled tiles for slick GIS map aesthetics
  const tileUrl =
    theme === 'dark'
      ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
      : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';

  const selectedIncident = incidents.find((i) => i.id === selectedIncidentId);

  // Direct-line relay indicator from the incident's origin to command HQ.
  // We only know the total hop_count from telemetry, not each intermediate
  // hop's coordinates — Incident/TelemetryPacket don't carry per-hop
  // location — so this is a straight line rather than a fabricated
  // multi-point path. It shouldn't imply more precision than the data
  // actually supports.
  const meshPath: [number, number][] =
    selectedIncident?.latitude && selectedIncident?.longitude
      ? [[selectedIncident.latitude, selectedIncident.longitude], center]
      : [];

  const noActiveIncidents = showIncidents && incidents.length === 0;

  return (
    <div className={`relative ${className}`}>
      {noActiveIncidents && (
        <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none">
          <span className="px-3 py-1.5 rounded-full text-xs font-semibold bg-card/95 border border-border text-muted-foreground shadow-md">
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

        {/* Mesh relay indicator for selected incident */}
        {showIncidents && meshPath.length > 1 && (
          <Polyline
            positions={meshPath}
            pathOptions={{
              color: theme === 'dark' ? '#4FB4C9' : '#1F91A8',
              weight: 3,
              dashArray: '6, 8',
            }}
          />
        )}

        {/* Incident Markers */}
        {showIncidents &&
          incidents.map((inc) => {
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