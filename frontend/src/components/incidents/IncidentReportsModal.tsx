import React, { useEffect, useState } from 'react';
import { X, FileText, AlertCircle, MapPin, Clock, ShieldAlert, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { apiService } from '@/services/api';
import type { IncidentReport } from '@/types/api';

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
  const [reports, setReports] = useState<IncidentReport[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen || !incidentId) return;

    setIsLoading(true);
    setError(null);

    apiService
      .getIncidentReports(incidentId)
      .then((res) => {
        setReports(res.reports || []);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Failed to fetch incident reports');
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [isOpen, incidentId]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4">
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl max-w-2xl w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="bg-blue-500/10 p-2 rounded-xl border border-blue-500/20 text-blue-500">
              <FileText className="h-5 w-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-base text-slate-900 dark:text-slate-100">
                CITIZEN INCIDENT REPORTS
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 font-mono">
                Incident #{incidentId} {incidentUuid ? `• ${incidentUuid}` : ''}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400 flex flex-col items-center justify-center gap-2">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-500 border-t-transparent" />
            Loading normalized reports...
          </div>
        ) : error ? (
          <div className="p-4 rounded-xl border border-red-500/20 bg-red-500/10 text-red-500 text-xs flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        ) : reports.length === 0 ? (
          <div className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
            No citizen reports attached to this incident yet.
          </div>
        ) : (
          <div className="space-y-3 max-h-96 overflow-y-auto pr-1">
            {reports.map((report) => (
              <div
                key={report.id}
                className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 space-y-2.5"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Badge variant={report.detail_state === 'FULL' ? 'success' : 'outline'} className="text-[10px]">
                      {report.detail_state} REPORT
                    </Badge>
                    <Badge variant="secondary" className="text-[10px] font-mono">
                      TOKEN: {report.transport_token || report.report_uuid.substring(0, 8)}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                    <Clock className="h-3.5 w-3.5" />
                    <span>{new Date(report.observed_at || report.created_at).toLocaleString()}</span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-slate-400">Type: </span>
                    <span className="font-semibold text-slate-900 dark:text-slate-100">{report.emergency_type}</span>
                    {report.subtype ? <span className="text-slate-500"> ({report.subtype})</span> : null}
                  </div>
                  <div>
                    <span className="text-slate-400">Subject: </span>
                    <span className="font-semibold text-slate-900 dark:text-slate-100">{report.subject_relation}</span>
                  </div>
                  <div>
                    <span className="text-slate-400">Source: </span>
                    <span className="font-semibold text-slate-900 dark:text-slate-100">{report.location_source} ({report.origin_channel})</span>
                  </div>
                  <div>
                    <span className="text-slate-400">Danger: </span>
                    {report.immediate_danger ? (
                      <span className="font-bold text-red-500 flex items-center gap-1 inline-flex">
                        <ShieldAlert className="h-3 w-3" /> IMMEDIATE DANGER
                      </span>
                    ) : (
                      <span className="text-slate-500 flex items-center gap-1 inline-flex">
                        <CheckCircle className="h-3 w-3 text-teal-500" /> NORMAL
                      </span>
                    )}
                  </div>
                </div>

                {report.latitude !== undefined && report.longitude !== undefined && (
                  <div className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
                    <MapPin className="h-3.5 w-3.5 text-red-500 shrink-0" />
                    <span>
                      {report.latitude.toFixed(6)}, {report.longitude.toFixed(6)}
                      {report.gps_accuracy_meters ? ` (±${Math.round(report.gps_accuracy_meters)}m)` : ''}
                    </span>
                    {report.selected_barangay_code && (
                      <span className="text-slate-400 text-[11px]"> • {report.selected_barangay_code}</span>
                    )}
                  </div>
                )}

                {report.description && (
                  <div className="text-xs bg-white dark:bg-slate-900 p-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300">
                    "{report.description}"
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        <div className="flex items-center justify-end pt-2 border-t border-slate-200 dark:border-slate-800">
          <Button variant="outline" size="sm" onClick={onClose} className="text-xs">
            Close
          </Button>
        </div>
      </div>
    </div>
  );
};
