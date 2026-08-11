import type { Incident } from '@/types/api';

export function incidentIdentityParts(incident: Incident): string[] {
  return [
    incident.incident_uuid ? `incident:${incident.incident_uuid}` : '',
    incident.packet_uuid ? `packet:${incident.packet_uuid}` : '',
    Number.isFinite(Number(incident.id)) ? `id:${incident.id}` : '',
  ].filter(Boolean);
}

export function incidentIdentity(incident: Incident): string {
  return incidentIdentityParts(incident)[0] ?? `id:${incident.id}`;
}

export function sameIncident(left: Incident, right: Incident): boolean {
  const leftParts = new Set(incidentIdentityParts(left));
  return incidentIdentityParts(right).some((part) => leftParts.has(part));
}

export function deduplicateIncidents(incidents: Incident[]): Incident[] {
  const result: Incident[] = [];
  for (const incident of incidents) {
    const existingIndex = result.findIndex((current) => sameIncident(current, incident));
    if (existingIndex === -1) {
      result.push(incident);
    } else {
      result[existingIndex] = { ...incident, ...result[existingIndex] };
    }
  }
  return result;
}
