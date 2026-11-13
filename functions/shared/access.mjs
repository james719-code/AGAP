import { HttpError, first } from './http.mjs';

export const citizenIncidentScope = `(i.citizen_id = ? OR EXISTS (
  SELECT 1 FROM incident_reports r WHERE r.incident_id = i.id AND r.reporter_user_id = ?
))`;

export const isCitizen = (user) => user.role === 'CITIZEN';
export const isAdmin = (user) => user.role === 'ADMIN';
export const activeDispatches = "'ASSIGNED','ACCEPTED','EN_ROUTE','ON_SCENE'";

export function deny(message = 'Access denied') {
  throw new HttpError(403, message);
}

export function ownUnit(db, user) {
  return first(db, `SELECT u.id FROM rescue_units u
    LEFT JOIN rescue_unit_members m ON m.rescue_unit_id = u.id AND m.responder_id = ? AND m.removed_at IS NULL
    WHERE u.user_id = ? OR m.id IS NOT NULL
    ORDER BY COALESCE(m.is_leader, 0) DESC, u.id LIMIT 1`, [user.id, user.id]);
}

export async function requireUnit(db, user, unitId) {
  if (isAdmin(user)) return;
  const unit = user.role === 'RESPONDER' ? await ownUnit(db, user) : null;
  if (!unit || unit.id !== unitId) deny('This unit is assigned to another account');
}

export async function requireIncidentAssignment(db, user, incidentId, { dispatchId, activeOnly = false } = {}) {
  if (isAdmin(user)) return;
  const unit = user.role === 'RESPONDER' ? await ownUnit(db, user) : null;
  if (!unit || !await first(db, `SELECT id FROM incident_dispatches
    WHERE incident_id = ? AND unit_id = ? ${dispatchId ? 'AND id = ?' : ''}
    ${activeOnly ? `AND status IN (${activeDispatches})` : ''} LIMIT 1`,
  [incidentId, unit.id, ...(dispatchId ? [dispatchId] : [])])) {
    deny('This incident is assigned to another responder unit');
  }
}

export function authorizeRoute(user, path, method) {
  if (isAdmin(user)) return;
  if (path.startsWith('/admin/') || path.startsWith('/system/') || path === '/users') deny();
  if (method !== 'GET' && /^\/(agencies|rescue-units|responders|emergency-types|messages\/catalog)(\/|$)/.test(path)) {
    if (method === 'PUT' && /^\/(rescue-units|responders)\/\d+\/location$/.test(path) && user.role === 'RESPONDER') return;
    deny();
  }
  if (isCitizen(user) && /^\/(responders|rescue-units)(\/|$)/.test(path)) deny();
  if (/^\/(responders|rescue-units)\/\d+\/(dispatches|members)(\/|$)/.test(path)) deny();
}
