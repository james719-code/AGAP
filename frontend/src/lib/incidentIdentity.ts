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
  const indexByIdentity = new Map<string, number>();

  for (const incident of incidents) {
    let existingIndex = -1;
    for (const identity of incidentIdentityParts(incident)) {
      const index = indexByIdentity.get(identity);
      if (index !== undefined && (existingIndex === -1 || index < existingIndex)) {
        existingIndex = index;
      }
    }

    if (existingIndex === -1) {
      existingIndex = result.length;
      result.push(incident);
    } else {
      result[existingIndex] = { ...incident, ...result[existingIndex] };
    }

    // Index the merged row: earlier fields still win, so an incoming alias
    // that was overwritten must not become an identity for this incident.
    for (const identity of incidentIdentityParts(result[existingIndex])) {
      const index = indexByIdentity.get(identity);
      if (index === undefined || existingIndex < index) {
        indexByIdentity.set(identity, existingIndex);
      }
    }
  }
  return result;
}
