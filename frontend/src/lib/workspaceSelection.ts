/** Retain a user's explicit selection only while it is still available. Never silently select a replacement. */
export function retainUnitSelection(current: string, candidates: ReadonlyArray<{ id: number }>): string {
  return candidates.some((candidate) => String(candidate.id) === current) ? current : '';
}

/** A re-render or late response must not show messages from another incident. */
export function messagesForIncident<T extends { incident_id: number }>(messages: readonly T[], incidentId: number | undefined): T[] {
  return incidentId == null ? [] : messages.filter((message) => message.incident_id === incidentId);
}
