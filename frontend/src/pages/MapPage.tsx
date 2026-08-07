import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { IncidentMap } from '@/components/map/IncidentMap';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/select';
import { Search, Filter, AlertTriangle } from 'lucide-react';
import type { Incident } from '@/types/api';

export const MapPage: React.FC = () => {
  const { incidents } = useIncidents();
  const { responders } = useResponders();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState('Last 30 Days');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [showRespondersOnMap, setShowRespondersOnMap] = useState(true);

  // Filter incidents based on floating search & category options
  const filteredIncidents = incidents.filter((inc) => {
    if (selectedCategory !== 'ALL' && inc.incident_type !== selectedCategory) return false;
    if (activeOpsOnly && inc.status === 'RESOLVED') return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const targetStr = typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || '';
      return (
        inc.incident_type.toLowerCase().includes(q) ||
        targetStr.toLowerCase().includes(q) ||
        inc.id.toString().includes(q)
      );
    }
    return true;
  });

  const selectedIsP2P = selectedIncident && (selectedIncident.channel === 'BLE_MESH' || selectedIncident.delivery_path === 'BLE_CLOUD_SHORTCUT');

  const selectedEtaInfo = React.useMemo(() => {
    if (!selectedIncident?.latitude || !selectedIncident?.longitude) return null;
    const lat1 = 13.1391;
    const lon1 = 123.7438;
    const lat2 = selectedIncident.latitude;
    const lon2 = selectedIncident.longitude;
    const R = 6371e3; // metres
    const φ1 = (lat1 * Math.PI) / 180;
    const φ2 = (lat2 * Math.PI) / 180;
    const Δφ = ((lat2 - lat1) * Math.PI) / 180;
    const Δλ = ((lon2 - lon1) * Math.PI) / 180;
    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const meters = R * c;
    const km = meters / 1000;
    const distText = km < 1 ? `${Math.round(meters)} m` : `${km.toFixed(1)} km`;
    const speedMps = 11.11; // ~40 km/h avg speed
    const mins = Math.max(1, Math.round(meters / speedMps / 60));
    const etaText = mins < 60 ? `~${mins} min ETA` : `~${Math.floor(mins / 60)}h ${mins % 60}m ETA`;
    return { distText, etaText };
  }, [selectedIncident]);

  return (
    <div className="relative h-[calc(100vh-6rem)] w-full rounded-lg overflow-hidden border border-border shadow-sm">
      {/* Interactive Map Base */}
      <IncidentMap
        incidents={filteredIncidents}
        responders={responders}
        showIncidents={true}
        showResponders={showRespondersOnMap}
        selectedIncidentId={selectedIncident?.id}
        onSelectIncident={(inc) => setSelectedIncident(inc)}
        className="h-full w-full"
      />

      {/* Floating Top Search Bar */}
      <div className="absolute top-4 left-1/2 -translate-x-1/2 z-20 w-full max-w-md px-4">
        <div className="relative shadow-lg">
          <Search className="absolute left-4 top-3 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Map by Location, Target ID, or Emergency Type..."
            className="pl-11 h-10 bg-card/95 border-border text-foreground rounded-xl shadow-lg"
          />
        </div>
      </div>

      {/* Floating Right Data Filters Card */}
      <div className="absolute top-4 right-4 z-20 w-80">
        <Card className="bg-card/95 border-border shadow-lg rounded-lg overflow-hidden">
          <CardContent className="p-4 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-border">
              <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-[#1F91A8]" /> DATA FILTERS
              </span>
              <Badge variant="outline" className="text-[10px] font-medium text-[#1F91A8] border-secondary/30">
                {filteredIncidents.length} ALERTS • {responders.length} UNITS
              </Badge>
            </div>

            {/* Date Range Dropdown */}
            <div className="space-y-1">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">DATE RANGE</label>
              <Select value={dateRange} onChange={(e) => setDateRange(e.target.value)} className="h-9 text-xs">
                <option value="Last 24 Hours">Last 24 Hours</option>
                <option value="Last 7 Days">Last 7 Days</option>
                <option value="Last 30 Days">Last 30 Days</option>
                <option value="All Time">All Time</option>
              </Select>
            </div>

            {/* Category Pills */}
            <div className="space-y-1.5">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">CATEGORY</label>
              <div className="flex flex-wrap gap-1.5">
                {['ALL', 'CRIME', 'MEDICAL', 'ACCIDENT', 'DISASTER'].map((cat) => (
                  <button
                    key={cat}
                    onClick={() => setSelectedCategory(cat)}
                    className={`px-2.5 py-1 text-[11px] font-semibold rounded-md transition-all ${
                      selectedCategory === cat
                        ? 'bg-[#1F91A8] text-white shadow-sm'
                        : 'bg-muted text-muted-foreground hover:bg-accent hover:text-foreground'
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Status Checkboxes */}
            <div className="space-y-2 pt-1 border-t border-border">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">DISPLAY OPTIONS</label>
              <div className="space-y-1.5 text-sm text-foreground">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={activeOpsOnly}
                    onChange={(e) => setActiveOpsOnly(e.target.checked)}
                    className="rounded border-border text-[#1F91A8] focus:ring-[#1F91A8]"
                  />
                  <span>Active Operations</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={showRespondersOnMap}
                    onChange={(e) => setShowRespondersOnMap(e.target.checked)}
                    className="rounded border-border text-[#1F91A8] focus:ring-[#1F91A8]"
                  />
                  <span>Show Responders On Duty</span>
                </label>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Floating Bottom Selected Marker Details Card */}
      {selectedIncident && (
        <div className="absolute bottom-6 left-6 z-20 max-w-sm w-full">
          <Card className="bg-card/95 backdrop-blur-md border-border shadow-lg rounded-lg p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-destructive flex items-center gap-1.5">
                <AlertTriangle className="h-4 w-4" /> Incident #{selectedIncident.id}
              </span>
              <Badge variant="outline" className="text-[10px] font-medium text-[#F2A540] border-[#F2A540]/40">
                {selectedIncident.status}
              </Badge>
            </div>

            <div className="flex items-center justify-between text-[11px] font-mono">
              <span className="text-muted-foreground">Alert Source:</span>
              <span className={`px-2 py-0.5 rounded font-bold ${selectedIsP2P ? 'bg-amber-500/20 text-amber-500 border border-amber-500/40' : 'bg-emerald-500/20 text-emerald-500 border border-emerald-500/40'}`}>
                {selectedIsP2P ? 'P2P RECEIVED (BLE MESH)' : 'DATABASE (ONLINE)'}
              </span>
            </div>

            {selectedEtaInfo && (
              <div className="flex items-center justify-between text-[11px] font-mono bg-muted/60 p-1.5 rounded border border-border">
                <span className="text-muted-foreground">Dist & Response ETA:</span>
                <span className="font-bold text-[#1F91A8]">
                  {selectedEtaInfo.distText} ({selectedEtaInfo.etaText})
                </span>
              </div>
            )}

            <div className="text-xs text-foreground font-medium">
              Target ID: {typeof selectedIncident.target_id === 'string' ? selectedIncident.target_id : selectedIncident.target_id?.raw}
            </div>
            <div className="flex items-center justify-between text-[11px] font-medium text-muted-foreground pt-1 border-t border-border">
              <span>Hops: {selectedIncident.hop_count}</span>
              <span>Channel: {selectedIncident.channel}</span>
            </div>
            <button
              onClick={() => setSelectedIncident(null)}
              className="w-full py-1 text-xs text-muted-foreground hover:text-foreground underline underline-offset-2 text-center"
            >
              Close Details
            </button>
          </Card>
        </div>
      )}
    </div>
  );
};
