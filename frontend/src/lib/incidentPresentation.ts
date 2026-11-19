import type { IncidentStatus } from '@/types/api';

export const NEEDS_ASSIGNMENT_STATUSES = new Set<IncidentStatus>(['RECEIVED', 'ACKNOWLEDGED']);
const RESPONSE_STATUSES = new Set<IncidentStatus>(['DISPATCHED', 'EN_ROUTE', 'ON_SCENE']);
const PENDING_STATUSES = new Set<IncidentStatus>(['CREATED', 'QUEUED']);

export function incidentQueueRank(status: IncidentStatus): number {
  if (NEEDS_ASSIGNMENT_STATUSES.has(status)) return 0;
  if (RESPONSE_STATUSES.has(status)) return 1;
  if (PENDING_STATUSES.has(status)) return 2;
  return 3;
}

export function incidentStatusTone(status: IncidentStatus): string {
  if (NEEDS_ASSIGNMENT_STATUSES.has(status)) return 'tone-danger';
  if (RESPONSE_STATUSES.has(status)) return 'tone-info';
  if (PENDING_STATUSES.has(status)) return 'tone-warning';
  if (status === 'RESOLVED') return 'tone-success';
  return '';
}

export function incidentStatusLabel(status: string): string {
  const label = status.replaceAll('_', ' ').toLowerCase();
  return label.charAt(0).toUpperCase() + label.slice(1);
}
