import React from 'react';
import type { Incident } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Radio, Hop, Layers } from 'lucide-react';

interface LiveFeedProps {
  incidents: Incident[];
  isConnected: boolean;
}

export const LiveFeed: React.FC<LiveFeedProps> = ({ incidents, isConnected }) => {
  return (
    <Card className="h-full flex flex-col border-border bg-card shadow-sm">
      <CardHeader className="py-4 border-b border-border flex flex-row items-center justify-between">
        <CardTitle className="text-sm font-semibold flex items-center gap-2 text-card-foreground">
          <Radio className={`h-4 w-4 ${isConnected ? 'text-secondary' : 'text-muted-foreground'}`} />
          Telemetry Live Feed
        </CardTitle>
        <span className="text-[11px] font-medium text-muted-foreground">{incidents.length} events</span>
      </CardHeader>

      <CardContent className="flex-1 overflow-y-auto p-4 space-y-3 text-xs">
        {incidents.length === 0 ? (
          <div className="text-muted-foreground text-center py-12 text-xs">
            Listening for P2P mesh and uplink packets...
          </div>
        ) : (
          incidents.map((inc, i) => (
            <div
              key={inc.incident_uuid || inc.packet_uuid || i}
              className="p-3 rounded-lg border border-border bg-muted/60 space-y-2 hover:border-secondary/50 transition-colors"
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
                <span>{new Date(inc.received_at || inc.event_timestamp || Date.now()).toLocaleTimeString()}</span>
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
};
