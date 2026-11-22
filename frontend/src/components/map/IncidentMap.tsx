import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Tooltip, useMap } from 'react-leaflet';
import { LocateFixed } from 'lucide-react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, ResponderUnit } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { useTheme } from '@/context/ThemeContext';
import { formatTargetLocation } from '@/lib/targetIdFormatter';
import { incidentStatusLabel, incidentStatusTone } from '@/lib/incidentPresentation';

interface IncidentMapProps {
  incidents?: Incident[];
  responders?: ResponderUnit[];
  showIncidents?: boolean;
  showResponders?: boolean;
  selectedIncidentId?: number | null;
  onSelectIncident?: (incident: Incident) => void;
  className?: string;
  scrollWheelZoom?: boolean;
}

function validPosition(latitude: number, longitude: number): boolean {
  return Number.isFinite(latitude) && Number.isFinite(longitude) && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
}

function MapViewport({ points, selected }: { points: [number, number][]; selected?: Incident }) {
  const map = useMap();
  const initialized = React.useRef(false);
  const fitLocations = React.useCallback(() => {
    if (!points.length) return;
    map.invalidateSize({ pan: false });
    map.fitBounds(L.latLngBounds(points), { padding: [45, 45], maxZoom: 15, animate: false });
  }, [map, points]);

  // Frame the first loaded records; subsequent polling must preserve a
  // dispatcher's manual pan/zoom. The explicit control restores the overview.
  React.useEffect(() => {
    if (initialized.current || !points.length) return;
    initialized.current = true;
    fitLocations();
  }, [fitLocations, points.length]);

  const latitude = selected?.latitude;
  const longitude = selected?.longitude;
  React.useEffect(() => {
    if (latitude != null && longitude != null && validPosition(latitude, longitude)) {
      map.setView([latitude, longitude], 16, { animate: false });
    }
  }, [map, selected?.id, latitude, longitude]);

  return (
    <button type="button" disabled={!points.length} onClick={(event) => { event.stopPropagation(); fitLocations(); }} className="map-fit-button absolute right-3 top-3 z-[500] inline-flex min-h-10 items-center gap-2 rounded-lg border border-border bg-card px-3 text-sm font-semibold text-foreground shadow-md hover:bg-muted disabled:opacity-50" aria-label="Fit all locations on map">
      <LocateFixed className="size-4" aria-hidden="true" /><span className="hidden sm:inline">Fit locations</span>
    </button>
  );
}

// Reuse icons by appearance so live GPS updates don't rebuild every marker's
// DOM through Leaflet's setIcon. The visual variants keep both caches bounded.
const incidentIcons = new Map<string, L.DivIcon>();
const responderIcons = new Map<boolean, L.DivIcon>();
const mapCategories = [
  { letter: 'C', label: 'Crime', color: '#a82e20' },
  { letter: 'M', label: 'Medical', color: '#176e7e' },
  { letter: 'A', label: 'Accident', color: '#80520a' },
  { letter: 'D', label: 'Disaster', color: '#7643a5' },
  { letter: 'F', label: 'Fire', color: '#ac4c14' },
];

export function MapLegend({ showResponders = false }: { showResponders?: boolean }) {
  return (
    <div aria-label="Map marker legend" className="flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-muted-foreground">
      {mapCategories.map(({ letter, label, color }) => (
        <span key={label} className="inline-flex items-center gap-1.5"><span aria-hidden="true" className="grid size-5 place-items-center rounded-full text-[10px] font-bold text-white" style={{ backgroundColor: color }}>{letter}</span>{label}</span>
      ))}
      {showResponders && <span className="inline-flex items-center gap-1.5"><span className="size-4 rounded-full border-2 border-emerald-600 bg-emerald-500/30" aria-hidden="true" />Responder unit</span>}
    </div>
  );
}

// Custom HTML markers per emergency type – using brand palette
const createCustomIcon = (type: string, status: string, isSelected: boolean) => {
  let colorClass = 'bg-[#a82e20]'; // Crime
  if (type === 'ACCIDENT') colorClass = 'bg-[#80520a]';
  if (type === 'MEDICAL') colorClass = 'bg-[#176e7e]';
  if (type === 'DISASTER') colorClass = 'bg-[#7643a5]';
  if (type === 'FIRE') colorClass = 'bg-[#ac4c14]';

  const opacityClass = ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(status) ? 'opacity-40' : 'opacity-90';
  const ringClass = isSelected ? 'ring-4 ring-white ring-offset-2 ring-offset-[#0A0A0A] scale-125' : '';
  const letters: Record<string, string> = { CRIME: 'C', ACCIDENT: 'A', MEDICAL: 'M', DISASTER: 'D', FIRE: 'F' };
  const letter = letters[type] ?? '!';
  const iconKey = `${colorClass}:${opacityClass}:${ringClass}:${letter}`;
  const cached = incidentIcons.get(iconKey);
  if (cached) return cached;

  const icon = L.divIcon({
    className: 'custom-leaflet-marker',
    html: `
      <div class="relative flex items-center justify-center w-7 h-7 rounded-full ${ringClass} transition-transform">
        <div class="relative grid h-7 w-7 place-items-center rounded-full border-2 border-white shadow-lg ${colorClass} ${opacityClass} text-xs font-bold text-white">${letter}</div>
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
  incidentIcons.set(iconKey, icon);
  return icon;
};

const createResponderIcon = (status: string) => {
  const isOffline = status === 'OFFLINE';
  const cached = responderIcons.get(isOffline);
  if (cached) return cached;
  const colorClass = isOffline ? 'bg-[#6B6864]' : 'bg-emerald-500 shadow-emerald-500/50';
  const icon = L.divIcon({
    className: 'custom-responder-marker',
    html: `
      <div class="relative flex items-center justify-center w-8 h-8 rounded-full bg-[#0A0A0A] border-2 border-emerald-400 shadow-lg">
        <div class="w-3.5 h-3.5 rounded-full ${colorClass}"></div>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
  responderIcons.set(isOffline, icon);
  return icon;
};

export const IncidentMap: React.FC<IncidentMapProps> = ({
  incidents = [],
  responders = [],
  showIncidents = true,
  showResponders = true,
  selectedIncidentId,
  onSelectIncident,
  className = 'h-[480px] w-full',
  scrollWheelZoom = true,
}) => {
  const { theme } = useTheme();
  const mapRef = React.useRef<L.Map | null>(null);
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [tileError, setTileError] = React.useState(false);
  const tileEvents = React.useMemo(() => ({
    loading: () => setTileError(false),
    tileerror: () => setTileError(true),
  }), []);
  // Center on Legazpi City, Philippines
  const center: [number, number] = [13.1391, 123.7438];

  React.useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver(() => mapRef.current?.invalidateSize({ pan: false }));
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  const configuredTiles = import.meta.env.VITE_MAP_TILES_URL?.trim();
  const standardTiles = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
  const defaultTiles = !configuredTiles || configuredTiles === 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
    ? standardTiles
    : configuredTiles;
  const tileUrl = theme === 'dark' ? import.meta.env.VITE_MAP_TILES_DARK_URL?.trim() || defaultTiles : defaultTiles;
  const tileAttribution = import.meta.env.VITE_MAP_ATTRIBUTION?.trim() ||
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

  const selectedIncident = incidents.find((i) => i.id === selectedIncidentId);

  const points = React.useMemo<[number, number][]>(() => [
    ...(showIncidents ? incidents.filter((item) => validPosition(item.latitude, item.longitude)).map((item): [number, number] => [item.latitude, item.longitude]) : []),
    ...(showResponders ? responders.filter((item) => validPosition(item.current_lat, item.current_lon)).map((item): [number, number] => [item.current_lat, item.current_lon]) : []),
  ], [incidents, responders, showIncidents, showResponders]);
  const noActiveIncidents = showIncidents && incidents.length === 0;

  return (
    <div ref={containerRef} className={`agap-map relative isolate ${className}`}>
      {noActiveIncidents && (
        <div className="pointer-events-none absolute left-1/2 top-4 z-10 -translate-x-1/2 whitespace-nowrap">
          <span className="rounded-full border border-border bg-card/95 px-3 py-1.5 text-xs font-semibold text-muted-foreground shadow-md">
            No incidents to display
          </span>
        </div>
      )}
      {tileError && (
        <p role="status" className="tone-warning pointer-events-none absolute inset-x-3 bottom-7 z-10 rounded-md border border-border px-3 py-2 text-xs font-medium shadow-sm">
          Map imagery unavailable. Incident locations remain accessible in the incident register.
        </p>
      )}

      <MapContainer
        ref={mapRef}
        className="relative z-0"
        center={center}
        zoom={13}
        scrollWheelZoom={scrollWheelZoom}
        style={{ height: '100%', width: '100%' }}
      >
        <MapViewport points={points} selected={selectedIncident} />
        <TileLayer
          eventHandlers={tileEvents}
          url={tileUrl}
          attribution={tileAttribution}
        />

        {showIncidents &&
          incidents.map((inc) => {
            if (!validPosition(inc.latitude, inc.longitude)) return null;
            const isSelected = inc.id === selectedIncidentId;
            const isP2P = inc.channel === 'BLE_MESH' || inc.delivery_path === 'BLE_CLOUD_SHORTCUT';
            const sourceLabel = isP2P ? 'Bluetooth mesh' : 'Online';
            const targetLocation = formatTargetLocation(inc.target_id, inc.latitude, inc.longitude);

            return (
              <Marker
                key={inc.id || inc.incident_uuid || inc.packet_uuid}
                position={[inc.latitude, inc.longitude]}
                icon={createCustomIcon(inc.incident_type, inc.status, isSelected)}
                title={`${incidentStatusLabel(inc.incident_type)} #${inc.id} — ${incidentStatusLabel(inc.status)}`}
                alt={`Incident #${inc.id}`}
                eventHandlers={{
                  click: () => onSelectIncident && onSelectIncident(inc),
                }}
              >
                <Tooltip direction="top" offset={[0, -10]} opacity={0.95}>
                  <div className="text-xs p-1 space-y-1 font-sans">
                    <div className="flex items-center gap-1.5 font-bold">
                      <span className={`px-1.5 py-0.5 text-[9px] rounded-md font-mono ${isP2P ? 'bg-[#F2A540]/20 text-[#F2A540] border border-[#F2A540]/30' : 'bg-[#1F91A8]/20 text-[#1F91A8] border border-[#1F91A8]/30'}`}>
                        {sourceLabel}
                      </span>
                      <span>{inc.incident_type} #{inc.id}</span>
                    </div>
                    <div className="text-[11px] text-muted-foreground">
                      Status: <strong className="text-foreground">{inc.status}</strong> • Hops: {inc.hop_count}
                    </div>
                    <div className="font-mono text-[10px] text-muted-foreground truncate max-w-[200px]">
                      {targetLocation.readable}
                    </div>
                  </div>
                </Tooltip>

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
                      <span className={`status-pill ${incidentStatusTone(inc.status)}`}>{incidentStatusLabel(inc.status)}</span>
                    </div>

                    <div className="text-xs space-y-1.5">
                      <div className="flex items-center justify-between text-[10px] font-mono">
                        <span className="text-muted-foreground">Source:</span>
                        <span className={`font-bold ${isP2P ? 'text-[#F2A540]' : 'text-[#1F91A8]'}`}>
                          {sourceLabel}
                        </span>
                      </div>

                      <p className="font-semibold text-foreground">Location</p>
                      <p className="break-all font-mono text-[10px] text-muted-foreground">
                        {targetLocation.readable}
                      </p>
                      <p className="text-[9px] text-muted-foreground">{targetLocation.subText}</p>

                      <div className="flex justify-between text-muted-foreground pt-1 font-mono text-[11px]">
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
            if (!validPosition(resp.current_lat, resp.current_lon)) return null;
            return (
              <Marker
                key={resp.id}
                position={[resp.current_lat, resp.current_lon]}
                icon={createResponderIcon(resp.status)}
                title={resp.unit_callsign}
                alt={`Responder ${resp.unit_callsign}`}
              >
                <Tooltip direction="top" offset={[0, -12]} opacity={0.95}>
                  <div className="text-xs p-1 space-y-0.5 font-sans">
                    <div className="font-bold text-emerald-500 flex items-center gap-1">
                      <span>🚒</span> {resp.unit_callsign}
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
                      <Badge
                        variant={resp.status === 'OFFLINE' ? 'outline' : resp.status === 'IDLE' ? 'success' : 'info'}
                        className="text-[10px]"
                      >
                        {resp.status}
                      </Badge>
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
