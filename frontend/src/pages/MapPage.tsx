import React, { useState } from 'react';
import { useIncidents } from '@/hooks/useIncidents';
import { IncidentMap } from '@/components/map/IncidentMap';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/select';
import { Search, Filter, AlertTriangle } from 'lucide-react';
import type { Incident } from '@/types/api';

export const MapPage: React.FC = () => {
  const { incidents } = useIncidents();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState('Last 30 Days');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [pendingReviewOnly, setPendingReviewOnly] = useState(true);

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

  return (
    <div className="relative h-[calc(100vh-6rem)] w-full rounded-2xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-2xl">
      {/* Interactive Map Base */}
      <IncidentMap
        incidents={filteredIncidents}
        selectedIncidentId={selectedIncident?.id}
        onSelectIncident={(inc) => setSelectedIncident(inc)}
        className="h-full w-full"
      />

      {/* Floating Top Search Bar */}
      <div className="absolute top-4 left-1/2 -translate-x-1/2 z-20 w-full max-w-md px-4">
        <div className="relative shadow-xl">
          <Search className="absolute left-4 top-3 h-4 w-4 text-slate-400" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Map by Location, Target ID, or Emergency Type..."
            className="pl-11 h-10 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border-slate-200 dark:border-slate-700 text-slate-900 dark:text-slate-100 rounded-full shadow-2xl font-sans"
          />
        </div>
      </div>

      {/* Floating Right Data Filters Card (Matching Mockup Screenshot) */}
      <div className="absolute top-4 right-4 z-20 w-80">
        <Card className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-md border-slate-200 dark:border-slate-800 shadow-2xl rounded-2xl overflow-hidden">
          <CardContent className="p-4 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-slate-200 dark:border-slate-800">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-teal-500" /> DATA FILTERS
              </span>
              <Badge variant="outline" className="text-[10px] font-mono border-teal-500/30 text-teal-600 dark:text-teal-400">
                {filteredIncidents.length} LOCATIONS
              </Badge>
            </div>

            {/* Date Range Dropdown */}
            <div className="space-y-1">
              <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">DATE RANGE</label>
              <Select value={dateRange} onChange={(e) => setDateRange(e.target.value)} className="h-9 text-xs">
                <option value="Last 24 Hours">Last 24 Hours</option>
                <option value="Last 7 Days">Last 7 Days</option>
                <option value="Last 30 Days">Last 30 Days</option>
                <option value="All Time">All Time</option>
              </Select>
            </div>

            {/* Category Pills */}
            <div className="space-y-1.5">
              <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">CATEGORY</label>
              <div className="flex flex-wrap gap-1.5">
                {['ALL', 'CRIME', 'MEDICAL', 'ACCIDENT', 'DISASTER'].map((cat) => (
                  <button
                    key={cat}
                    onClick={() => setSelectedCategory(cat)}
                    className={`px-2.5 py-1 text-[11px] font-semibold rounded-md transition-all ${
                      selectedCategory === cat
                        ? 'bg-teal-500 text-white shadow-sm'
                        : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Status Checkboxes */}
            <div className="space-y-2 pt-1 border-t border-slate-200 dark:border-slate-800">
              <label className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">STATUS</label>
              <div className="space-y-1.5 text-xs text-slate-700 dark:text-slate-300">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={activeOpsOnly}
                    onChange={(e) => setActiveOpsOnly(e.target.checked)}
                    className="rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                  />
                  <span>Active Operations</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={pendingReviewOnly}
                    onChange={(e) => setPendingReviewOnly(e.target.checked)}
                    className="rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                  />
                  <span>Pending Review</span>
                </label>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Floating Bottom Selected Marker Details Card */}
      {selectedIncident && (
        <div className="absolute bottom-6 left-6 z-20 max-w-sm w-full">
          <Card className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-md border-red-500/30 shadow-2xl rounded-xl p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-red-500 flex items-center gap-1.5">
                <AlertTriangle className="h-4 w-4" /> Incident #{selectedIncident.id}
              </span>
              <Badge variant="outline" className="text-[10px] font-mono text-amber-500 border-amber-500/30">
                {selectedIncident.status}
              </Badge>
            </div>
            <div className="text-xs text-slate-700 dark:text-slate-300 font-medium">
              Target ID: {typeof selectedIncident.target_id === 'string' ? selectedIncident.target_id : selectedIncident.target_id?.raw}
            </div>
            <div className="flex items-center justify-between text-[11px] font-mono text-slate-500 dark:text-slate-400 pt-1 border-t border-slate-200 dark:border-slate-800">
              <span>Hops: {selectedIncident.hop_count}</span>
              <span>Channel: {selectedIncident.channel}</span>
            </div>
            <button
              onClick={() => setSelectedIncident(null)}
              className="w-full py-1 text-xs text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 underline font-sans text-center"
            >
              Close Details
            </button>
          </Card>
        </div>
      )}
    </div>
  );
};
