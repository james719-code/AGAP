import React, { useState, useEffect } from 'react';
import type { Incident } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Hop, Layers, ChevronLeft, ChevronRight } from 'lucide-react';
import { incidentIdentity } from '@/lib/incidentIdentity';

interface LiveFeedProps {
  incidents: Incident[];
  isConnected: boolean;
}

const ITEMS_PER_PAGE = 4;

export const LiveFeed: React.FC<LiveFeedProps> = ({ incidents, isConnected }) => {
  const [currentPage, setCurrentPage] = useState(1);

  // Reset to first page when incident list changes
  useEffect(() => {
    setCurrentPage(1);
  }, [incidents.length]);

  const totalPages = Math.ceil(incidents.length / ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedIncidents = incidents.slice(startIndex, startIndex + ITEMS_PER_PAGE);

  const handlePrevious = () => {
    setCurrentPage((prev) => Math.max(prev - 1, 1));
  };

  const handleNext = () => {
    setCurrentPage((prev) => Math.min(prev + 1, totalPages));
  };

  return (
    <div className="h-full flex flex-col">
      <div className="flex-1 overflow-y-auto py-4 space-y-3 text-xs border-t border-border">
        {paginatedIncidents.length === 0 ? (
          <div className="text-muted-foreground text-center py-12 text-xs">
            {isConnected ? 'No recent incident activity.' : 'Connecting to live updates...'}
          </div>
        ) : (
          paginatedIncidents.map((inc) => (
            <div
              key={incidentIdentity(inc)}
              className="p-3 rounded-md border border-border bg-muted/60 space-y-2 hover:border-secondary/50 transition-colors"
            >
              <div className="flex items-center justify-between">
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

                <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Hop className="h-3 w-3 text-secondary" />
                    Hops: {inc.hop_count}
                  </span>
                  <span className="flex items-center gap-1">
                    <Layers className="h-3 w-3 text-[#F2A540]" />
                    {inc.channel}
                  </span>
                </div>
              </div>

              <div className="text-[11px] text-foreground truncate">
                Target: {typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A'}
              </div>

              <div className="flex justify-between text-[10px] text-muted-foreground pt-1 border-t border-border">
                <span>Lat: {inc.latitude?.toFixed(4)}, Lon: {inc.longitude?.toFixed(4)}</span>
                <span>
                  {inc.received_at || inc.event_timestamp
                    ? new Date(inc.received_at || inc.event_timestamp!).toLocaleTimeString()
                    : '—'}
                </span>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Pagination Controls – only show if more than one page */}
      {totalPages > 1 && (
        <div className="px-0 py-3 border-t border-border flex items-center justify-between text-xs text-muted-foreground">
          <span className="text-[10px] font-medium">
            Page {currentPage} of {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={handlePrevious}
              disabled={currentPage === 1}
              className="p-1.5 rounded-md border border-border bg-card hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Previous page"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={handleNext}
              disabled={currentPage === totalPages}
              className="p-1.5 rounded-md border border-border bg-card hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Next page"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
