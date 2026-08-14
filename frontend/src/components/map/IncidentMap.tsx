import React from 'react';
import { GeoJSON, MapContainer, Polyline, TileLayer, Marker, Popup, Tooltip } from 'react-leaflet';
import L from 'leaflet';
import type { GeoJsonObject } from 'geojson';
import 'leaflet/dist/leaflet.css';
import type { Incident, ResponderUnit } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { useTheme } from '@/context/ThemeContext';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { barangays } from './barangayMapData';
import type { BarangayMapItem } from './barangayMapData';

interface IncidentMapProps {
  incidents?: Incident[];
  responders?: ResponderUnit[];
  showIncidents?: boolean;
  showResponders?: boolean;
  showBarangays?: boolean;
  selectedBarangayCode?: string | null;
  onSelectBarangay?: (barangay: BarangayMapItem | null) => void;
  route?: {
    responder: ResponderUnit;
    incident: Incident;
  } | null;
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
  showBarangays = true,
  selectedBarangayCode,
  onSelectBarangay,
  route,
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
  const selectedBarangay = barangays.find((barangay) => barangay.barangay_code === selectedBarangayCode);

  React.useEffect(() => {
    if (selectedIncident && Number.isFinite(selectedIncident.latitude) && Number.isFinite(selectedIncident.longitude)) {
      mapRef.current?.flyTo([selectedIncident.latitude, selectedIncident.longitude], 16, { duration: 0.6 });
    }
  }, [selectedIncident]);

  React.useEffect(() => {
    if (selectedBarangay) {
      mapRef.current?.flyTo(
        [selectedBarangay.representative_latitude_e7 / 1e7, selectedBarangay.representative_longitude_e7 / 1e7],
        15,
        { duration: 0.6 }
      );
    }
  }, [selectedBarangay]);

  React.useEffect(() => {
    if (!route || !route.responder.current_lat || !route.responder.current_lon) return;
    mapRef.current?.fitBounds(
      [
        [route.responder.current_lat, route.responder.current_lon],
        [route.incident.latitude, route.incident.longitude],
      ],
      { padding: [64, 64], maxZoom: 16, animate: true }
    );
  }, [route]);
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

        {showBarangays && barangays.map((barangay) => {
          const isSelected = barangay.barangay_code === selectedBarangayCode;
          const geometry = JSON.parse(barangay.boundary_geojson) as GeoJsonObject;
          const center: [number, number] = [
            barangay.representative_latitude_e7 / 1e7,
            barangay.representative_longitude_e7 / 1e7,
          ];

          return (
            <React.Fragment key={barangay.barangay_code}>
              <GeoJSON
                key={`${barangay.barangay_code}-${theme}-${isSelected}`}
                data={geometry}
                style={{
                  color: isSelected ? '#F2A540' : '#1F91A8',
                  weight: isSelected ? 3 : 1.5,
                  opacity: isSelected ? 1 : 0.8,
                  fillColor: isSelected ? '#F2A540' : '#1F91A8',
                  fillOpacity: isSelected ? 0.22 : theme === 'dark' ? 0.11 : 0.08,
                }}
                eventHandlers={{
                  click: (event) => {
                    L.DomEvent.stopPropagation(event.originalEvent);
                    onSelectBarangay?.(barangay);
                  },
                  mouseover: (event) => event.target.setStyle({ weight: 3, fillOpacity: 0.2 }),
                  mouseout: (event) => event.target.setStyle({
                    weight: isSelected ? 3 : 1.5,
                    fillOpacity: isSelected ? 0.22 : theme === 'dark' ? 0.11 : 0.08,
                  }),
                }}
              >
                <Tooltip sticky direction="top" opacity={0.96}>
                  <div className="space-y-0.5 p-0.5 font-sans text-xs">
                    <div className="font-bold">{barangay.barangay_name}</div>
                    <div className="text-[10px] text-[#5C5A56]">{barangay.city_municipality}, {barangay.province}</div>
                    <div className="font-mono text-[9px] text-[#1F91A8]">{barangay.barangay_code}</div>
                  </div>
                </Tooltip>
              </GeoJSON>
              <Marker
                position={center}
                interactive={false}
                zIndexOffset={-200}
                icon={L.divIcon({
                  className: 'barangay-map-label',
                  html: `<span>${barangay.barangay_name.replace(/^Barangay\s+/i, 'Brgy. ')}</span>`,
                  iconSize: [150, 20],
                  iconAnchor: [75, 10],
                })}
              />
            </React.Fragment>
          );
        })}

        {route && Number.isFinite(route.responder.current_lat) && Number.isFinite(route.responder.current_lon) && (
          <Polyline
            positions={[
              [route.responder.current_lat, route.responder.current_lon],
              [route.incident.latitude, route.incident.longitude],
            ]}
            pathOptions={{
              color: '#C97B63',
              weight: 5,
              opacity: 0.95,
              dashArray: '12 9',
              lineCap: 'round',
              lineJoin: 'round',
            }}
          >
            <Tooltip sticky direction="top" opacity={0.96}>
              <div className="space-y-0.5 p-0.5 font-sans text-xs">
                <div className="font-bold text-[#C97B63]">Dispatch route preview</div>
                <div>{route.responder.unit_callsign} → Incident #{route.incident.id}</div>
                <div className="text-[9px] text-[#5C5A56]">Direct fallback route</div>
              </div>
            </Tooltip>
          </Polyline>
        )}

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
