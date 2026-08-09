import type { ResponderUnit } from '@/types/api';

// A responder whose last_seen_at is older than this is treated as stale —
// e.g. the device stopped reporting without ever flipping to OFFLINE server-
// side. 15 minutes is a placeholder threshold; tune to match how often
// responder units are expected to check in (see updateResponderLocation).
export const RESPONDER_STALE_MS = 15 * 60 * 1000;

/**
 * A responder counts as "active"/"on duty" only if its status isn't OFFLINE
 * AND it has reported in recently. Prevents a unit that silently stopped
 * checking in (but never got marked OFFLINE) from still being counted as
 * part of the operational fleet.
 */
export function isResponderActive(responder: ResponderUnit, now: number = Date.now()): boolean {
  if (responder.status === 'OFFLINE') return false;
  if (!responder.last_seen_at) return true; // no timestamp to judge staleness by — don't penalize
  const lastSeen = new Date(responder.last_seen_at).getTime();
  if (Number.isNaN(lastSeen)) return true;
  return now - lastSeen <= RESPONDER_STALE_MS;
}
