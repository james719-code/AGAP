import React, { useEffect, useState } from 'react';
import { X, FileText, AlertCircle, MapPin, Clock, ShieldAlert, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { apiService } from '@/services/api';
import type { IncidentReport } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';

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

    const run = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const res = await apiService.getIncidentReports(incidentId);
        if (!cancelled) setReports(res.reports || []);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to fetch incident reports');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [isOpen, incidentId, lastIncidentReportUpdate]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm p-4">
      <div className="bg-white dark:bg-[#161A1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] max-w-2xl w-full p-6 shadow-[0_1px_2px_rgba(10,10,10,0.04)] space-y-5 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#E7E3DC] dark:border-[#26292C]">
          <div className="flex items-center gap-2.5">
            <div className="bg-[#1F91A8]/10 p-2 rounded-[2px] border border-[#1F91A8]/20 text-[#1F91A8]">
              <FileText className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white">Incident Reports</h3>
              <p className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] font-mono">
                Incident #{incidentId} {incidentUuid ? `• ${incidentUuid}` : ''}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-[2px] text-[#6B6864] dark:text-[#9C9994] hover:text-[#0A0A0A] dark:hover:text-white hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="py-12 text-center text-sm text-[#5C5A56] dark:text-[#B9B6B0] flex flex-col items-center justify-center gap-2">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-[#1F91A8] border-t-transparent" />
            Loading reports...
          </div>
        ) : error ? (
          <div className="p-4 rounded-[2px] border border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400 text-xs flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        ) : reports.length === 0 ? (
          <div className="py-8 text-center text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
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
                    <Badge variant={report.detail_state === 'FULL' ? 'success' : 'outline'} className="rounded-[2px] text-[10px] font-semibold">
                      {report.detail_state} REPORT
                    </Badge>
                    <Badge variant="secondary" className="rounded-[2px] text-[10px] font-semibold font-mono">
                      TOKEN: {report.transport_token || report.report_uuid.substring(0, 8)}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
                    <Clock className="h-3.5 w-3.5" />
                    <span>{new Date(report.observed_at || report.created_at).toLocaleString()}</span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Type: </span>
                    <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">{report.emergency_type}</span>
                    {report.subtype ? <span className="text-[#5C5A56] dark:text-[#B9B6B0]"> ({report.subtype})</span> : null}
                  </div>
                  <div>
                    <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Subject: </span>
                    <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">{report.subject_relation}</span>
                  </div>
                  <div>
                    <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Source: </span>
                    <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">{report.location_source} ({report.origin_channel})</span>
                  </div>
                  <div>
                    <span className="text-[#5C5A56] dark:text-[#B9B6B0]">Danger: </span>
                    {report.immediate_danger ? (
                      <span className="font-bold text-red-600 dark:text-red-400 flex items-center gap-1 inline-flex">
                        <ShieldAlert className="h-3 w-3" /> IMMEDIATE DANGER
                      </span>
                    ) : (
                      <span className="text-[#5C5A56] dark:text-[#B9B6B0] flex items-center gap-1 inline-flex">
                        <CheckCircle className="h-3 w-3 text-[#1F91A8]" /> Normal
                      </span>
                    )}
                  </div>
                </div>

                {report.latitude !== undefined && report.longitude !== undefined && (
                  <div className="flex items-center gap-1.5 text-xs text-[#0A0A0A] dark:text-[#F2F0EC]">
                    <MapPin className="h-3.5 w-3.5 text-[#D15033] shrink-0" />
                    <span>
                      {report.latitude.toFixed(6)}, {report.longitude.toFixed(6)}
                      {report.gps_accuracy_meters ? ` (±${Math.round(report.gps_accuracy_meters)}m)` : ''}
                    </span>
                    {report.selected_barangay_code && (
                      <span className="text-[#5C5A56] dark:text-[#B9B6B0] text-[11px]"> • {report.selected_barangay_code}</span>
                    )}
                  </div>
                )}

                {report.description && (
                  <div className="border-l-2 border-[#1F91A8]/40 pl-3 text-xs text-[#0A0A0A] dark:text-[#F2F0EC]">
                    "{report.description}"
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        <div className="flex items-center justify-end pt-2 border-t border-[#E7E3DC] dark:border-[#26292C]">
          <Button
            variant="outline"
            size="sm"
            onClick={onClose}
            className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] text-xs"
          >
            Close
          </Button>
        </div>
      </div>
    </div>
  );
};
