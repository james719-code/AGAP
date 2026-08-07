import React, { useEffect, useMemo, useState } from 'react';
import { Activity, Clock, FileJson, FileSpreadsheet, RadioTower, ShieldCheck, Siren, Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useAgencies } from '@/hooks/useAgencies';
import { useEmergencyTypes } from '@/hooks/useEmergencyTypes';
import { useIncidents } from '@/hooks/useIncidents';
import { useResponders } from '@/hooks/useResponders';
import { apiService } from '@/services/api';
import type { IncidentDispatch, IncidentStatusHistory } from '@/types/api';
import { exportIncidentsToCSV, exportIncidentsToJSON } from '@/lib/exportUtils';
import { formatTargetLocation } from '@/lib/targetIdFormatter';

type IncidentTimelineData = {
  dispatches: IncidentDispatch[];
  history: IncidentStatusHistory[];
};

const averageMinutes = (values: number[]) => {
  const usable = values.filter((value) => Number.isFinite(value) && value >= 0);
  if (usable.length === 0) return 0;
  return usable.reduce((sum, value) => sum + value, 0) / usable.length / 60000;
};

const msBetween = (start?: string, end?: string) => {
  if (!start || !end) return null;
  const startMs = new Date(start).getTime();
  const endMs = new Date(end).getTime();
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs < startMs) return null;
  return endMs - startMs;
};

export const ReportsPage: React.FC = () => {
  const { incidents } = useIncidents();
  const { responders } = useResponders();
  const { agencies } = useAgencies();
  const { emergencyTypes } = useEmergencyTypes(false);
  const [timelineByIncident, setTimelineByIncident] = useState<Record<number, IncidentTimelineData>>({});
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelineError, setTimelineError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const activeIncidents = incidents.slice(0, 100);

    if (activeIncidents.length === 0) {
      setTimelineByIncident({});
      setTimelineError(null);
      return;
    }

    setTimelineLoading(true);
    setTimelineError(null);

    Promise.all(
      activeIncidents.map(async (incident) => {
        const [dispatches, history] = await Promise.all([
          apiService.getIncidentDispatches(incident.id).catch(() => [] as IncidentDispatch[]),
          apiService.getIncidentStatusHistory(incident.id).catch(() => [] as IncidentStatusHistory[]),
        ]);
        return [incident.id, { dispatches, history }] as const;
      })
    )
      .then((entries) => {
        if (!cancelled) {
          setTimelineByIncident(Object.fromEntries(entries));
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setTimelineByIncident({});
          setTimelineError(err instanceof Error ? err.message : 'Failed to load incident timeline data');
        }
      })
      .finally(() => {
        if (!cancelled) setTimelineLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [incidents]);

  const report = useMemo(() => {
    const total = incidents.length;
    const active = incidents.filter((i) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(i.status)).length;
    const ble = incidents.filter((i) => i.channel === 'BLE_MESH').length;
    const online = incidents.filter((i) => i.channel === 'ONLINE').length;
    const resolved = incidents.filter((i) => i.status === 'RESOLVED').length;
    const falseAlarm = incidents.filter((i) => i.status === 'FALSE_ALARM').length;
    const avgHops = total === 0 ? 0 : incidents.reduce((sum, item) => sum + (item.hop_count || 0), 0) / total;
    const bleRelayed = incidents.filter((i) => i.channel === 'BLE_MESH' && (i.hop_count || 0) > 0).length;
    const bleGatewayUplink = incidents.filter((i) => i.delivery_path === 'BLE_CLOUD_SHORTCUT').length;
    const byType = incidents.reduce<Record<string, number>>((acc, item) => {
      acc[item.incident_type] = (acc[item.incident_type] || 0) + 1;
      return acc;
    }, {});
    const byBarangay = incidents.reduce<Record<string, number>>((acc, item) => {
      const location = formatTargetLocation(item.target_id, item.latitude, item.longitude);
      acc[location.readable] = (acc[location.readable] || 0) + 1;
      return acc;
    }, {});
    const emergencyTypeByCode = new Map(emergencyTypes.map((item) => [item.code, item]));
    const agencyTypeToNames = agencies.reduce<Record<string, string[]>>((acc, agency) => {
      acc[agency.type] = [...(acc[agency.type] || []), agency.name];
      return acc;
    }, {});
    const byAgency = incidents.reduce<Record<string, number>>((acc, item) => {
      const targetAgencyType = emergencyTypeByCode.get(item.incident_type)?.target_agency_type || item.incident_type;
      const agencyNames = agencyTypeToNames[targetAgencyType] || [];
      const label = agencyNames[0] ? `${targetAgencyType} - ${agencyNames[0]}` : targetAgencyType;
      acc[label] = (acc[label] || 0) + 1;
      return acc;
    }, {});
    const allDispatches = Object.values(timelineByIncident).flatMap((item) => item.dispatches);
    const byResponderActivity = allDispatches.reduce<Record<string, number>>((acc, dispatch) => {
      const label = dispatch.responder_name || dispatch.unit_callsign || `Unit #${dispatch.unit_id}`;
      acc[label] = (acc[label] || 0) + 1;
      return acc;
    }, {});
    const byUnitDeployment = allDispatches.reduce<Record<string, number>>((acc, dispatch) => {
      const label = dispatch.unit_callsign || `Unit #${dispatch.unit_id}`;
      acc[label] = (acc[label] || 0) + 1;
      return acc;
    }, {});
    const ackDurations = incidents
      .map((incident) => {
        const ack = timelineByIncident[incident.id]?.history.find((event) => event.status === 'ACKNOWLEDGED');
        return msBetween(incident.received_at || incident.created_at, ack?.created_at);
      })
      .filter((value): value is number => value !== null);
    const dispatchDurations = incidents
      .map((incident) => {
        const firstDispatch = timelineByIncident[incident.id]?.dispatches[0];
        return msBetween(incident.received_at || incident.created_at, firstDispatch?.dispatched_at);
      })
      .filter((value): value is number => value !== null);
    const arrivalDurations = allDispatches
      .map((dispatch) => msBetween(dispatch.dispatched_at, dispatch.arrived_at))
      .filter((value): value is number => value !== null);
    const totalResponseDurations = incidents
      .map((incident) => {
        const completedDispatch = timelineByIncident[incident.id]?.dispatches.find((dispatch) => dispatch.completed_at);
        return msBetween(incident.received_at || incident.created_at, completedDispatch?.completed_at || (incident.status === 'RESOLVED' ? incident.updated_at : undefined));
      })
      .filter((value): value is number => value !== null);
    return {
      total,
      active,
      ble,
      online,
      resolved,
      falseAlarm,
      avgHops,
      bleRelayed,
      bleGatewayUplink,
      byType,
      byBarangay,
      byAgency,
      byResponderActivity,
      byUnitDeployment,
      avgAckMinutes: averageMinutes(ackDurations),
      avgDispatchMinutes: averageMinutes(dispatchDurations),
      avgArrivalMinutes: averageMinutes(arrivalDurations),
      avgTotalResponseMinutes: averageMinutes(totalResponseDurations),
      falseAlarmRate: total === 0 ? 0 : (falseAlarm / total) * 100,
      bleRelaySuccessRate: ble === 0 ? 0 : (bleRelayed / ble) * 100,
    };
  }, [agencies, emergencyTypes, incidents, timelineByIncident]);

  const typeRows = Object.entries(report.byType).sort((a, b) => b[1] - a[1]);
  const barangayRows = Object.entries(report.byBarangay).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const agencyRows = Object.entries(report.byAgency).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const responderRows = Object.entries(report.byResponderActivity).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const unitRows = Object.entries(report.byUnitDeployment).sort((a, b) => b[1] - a[1]).slice(0, 8);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Reports</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Operational Reports</h2>
          <p className="mt-1 text-sm text-muted-foreground">Incident volume, mesh usage, and exportable command-center records</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={() => exportIncidentsToCSV(incidents)}>
            <FileSpreadsheet className="mr-2 h-4 w-4" /> CSV
          </Button>
          <Button variant="outline" size="sm" onClick={() => exportIncidentsToJSON(incidents)}>
            <FileJson className="mr-2 h-4 w-4" /> JSON
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Total Incidents', value: report.total, icon: Siren, badge: 'All reports' },
          { label: 'Active Operations', value: report.active, icon: ShieldCheck, badge: 'Open' },
          { label: 'BLE Mesh Uplinks', value: report.ble, icon: RadioTower, badge: 'Fallback' },
          { label: 'Responder Units', value: responders.length, icon: Users, badge: 'Available' },
        ].map((item) => (
          <Card key={item.label}>
            <CardContent className="p-5">
              <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <span>{item.label}</span>
                <item.icon className="h-4 w-4 text-primary" />
              </div>
              <div className="mt-2 text-3xl font-extrabold tracking-tight text-foreground tabular-nums">{item.value}</div>
              <Badge variant="outline" className="mt-3 text-[10px]">{item.badge}</Badge>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Avg Ack Time', value: `${report.avgAckMinutes.toFixed(1)}m`, icon: Clock, badge: timelineLoading ? 'Loading' : 'History' },
          { label: 'Avg Dispatch Time', value: `${report.avgDispatchMinutes.toFixed(1)}m`, icon: Activity, badge: 'Dispatch' },
          { label: 'Avg Arrival Time', value: `${report.avgArrivalMinutes.toFixed(1)}m`, icon: ShieldCheck, badge: 'Arrival' },
          { label: 'Avg Total Response', value: `${report.avgTotalResponseMinutes.toFixed(1)}m`, icon: Siren, badge: 'Resolved' },
        ].map((item) => (
          <Card key={item.label}>
            <CardContent className="p-5">
              <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <span>{item.label}</span>
                <item.icon className="h-4 w-4 text-primary" />
              </div>
              <div className="mt-2 text-2xl font-extrabold tracking-tight text-foreground tabular-nums">{item.value}</div>
              <Badge variant={timelineError ? 'destructive' : 'outline'} className="mt-3 text-[10px]">
                {timelineError ? 'PARTIAL' : item.badge}
              </Badge>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardContent className="p-6">
            <div className="mb-4 flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">Incident Type Summary</h3>
              <Badge variant="info" className="text-[10px]">{report.total} TOTAL</Badge>
            </div>
            <div className="space-y-3">
              {(typeRows.length ? typeRows : [['NONE', 0]]).map(([type, count]) => (
                <div key={type} className="flex items-center justify-between rounded-md border border-border bg-muted/50 px-3 py-2">
                  <span className="text-sm font-semibold text-foreground">{type}</span>
                  <span className="text-sm tabular-nums text-muted-foreground">{count}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <SummaryCard title="Incident Barangay Summary" badge="TOP AREAS" rows={barangayRows} emptyLabel="NONE" />
        <SummaryCard title="Incident Agency Summary" badge="TARGET AGENCIES" rows={agencyRows} emptyLabel="NONE" />
        <SummaryCard title="Responder Activity" badge="ASSIGNMENTS" rows={responderRows} emptyLabel="NONE" />
        <SummaryCard title="Unit Deployment Frequency" badge="UNITS" rows={unitRows} emptyLabel="NONE" />

        <Card>
          <CardContent className="p-6">
            <div className="mb-4 flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">Response Health</h3>
              <Badge variant="success" className="text-[10px]">LIVE DATA</Badge>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">Resolved</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.resolved}</div>
              </div>
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">Avg Mesh Hops</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.avgHops.toFixed(1)}</div>
              </div>
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">Online vs BLE</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.online}:{report.ble}</div>
              </div>
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">False Alarm Rate</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.falseAlarmRate.toFixed(1)}%</div>
              </div>
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">BLE Relay Success</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.bleRelaySuccessRate.toFixed(1)}%</div>
              </div>
              <div className="rounded-md border border-border bg-muted/50 p-4">
                <div className="text-xs font-semibold uppercase text-muted-foreground">Gateway Uplinks</div>
                <div className="mt-1 text-2xl font-extrabold text-foreground tabular-nums">{report.bleGatewayUplink}</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

function SummaryCard({
  title,
  badge,
  rows,
  emptyLabel,
}: {
  title: string;
  badge: string;
  rows: Array<[string, number]>;
  emptyLabel: string;
}) {
  return (
    <Card>
      <CardContent className="p-6">
        <div className="mb-4 flex items-center justify-between border-b border-border pb-3">
          <h3 className="text-base font-bold text-foreground">{title}</h3>
          <Badge variant="outline" className="text-[10px]">{badge}</Badge>
        </div>
        <div className="space-y-3">
          {(rows.length ? rows : [[emptyLabel, 0] as [string, number]]).map(([label, count]) => (
            <div key={label} className="flex items-center justify-between gap-4 rounded-md border border-border bg-muted/50 px-3 py-2">
              <span className="min-w-0 truncate text-sm font-semibold text-foreground">{label}</span>
              <span className="shrink-0 text-sm tabular-nums text-muted-foreground">{count}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
