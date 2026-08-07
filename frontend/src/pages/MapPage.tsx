import React, { useState } from "react";
import { useIncidents } from "@/hooks/useIncidents";
import { IncidentMap } from "@/components/map/IncidentMap";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Select } from "@/components/ui/select";
import { Search, Filter, AlertTriangle } from "lucide-react";
import type { Incident } from "@/types/api";

export const MapPage: React.FC = () => {
  const { incidents } = useIncidents();
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(
    null,
  );
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState("Last 30 Days");
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [activeOpsOnly, setActiveOpsOnly] = useState(true);
  const [pendingReviewOnly, setPendingReviewOnly] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);

  // Filter incidents based on floating search & category options
  const filteredIncidents = incidents.filter((inc) => {
    if (selectedCategory !== "ALL" && inc.incident_type !== selectedCategory)
      return false;
    if (activeOpsOnly && inc.status === "RESOLVED") return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const targetStr =
        typeof inc.target_id === "string"
          ? inc.target_id
          : inc.target_id?.raw || "";
      return (
        inc.incident_type.toLowerCase().includes(q) ||
        targetStr.toLowerCase().includes(q) ||
        inc.id.toString().includes(q)
      );
    }
    return true;
  });

  return (
    <div className="relative h-[calc(100vh-7rem)] lg:h-[calc(100vh-8rem)] w-full rounded-lg overflow-hidden border border-border shadow-sm">
      {/* Interactive Map Base */}
      <IncidentMap
        incidents={filteredIncidents}
        selectedIncidentId={selectedIncident?.id}
        onSelectIncident={(inc) => setSelectedIncident(inc)}
        className="relative z-0 h-full w-full"
      />

      {/* Floating Search Bar and Filter Trigger */}
      <div className="absolute top-4 left-1/2 z-30 flex w-full max-w-lg -translate-x-1/2 items-start gap-2 px-4">
        <div className="relative flex-1 shadow-lg">
          <Search className="absolute left-4 top-3 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Map by Location, Target ID, or Emergency Type..."
            className="pl-11 h-10 bg-card/95 border-border text-foreground rounded-xl shadow-lg"
          />
        </div>

        <button
          type="button"
          onClick={() => setFiltersOpen((open) => !open)}
          aria-label={filtersOpen ? "Close map filters" : "Open map filters"}
          aria-expanded={filtersOpen}
          aria-controls="map-data-filters"
          aria-haspopup="dialog"
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border shadow-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#1F91A8] focus-visible:ring-offset-2 ${
            filtersOpen
              ? "border-[#1F91A8] bg-[#1F91A8] text-white"
              : "border-border bg-card/95 text-muted-foreground hover:bg-accent hover:text-foreground"
          }`}
        >
          <Filter className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      {/* Floating Data Filters Popover */}
      {filtersOpen && (
        <div
          id="map-data-filters"
          role="dialog"
          aria-label="Map data filters"
          className="absolute top-16 left-1/2 z-20 flex w-full max-w-lg -translate-x-1/2 justify-end px-4"
        >
          <Card className="w-80 max-w-full bg-card/95 border-border shadow-lg rounded-lg overflow-hidden">
          <CardContent className="p-4 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-border">
              <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-[#1F91A8]" /> DATA FILTERS
              </span>
              <Badge
                variant="outline"
                className="text-[10px] font-medium text-[#1F91A8] border-secondary/30"
              >
                {filteredIncidents.length} LOCATIONS
              </Badge>
            </div>

            {/* Date Range Dropdown */}
            <div className="space-y-1">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">
                DATE RANGE
              </label>
              <Select
                value={dateRange}
                onChange={(e) => setDateRange(e.target.value)}
                className="h-9 text-xs"
              >
                <option value="Last 24 Hours">Last 24 Hours</option>
                <option value="Last 7 Days">Last 7 Days</option>
                <option value="Last 30 Days">Last 30 Days</option>
                <option value="All Time">All Time</option>
              </Select>
            </div>

            {/* Category Pills */}
            <div className="space-y-1.5">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">
                CATEGORY
              </label>
              <div className="flex flex-wrap gap-1.5">
                {["ALL", "CRIME", "MEDICAL", "ACCIDENT", "DISASTER"].map(
                  (cat) => (
                    <button
                      type="button"
                      key={cat}
                      onClick={() => setSelectedCategory(cat)}
                      className={`px-2.5 py-1 text-[11px] font-semibold rounded-md transition-all ${
                        selectedCategory === cat
                          ? "bg-[#1F91A8] text-white shadow-sm"
                          : "bg-muted text-muted-foreground hover:bg-accent hover:text-foreground"
                      }`}
                    >
                      {cat}
                    </button>
                  ),
                )}
              </div>
            </div>

            {/* Status Checkboxes */}
            <div className="space-y-2 pt-1 border-t border-border">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">
                STATUS
              </label>
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
                    checked={pendingReviewOnly}
                    onChange={(e) => setPendingReviewOnly(e.target.checked)}
                    className="rounded border-border text-[#1F91A8] focus:ring-[#1F91A8]"
                  />
                  <span>Pending Review</span>
                </label>
              </div>
            </div>
          </CardContent>
          </Card>
        </div>
      )}

      {/* Floating Bottom Selected Marker Details Card */}
      {selectedIncident && (
        <div className="absolute bottom-6 left-6 z-20 max-w-sm w-full">
          <Card className="bg-card/95 backdrop-blur-md border-border shadow-lg rounded-lg p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-destructive flex items-center gap-1.5">
                <AlertTriangle className="h-4 w-4" /> Incident #
                {selectedIncident.id}
              </span>
              <Badge
                variant="outline"
                className="text-[10px] font-medium text-[#F2A540] border-[#F2A540]/40"
              >
                {selectedIncident.status}
              </Badge>
            </div>
            <div className="text-xs text-foreground font-medium">
              Target ID:{" "}
              {typeof selectedIncident.target_id === "string"
                ? selectedIncident.target_id
                : selectedIncident.target_id?.raw}
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
