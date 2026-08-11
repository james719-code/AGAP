import type { Agency, AgencyType, Incident, IncidentStatus, IncidentType, ResponderUnit } from '@/types/api';
import { isResponderActive } from '@/lib/responderStatus';

const CLOSED_INCIDENT_STATUSES = new Set<IncidentStatus>(['RESOLVED', 'CANCELLED', 'FALSE_ALARM']);

const AGENCY_TYPE_FOR_INCIDENT: Record<IncidentType, AgencyType> = {
  CRIME: 'POLICE',
  MEDICAL: 'MEDICAL',
  ACCIDENT: 'RESCUE',
  DISASTER: 'DRRMO',
  FIRE: 'FIRE',
};

export function isIncidentDispatchable(incident: Incident): boolean {
  return !CLOSED_INCIDENT_STATUSES.has(incident.status);
}

export function requiredAgencyType(incident: Incident): AgencyType {
  return AGENCY_TYPE_FOR_INCIDENT[incident.incident_type];
}

export function eligibleResponders(
  incident: Incident,
  responders: ResponderUnit[],
  agencies: Agency[],
  excludedUnitIds: ReadonlySet<number> = new Set<number>(),
): ResponderUnit[] {
  if (!isIncidentDispatchable(incident)) return [];

  const agencyType = requiredAgencyType(incident);
  const agenciesById = new Map(agencies.map((agency) => [agency.id, agency]));
  return responders.filter((responder) => {
    const agency = agenciesById.get(responder.agency_id);
    return responder.status === 'IDLE'
      && isResponderActive(responder)
      && !excludedUnitIds.has(responder.id)
      && agency?.is_active !== false
      && agency?.type === agencyType;
  });
}
