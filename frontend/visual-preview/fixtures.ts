import type {
  Agency, AuditLog, EmergencyType, Incident, IncidentDispatch, IncidentMessage, IncidentReport,
  IncidentStatus, IncidentType, Notification, OperationalReport, PredefinedMessage, ResponderUnit, UserAccount,
} from '@/types/api';

// All names, IDs, events, positions and metrics below are illustrative fixtures.
const ago = (m: number) => new Date(Date.now() - m * 60_000).toISOString();
export const demoAdministrator: UserAccount = {
  id: 9000, username: 'preview-admin', name: 'Demo Dispatcher', email: 'dispatcher@example.invalid',
  role: 'ADMIN', account_status: 'APPROVED', is_verified: true, created_at: ago(120_000),
};

const agencyTypes: Agency['type'][] = ['DRRMO', 'MEDICAL', 'RESCUE', 'POLICE', 'FIRE', 'VOLUNTEER'];
const agencyNames = ['Sample Municipal Operations Center', 'Sample Medical Response Team', 'Sample Rescue Coordination Team', 'Sample Public Safety Office', 'Sample Fire Response Team', 'Sample Community Volunteers'];
export const agencies: Agency[] = agencyTypes.map((type, i) => ({
  id: i + 1, code: `DEMO-${String(i + 1).padStart(2, '0')}`, name: agencyNames[i], type,
  region_code: '05', province_code: '05', muni_code: '12', barangay_code: '01',
  contact_number: 'Not provided (demo)', latitude: 13.62 + i * .008, longitude: 123.18 + i * .007,
  is_active: i !== 5, operating_hours: '24/7', created_at: ago(50_000),
}));

const kinds: IncidentType[] = ['MEDICAL', 'DISASTER', 'ACCIDENT', 'FIRE', 'CRIME'];
const statuses: IncidentStatus[] = ['RECEIVED', 'ACKNOWLEDGED', 'DISPATCHED', 'EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED', 'FALSE_ALARM'];
export const incidents: Incident[] = Array.from({ length: 29 }, (_, i) => ({
  id: 801 + i, incident_uuid: `fixture-${i + 1}`, packet_uuid: `sample-packet-${i + 1}`, packet_hash: `DEMO-HASH-${i + 1}`,
  target_id: { raw: `05-05-12-${String(i + 1).padStart(2, '0')}-01::sample-${i}`, region: '05', province: '05', municipality: '12', barangay: String(i + 1).padStart(2, '0'), zone: '01', uuid_hash: `sample-${i + 1}` },
  incident_type: kinds[i % kinds.length], channel: i % 3 === 0 ? 'BLE_MESH' : 'ONLINE',
  delivery_path: i % 3 === 0 ? 'BLE_CLOUD_SHORTCUT' : 'DIRECT_UPLINK',
  latitude: 13.62 + (i % 7) * .009, longitude: 123.18 + (i % 5) * .012,
  altitude: 5 + i, gps_accuracy: 8 + i % 8, hop_count: i % 3 === 0 ? 2 + i % 5 : 0,
  ttl: 15, sender_device_id: `SYNTHETIC-${i + 101}`, status: statuses[i % statuses.length],
  event_timestamp: ago(5 + i * 38), received_at: ago(4 + i * 38), created_at: ago(4 + i * 38), updated_at: ago(i * 38),
}));

export const responders: ResponderUnit[] = Array.from({ length: 16 }, (_, i) => ({
  id: 501 + i, agency_id: i % agencies.length + 1, target_id: `05-05-12-01-01::demo-responder-${i + 1}`,
  unit_callsign: `DEMO-${agencies[i % agencies.length].type}-${String(i + 1).padStart(2, '0')}`,
  device_id: `FIXTURE-DEVICE-${i + 1}`, status: i % 4 === 0 ? 'OFFLINE' : i % 3 === 0 ? 'DISPATCHED' : 'IDLE',
  current_lat: 13.62 + (i % 4) * .018, current_lon: 123.18 + (i % 6) * .01,
  last_seen_at: ago(i % 4 === 0 ? 180 : 2 + i % 7), responder_name: `Sample Responder ${String(i + 1).padStart(2, '0')}`,
}));

export const users: UserAccount[] = [demoAdministrator, ...Array.from({ length: 26 }, (_, i): UserAccount => ({
  id: 9500 + i, username: `sample-user-${String(i + 1).padStart(2, '0')}`, name: `Sample ${i % 3 === 0 ? 'Responder' : 'Citizen'} ${String(i + 1).padStart(2, '0')}`,
  email: `sample-${i + 1}@example.invalid`, role: i % 3 === 0 ? 'RESPONDER' : 'CITIZEN',
  account_status: i % 7 === 0 ? 'SUSPENDED' : i % 4 === 0 ? 'PENDING' : 'APPROVED',
  is_verified: i % 7 !== 0 && i % 4 !== 0, agency_id: i % 3 === 0 ? i % 5 + 1 : undefined,
  created_at: ago(2000 + i * 400),
}))];

const kindLabels = ['Medical emergency', 'Natural disaster', 'Road accident', 'Fire incident', 'Public safety incident'];
const assignedAgencies: Agency['type'][] = ['MEDICAL', 'DRRMO', 'RESCUE', 'FIRE', 'POLICE'];
export const emergencyTypes: EmergencyType[] = kinds.map((kind, i) => ({
  id: i + 1, code: kind, name: kindLabels[i], description: `Illustrative classification for ${kindLabels[i].toLowerCase()} reports.`,
  default_priority: i === 0 ? 5 : i + 1, protocol_code: i + 1, target_agency_type: assignedAgencies[i],
  is_active: true, created_at: ago(10000), updated_at: ago(300),
}));

const messageExamples = ['Distress alert received', 'Please remain at your reported location if safe', 'Responder assigned', 'Rescue team en route', 'Responder on scene', 'Incident resolved', 'Requesting additional assistance'];
export const catalog: PredefinedMessage[] = messageExamples.map((value, i) => ({
  id: 701 + i, code: `DEMO_${String(i + 1).padStart(2, '0')}`, protocol_code: i + 1,
  message_en: value, message_fil: 'Halimbawang mensahe', message_bikol: 'Halimbawang mensahe',
  message_type: i === 0 ? 'ACK' : i === 6 ? 'SOS' : 'STATUS', is_active: i !== 6,
  created_at: ago(9000), updated_at: ago(45),
}));

export const dispatches: IncidentDispatch[] = incidents.filter((x) => ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE', 'RESOLVED'].includes(x.status)).map((x, i) => ({
  id: 1100 + i, incident_id: x.id, unit_id: responders[i % responders.length].id, dispatched_by: 9000,
  status: x.status === 'RESOLVED' ? 'COMPLETED' : x.status === 'DISPATCHED' ? 'ASSIGNED' : x.status === 'ON_SCENE' ? 'ON_SCENE' : 'EN_ROUTE',
  dispatched_at: x.received_at, unit_callsign: responders[i % responders.length].unit_callsign,
  agency_name: agencies[responders[i % responders.length].agency_id - 1].name,
  responder_name: responders[i % responders.length].responder_name,
}));

export function messages(id: number): IncidentMessage[] {
  return [
    { id: id * 10, incident_id: id, sender_user_id: 9500, sender_name: 'Sample Citizen', sender_role: 'CITIZEN', custom_message: 'This is a simulated request for assistance. Our location is shown on the example incident map.', channel: 'BLE', sent_at: ago(24), message_type: 'CHAT' },
    { id: id * 10 + 1, incident_id: id, sender_user_id: 9000, sender_name: 'Demo Dispatcher', sender_role: 'ADMIN', custom_message: 'Sample notification: the incident has been acknowledged for UI review.', channel: 'ONLINE', sent_at: ago(20), message_type: 'CHAT' },
    { id: id * 10 + 2, incident_id: id, sender_user_id: 9501, sender_name: 'Sample Responder', sender_role: 'RESPONDER', custom_message: 'Sample update received. No real dispatch is occurring.', channel: 'ONLINE', sent_at: ago(12), message_type: 'CHAT' },
  ];
}

export const auditLogs: AuditLog[] = Array.from({ length: 27 }, (_, i) => ({
  id: 1200 + i, actor_id: 9000, action: ['INCIDENT_STATUS_UPDATED', 'USER_VERIFIED', 'DISPATCH_CREATED', 'AGENCY_UPDATED'][i % 4],
  entity_type: ['INCIDENT', 'USER', 'DISPATCH', 'AGENCY'][i % 4], entity_id: 801 + i,
  old_value: JSON.stringify({ status: 'Preview old value' }), new_value: JSON.stringify({ status: 'Preview new value' }), created_at: ago(10 + i * 33),
}));

export const notifications: Notification[] = Array.from({ length: 5 }, (_, i) => ({
  id: 1500 + i, user_id: 9000, incident_id: incidents[i].id,
  type: ['NEW_INCIDENT', 'DISPATCH_ASSIGNED', 'INCIDENT_STATUS_UPDATED'][i % 3], is_read: i > 1,
  sent_at: ago(5 + i * 15), payload: 'Synthetic event for preview',
}));

export function incidentReports(id: number): IncidentReport[] {
  const incident = incidents.find((item) => item.id === id);
  if (!incident) return [];
  return [{ id: id * 10, report_uuid: `fixture-report-${id}`, transport_token: `demo-${id}`, incident_id: id,
    submission_type: 'SOS', subject_relation: 'SELF', emergency_type: incident.incident_type, description: 'Illustrative incident details for visual inspection. No actual emergency is reported.',
    people_affected: 2, immediate_danger: false, location_source: 'GPS', latitude: incident.latitude, longitude: incident.longitude,
    gps_accuracy_meters: incident.gps_accuracy, observed_at: incident.event_timestamp, origin_channel: incident.channel,
    detail_state: 'FULL', created_at: incident.created_at }];
}

const breakdown = (values: string[]) => [...new Set(values)].map((label) => ({ label, count: values.filter((value) => value === label).length }));
export const report: OperationalReport = {
  generated_at: ago(0), total_incidents: incidents.length, active_incidents: incidents.filter((x) => !['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(x.status)).length,
  resolved_incidents: incidents.filter((x) => x.status === 'RESOLVED').length, false_alarms: incidents.filter((x) => x.status === 'FALSE_ALARM').length,
  false_alarm_rate: 10.3, average_ack_minutes: 2.8, average_dispatch_minutes: 5.4, average_arrival_minutes: 13.2, average_response_minutes: 28.6,
  active_responders: responders.filter((x) => x.status !== 'OFFLINE').length, total_responders: responders.length,
  status_distribution: breakdown(incidents.map((x) => x.status)), incident_type_distribution: breakdown(incidents.map((x) => x.incident_type)),
  barangay_distribution: breakdown(incidents.map((_, i) => `Sample area ${i % 5 + 1}`)),
  agency_dispatches: breakdown(dispatches.map((x) => x.agency_name ?? 'Sample agency')),
  responder_assignments: breakdown(dispatches.map((x) => x.responder_name ?? 'Sample responder')),
  unit_deployments: breakdown(dispatches.map((x) => x.unit_callsign ?? 'Sample unit')),
};
