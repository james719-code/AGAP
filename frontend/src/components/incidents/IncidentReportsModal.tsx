import React, { useEffect, useState } from 'react';
import { FileText, AlertCircle, MapPin, Clock, ShieldAlert, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { apiService } from '@/services/api';
import type { IncidentReport } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { hostedSecondaryRefreshMs, usesHostedApi } from '@/services/backend';
import { scheduleVisibleRefresh } from '@/hooks/useVisibleRefresh';

interface IncidentReportsModalProps {
  isOpen: boolean;
  onClose: () => void;
  incidentId: number;
  incidentUuid?: string;
}

export const IncidentReportsModal: React.FC<IncidentReportsModalProps> = ({
  isOpen,
  onClose,
  incidentId,
  incidentUuid,
}) => {
  const { lastIncidentReportUpdate } = useWebSocket();
  const [reports, setReports] = useState<IncidentReport[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen || !incidentId) return;

    let cancelled = false;

    let pending = false;
    const run = async (silent = false) => {
      if (pending) return;
      pending = true;
      if (!silent) {
        setIsLoading(true);
        setError(null);
      }

      try {
        const res = await apiService.getIncidentReports(incidentId);
        if (!cancelled) {
          setReports(res.reports || []);
          setError(null);
        }
      } catch (err) {
        if (!cancelled && !silent) setError(err instanceof Error ? err.message : 'Failed to fetch incident reports');
        return false;
      } finally {
        pending = false;
        if (!cancelled && !silent) setIsLoading(false);
      }
    };

    void run();
    const stop = usesHostedApi ? scheduleVisibleRefresh(() => run(true), hostedSecondaryRefreshMs) : undefined;

    return () => {
      cancelled = true;
      stop?.();
    };
  }, [isOpen, incidentId, lastIncidentReportUpdate]);

  if (!isOpen) return null;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }} contentClassName="max-w-2xl">
      <DialogHeader className="border-b border-border pb-3">
        <DialogTitle className="flex items-center gap-2">
          <FileText className="size-5 text-primary" aria-hidden="true" /> Incident reports
        </DialogTitle>
        <DialogDescription className="wrap-anywhere">
          Incident #{incidentId} {incidentUuid ? '· ' + incidentUuid : ''}
        </DialogDescription>
      </DialogHeader>

      {/* Content */}
      {isLoading ? (
        <div className="py-12 text-center text-sm text-muted-foreground flex flex-col items-center justify-center gap-2">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-[#1F91A8] border-t-transparent" />
          Loading reports...
        </div>
      ) : error ? (
        <div className="p-4 rounded-md border border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400 text-xs flex items-center gap-2">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      ) : reports.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground">
          No reports are attached to this incident.
        </div>
      ) : (
        <div className="space-y-3 max-h-96 overflow-y-auto pr-1">
          {reports.map((report) => (
            <div
              key={report.id}
              className="space-y-2.5 border-b border-[#E7E3DC] py-4 first:pt-0 last:border-b-0 last:pb-0 dark:border-[#26292C]"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Badge variant={report.detail_state === 'FULL' ? 'success' : 'outline'} className="rounded-md text-[10px] font-semibold">
                    {report.detail_state} REPORT
                  </Badge>
                  <Badge variant="secondary" className="rounded-md text-[10px] font-semibold font-mono">
                    TOKEN: {report.transport_token || report.report_uuid.substring(0, 8)}
                  </Badge>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Clock className="h-3.5 w-3.5" />
                  <span>{new Date(report.observed_at || report.created_at).toLocaleString()}</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  <span className="text-muted-foreground">Type: </span>
                  <span className="font-semibold text-foreground">{report.emergency_type}</span>
                  {report.subtype ? <span className="text-muted-foreground"> ({report.subtype})</span> : null}
                </div>
                <div>
                  <span className="text-muted-foreground">Subject: </span>
                  <span className="font-semibold text-foreground">{report.subject_relation}</span>
                </div>
                <div>
                  <span className="text-muted-foreground">Source: </span>
                  <span className="font-semibold text-foreground">{report.location_source} ({report.origin_channel})</span>
                </div>
                <div>
                  <span className="text-muted-foreground">Danger: </span>
                  {report.immediate_danger ? (
                    <span className="font-bold text-red-600 dark:text-red-400 flex items-center gap-1 inline-flex">
                      <ShieldAlert className="h-3 w-3" /> IMMEDIATE DANGER
                    </span>
                  ) : (
                    <span className="text-muted-foreground flex items-center gap-1 inline-flex">
                      <CheckCircle className="h-3 w-3 text-[#1F91A8]" /> Normal
                    </span>
                  )}
                </div>
              </div>

              {report.latitude !== undefined && report.longitude !== undefined && (
                <div className="flex items-center gap-1.5 text-xs text-foreground">
                  <MapPin className="h-3.5 w-3.5 text-[#D15033] shrink-0" />
                  <span>
                    {report.latitude.toFixed(6)}, {report.longitude.toFixed(6)}
                    {report.gps_accuracy_meters ? ` (±${Math.round(report.gps_accuracy_meters)}m)` : ''}
                  </span>
                  {report.selected_barangay_code && (
                    <span className="text-muted-foreground text-[11px]"> • {report.selected_barangay_code}</span>
                  )}
                </div>
              )}

              {report.description && (
                <div className="border-l-2 border-[#1F91A8]/40 pl-3 text-xs text-foreground">
                  "{report.description}"
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center justify-end pt-2 border-t border-border">
        <Button
          variant="outline"
          size="sm"
          onClick={onClose}
          className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent text-xs"
        >
          Close
        </Button>
      </div>
    </Dialog>
  );
};
