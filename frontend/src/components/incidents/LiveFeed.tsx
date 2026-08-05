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
    <Card className="h-full flex flex-col border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 shadow-sm">
      <CardHeader className="py-4 border-b border-slate-200 dark:border-slate-800/80 flex flex-row items-center justify-between">
        <CardTitle className="text-sm font-semibold flex items-center gap-2 text-slate-900 dark:text-slate-100">
          <Radio className={`h-4 w-4 ${isConnected ? 'text-emerald-500 dark:text-emerald-400 animate-pulse' : 'text-slate-400'}`} />
          Telemetry Live Feed
        </CardTitle>
        <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400">{incidents.length} events</span>
      </CardHeader>

      <CardContent className="flex-1 overflow-y-auto p-4 space-y-3 font-mono text-xs">
        {incidents.length === 0 ? (
          <div className="text-slate-400 dark:text-slate-500 text-center py-12 text-xs">
            Listening for P2P mesh and uplink packets...
          </div>
        ) : (
          incidents.map((inc, i) => (
            <div
              key={inc.packet_id || i}
              className="p-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/70 space-y-2 hover:border-slate-300 dark:hover:border-slate-700 transition-colors"
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

                <div className="flex items-center gap-2 text-[10px] text-slate-500 dark:text-slate-400">
                  <span className="flex items-center gap-1">
                    <Hop className="h-3 w-3 text-sky-500 dark:text-sky-400" />
                    Hops: {inc.hop_count}
                  </span>
                  <span className="flex items-center gap-1">
                    <Layers className="h-3 w-3 text-amber-500 dark:text-amber-400" />
                    {inc.channel}
                  </span>
                </div>
              </div>

              <div className="text-[11px] text-slate-700 dark:text-slate-300 truncate">
                Target: {typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || 'N/A'}
              </div>

              <div className="flex justify-between text-[10px] text-slate-400 dark:text-slate-500 pt-1 border-t border-slate-200 dark:border-slate-900">
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
