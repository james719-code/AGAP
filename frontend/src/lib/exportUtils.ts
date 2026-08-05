import type { Incident } from '@/types/api';

export function exportIncidentsToCSV(incidents: Incident[]) {
  if (incidents.length === 0) return;

  const headers = [
    'Incident ID',
    'Packet ID',
    'Emergency Type',
    'Status',
    'Target Address',
    'Latitude',
    'Longitude',
    'Mesh Hops',
    'Channel',
    'Sender Device',
    'Event Timestamp',
    'Received At',
  ];

  const rows = incidents.map((inc) => [
    inc.id,
    `"${inc.packet_id}"`,
    inc.incident_type,
    inc.status,
    `"${typeof inc.target_id === 'string' ? inc.target_id : inc.target_id?.raw || ''}"`,
    inc.latitude,
    inc.longitude,
    inc.hop_count,
    inc.channel,
    `"${inc.sender_device_id}"`,
    `"${inc.event_timestamp || ''}"`,
    `"${inc.received_at || ''}"`,
  ]);

  const csvContent = [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `AGAP_Incident_Telemetry_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export function exportIncidentsToJSON(incidents: Incident[]) {
  if (incidents.length === 0) return;

  const jsonContent = JSON.stringify(incidents, null, 2);
  const blob = new Blob([jsonContent], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `AGAP_Incident_Telemetry_${new Date().toISOString().slice(0, 10)}.json`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
