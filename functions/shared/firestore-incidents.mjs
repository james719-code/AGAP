import { deny, isAdmin, isCitizen } from './access.mjs';
import { agencyMatch, date, dispatchTransitions, incident, incidentInput, transitions, types } from './incidents.mjs';
import { HttpError, id, json, noContent, now, number, oneOf, pageNumber, readBody, requireRow, string, withBooleans } from './http.mjs';
import { eq } from './firestore-auth.mjs';
import { activeStatuses, ordered } from './firestore-store.mjs';

export const closedStatuses = ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'];
export const openStatuses = Object.keys(transitions).filter((status) => !closedStatuses.includes(status));
const isOpen = (row) => !closedStatuses.includes(row.status);
export const scope = (user) => isCitizen(user) ? [['visible_to', 'array-contains', user.id]] : [];
const byId = [['id', 'asc']];
const newest = (field) => [[field, 'desc'], ['id', 'desc']];
export const incidentView = ({ visible_to, ...row }) => incident(row);
export const unitView = (row) => ({ ...row, unit_callsign: row.unit_code, current_lat: row.current_latitude,
  current_lon: row.current_longitude, last_seen_at: row.last_location_at });
export const writeAudit = (tx, user, action, entity, entityId, detail = null) => tx.insert('audit_logs', {
  actor_id: user.id, action, entity_type: entity, entity_id: entityId, new_value: detail,
});

export async function ownFirestoreUnit(db, user) {
  const members = await db.list('rescue_unit_members', { where: [eq('responder_id', user.id), eq('removed_at', null)] });
  const ids = ordered(members, [['is_leader', 'desc'], ['rescue_unit_id', 'asc']]);
  const direct = (await db.list('rescue_units', { where: [eq('user_id', user.id)], order: byId, limit: 1 }))[0];
  const assigned = ids[0];
  if (assigned && (assigned.is_leader || !direct || assigned.rescue_unit_id < direct.id)) return db.get('rescue_units', assigned.rescue_unit_id);
  return direct ?? null;
}

async function requireUnit(db, user, unitId) {
  if (!isAdmin(user) && (user.role !== 'RESPONDER' || (await ownFirestoreUnit(db, user))?.id !== unitId)) {
    deny('This unit is assigned to another account');
  }
}

async function requireAssignment(tx, user, incidentId, dispatchId, active = false) {
  if (isAdmin(user)) return;
  const unit = user.role === 'RESPONDER' && await ownFirestoreUnit(tx, user);
  const where = [eq('incident_id', incidentId), eq('unit_id', unit?.id ?? 0)];
  if (dispatchId) where.push(eq('id', dispatchId));
  if (active) where.push(['status', 'in', activeStatuses]);
  if (!unit || !await tx.find('incident_dispatches', where)) deny('This incident is assigned to another responder unit');
}

export async function firestoreDispatches(db, where, order = newest('dispatched_at')) {
  const assignments = await db.list('incident_dispatches', { where, order });
  const units = new Map();
  const agencies = new Map();
  const lookup = (table, cache, key) => {
    if (!cache.has(key)) cache.set(key, db.get(table, key));
    return cache.get(key);
  };
  return Promise.all(assignments.map(async (assignment) => {
    const unit = await lookup('rescue_units', units, assignment.unit_id);
    const agency = unit && await lookup('agencies', agencies, unit.agency_id);
    return { ...assignment, unit_callsign: unit?.unit_code ?? null, agency_name: agency?.name ?? null,
      responder_name: unit?.responder_name ?? null };
  }));
}

export async function createFirestoreIncident(tx, values, actor = null) {
  const row = await tx.insert('incidents', { ...values, visible_to: values.citizen_id ? [values.citizen_id] : [] });
  await tx.insert('incident_status_history', { incident_id: row.id, status: 'RECEIVED', changed_by: actor });
  const admins = await tx.list('users', { where: [eq('role', 'ADMIN'), eq('account_status', 'APPROVED')], limit: 100 });
  for (const admin of admins) await tx.insert('notifications', { user_id: admin.id, incident_id: row.id,
    type: 'NEW_INCIDENT', payload: `New ${row.incident_type.toLowerCase()} incident received` });
  return row;
}

async function listIncidents(db, params, user) {
  const where = scope(user);
  const status = params.has('status') ? oneOf(params.get('status'), Object.keys(transitions), 'status') : null;
  const closed = params.get('closed_only') === 'true';
  if (status) where.push(eq('status', status));
  else if (closed) where.push(['status', 'in', closedStatuses]);
  if (params.has('type')) where.push(eq('incident_type', oneOf(params.get('type'), types, 'type')));
  if (params.has('region')) where.push(eq('region_code', string(params.get('region'), 'region', 16)));
  if (params.has('since')) where.push(['received_at', '>=', date(params.get('since'), 'since')]);
  if (params.get('exclude_demo') === 'true') where.push(eq('is_demo', 0));
  const limit = pageNumber(params.get('limit'), 50, 1, 200);
  const offset = pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER);
  const [total, page] = status && closed && !closedStatuses.includes(status) ? [0, []] : await Promise.all([
    db.count('incidents', where, newest('received_at')), db.list('incidents', { where, limit, offset, order: newest('received_at') }),
  ]);
  return json({ total, count: page.length, limit, offset, incidents: page.map(incidentView) });
}

async function changeStatus(tx, row, status, user, notes, timestamp) {
  await tx.update('incidents', row, { status, updated_at: timestamp,
    dispatched_at: ['DISPATCHED', 'EN_ROUTE'].includes(status) ? row.dispatched_at ?? timestamp : row.dispatched_at,
    resolved_at: status === 'RESOLVED' ? row.resolved_at ?? timestamp : row.resolved_at });
  await tx.insert('incident_status_history', { incident_id: row.id, status, changed_by: user.id, notes });
}

async function dispatch(tx, row, unitId, user) {
  await requireUnit(tx, user, unitId);
  const unit = requireRow(await tx.get('rescue_units', unitId), 'Responder unit not found');
  const agency = await tx.get('agencies', unit.agency_id);
  if (!agency?.is_active || !agencyMatch[row.incident_type]?.includes(agency.type)) {
    throw new HttpError(409, 'This agency cannot respond to this incident type');
  }
  if (!isOpen(row) || unit.status !== 'IDLE' || await activeForUnit(tx, unit.id)) {
    throw new HttpError(409, 'Incident is closed or the unit is no longer available');
  }
  const timestamp = now();
  const item = await tx.insert('incident_dispatches', { incident_id: row.id, unit_id: unitId, dispatched_by: user.id, dispatched_at: timestamp });
  await tx.update('rescue_units', unit, { status: 'DISPATCHED', updated_at: timestamp });
  if (['RECEIVED', 'ACKNOWLEDGED'].includes(row.status)) {
    await changeStatus(tx, row, 'DISPATCHED', user, `Unit ${unit.unit_code} dispatched`, timestamp);
  }
  await writeAudit(tx, user, 'CREATE_DISPATCH', 'INCIDENT', row.id);
  return json({ ...item, unit_callsign: unit.unit_code, agency_name: agency.name, responder_name: unit.responder_name }, 201);
}

export const activeForUnit = (db, unit) => db.find('incident_dispatches', [eq('unit_id', unit), ['status', 'in', activeStatuses]]);

async function changeDispatch(tx, row, dispatchId, status, user) {
  await requireAssignment(tx, user, row.id, dispatchId);
  const assignment = await tx.get('incident_dispatches', dispatchId);
  if (assignment?.incident_id !== row.id) throw new HttpError(404, 'Dispatch not found');
  const response = () => json({ status: 'ok', dispatch_id: dispatchId, new_status: status });
  if (assignment.status === status) return response();
  if (!dispatchTransitions[assignment.status]?.includes(status)) throw new HttpError(409, 'Invalid dispatch status transition');
  const stamp = now();
  const timeField = { ACCEPTED: 'accepted_at', EN_ROUTE: 'departed_at', ON_SCENE: 'arrived_at', COMPLETED: 'completed_at' }[status];
  await tx.update('incident_dispatches', assignment, { status, ...(timeField ? { [timeField]: assignment[timeField] ?? stamp } : {}) });
  await writeAudit(tx, user, 'UPDATE_DISPATCH_STATUS', 'INCIDENT_DISPATCH', dispatchId, status);
  if (['COMPLETED', 'CANCELLED'].includes(status)) {
    const unit = await tx.get('rescue_units', assignment.unit_id);
    await tx.update('rescue_units', unit, { status: unit.status === 'OFFLINE' ? 'OFFLINE' : 'IDLE', updated_at: stamp });
  }
  const busy = ['DISPATCHED', 'EN_ROUTE', 'ON_SCENE'].includes(row.status);
  const next = status === 'EN_ROUTE' && row.status === 'DISPATCHED' ? 'EN_ROUTE'
    : status === 'ON_SCENE' && ['DISPATCHED', 'EN_ROUTE'].includes(row.status) ? 'ON_SCENE'
      : status === 'COMPLETED' && busy ? 'RESOLVED' : null;
  const cancelled = status === 'CANCELLED' && busy && !await tx.find('incident_dispatches',
    [eq('incident_id', row.id), ['status', 'in', activeStatuses]]);
  if (next || cancelled) await changeStatus(tx, row, next || 'RECEIVED', user, `Dispatch ${dispatchId} moved to ${status}`, stamp);
  return response();
}

async function messageView(db, row) {
  const [catalog, sender, receiver] = await Promise.all([
    row.predefined_message_id && db.get('predefined_messages', row.predefined_message_id),
    row.sender_user_id && db.get('users', row.sender_user_id), row.receiver_user_id && db.get('users', row.receiver_user_id),
  ]);
  return { ...row, predefined_code: catalog?.code ?? null, message_en: catalog?.message_en ?? null,
    sender_name: sender?.username ?? null, receiver_name: receiver?.username ?? null, sender_role: sender?.role ?? null,
    message_type: catalog?.message_type === 'STATUS' ? 'STATUS_UPDATE' : 'CHAT' };
}

async function postMessage(tx, incidentId, body, user) {
  await requireAssignment(tx, user, incidentId, undefined, true);
  const text = body.custom_message == null ? null : string(body.custom_message, 'message', 2000);
  const catalog = body.predefined_message_id == null ? null : id(body.predefined_message_id);
  if (!text && !catalog) throw new HttpError(400, 'A message or catalog entry is required');
  const key = body.client_message_id == null ? crypto.randomUUID() : string(body.client_message_id, 'client_message_id', 128);
  const receiver = body.receiver_user_id == null ? null : id(body.receiver_user_id);
  const channel = oneOf(body.channel ?? 'ONLINE', ['ONLINE', 'BLE'], 'channel');
  const previous = await tx.find('incident_messages', [eq('incident_id', incidentId), eq('sender_user_id', user.id), eq('client_message_id', key)]);
  if (previous) return json(await messageView(tx, previous), 200);
  if (catalog && !(await tx.get('predefined_messages', catalog))?.is_active) throw new HttpError(404, 'Active catalog entry not found');
  const row = await tx.insert('incident_messages', { incident_id: incidentId, sender_user_id: user.id, receiver_user_id: receiver,
    predefined_message_id: catalog, custom_message: text, channel, client_message_id: key });
  return json(await messageView(tx, row), 201);
}

export async function firestoreIncidents(path, request, db, user, params) {
  const method = request.method;
  if (path === '/incidents' && method === 'GET') return listIncidents(db, params, user);
  if (path === '/incidents' && method === 'POST') {
    if (!isAdmin(user)) deny();
    const input = await incidentInput(await readBody(request));
    return db.transaction(async (tx) => {
      const found = await tx.find('incidents', [eq('incident_uuid', input.incident_uuid)]);
      const created = found || await createFirestoreIncident(tx, { ...input, channel: 'ONLINE', delivery_path: 'DIRECT_UPLINK' }, user.id);
      return json(incidentView(created), found ? 200 : 201);
    });
  }
  const bulk = path.match(/^\/incidents\/(status-history|dispatches)$/);
  if (bulk && method === 'GET') {
    if (isCitizen(user)) deny();
    const ids = (params.get('incident_ids') || '').split(',').filter(Boolean);
    if (ids.length > 200) throw new HttpError(400, 'At most 200 incidents can be queried at once');
    const result = await Promise.all([...new Set(ids.map(id))].map(async (key) => [key, bulk[1] === 'dispatches'
      ? await firestoreDispatches(db, [eq('incident_id', key)]) : await history(db, key)]));
    return json(Object.fromEntries(result));
  }
  const match = path.match(/^\/incidents\/(\d+)(?:\/(status|status-history|dispatches|reports|messages|dispatch|location|available-rescue-units)(?:\/(\d+)\/status)?)?$/);
  if (!match) return;
  const [, rawId, action, dispatchId] = match;
  const incidentId = id(rawId);
  const allowed = async (store) => {
    const row = await store.get('incidents', incidentId);
    if (!row || (isCitizen(user) && !row.visible_to?.includes(user.id))) throw new HttpError(404, 'Incident not found');
    return row;
  };
  if (method === 'GET' && !dispatchId) {
    const row = await allowed(db);
    if (!action) return json(incidentView(row));
    if (action === 'status-history') return json(await history(db, incidentId));
    if (action === 'dispatches') return json(await firestoreDispatches(db, [eq('incident_id', incidentId)]));
    if (action === 'reports') {
      if (isCitizen(user)) deny();
      const reports = await db.list('incident_reports', { where: [eq('incident_id', incidentId)], order: byId });
      return json({ success: true, reports: reports.map((item) => withBooleans(item, 'immediate_danger')) });
    }
    if (action === 'messages') {
      await requireAssignment(db, user, incidentId, undefined, true);
      const messages = await db.list('incident_messages', { where: [eq('incident_id', incidentId)], order: [['sent_at', 'asc'], ...byId] });
      return json(await Promise.all(messages.map((item) => messageView(db, item))));
    }
    if (action === 'available-rescue-units') {
      if (!isAdmin(user)) deny();
      const matches = agencyMatch[row.incident_type];
      const agencies = matches?.length ? await db.list('agencies', { where: [eq('is_active', 1), ['type', 'in', matches]] }) : [];
      const units = (await Promise.all(agencies.map((agency) => db.list('rescue_units', {
        where: [eq('agency_id', agency.id), eq('status', 'IDLE')],
      })))).flat();
      const available = await Promise.all(units.map(async (unit) => await activeForUnit(db, unit.id) ? null : unit));
      return json(ordered(available.filter(Boolean), [['unit_code', 'asc']]).map(unitView));
    }
  }
  if ((method === 'POST' && ['dispatch', 'messages'].includes(action) && !dispatchId) ||
      (method === 'PUT' && (['status', 'location'].includes(action) && !dispatchId || action === 'dispatches' && dispatchId))) {
    const body = await readBody(request);
    return db.transaction(async (tx) => {
      const row = await allowed(tx);
      if (action === 'dispatch') return dispatch(tx, row, id(body.unit_id), user);
      if (action === 'messages') return postMessage(tx, incidentId, body, user);
      if (action === 'dispatches') return changeDispatch(tx, row, id(dispatchId),
        oneOf(body.status, Object.keys(dispatchTransitions), 'dispatch status'), user);
      if (action === 'status' || !isCitizen(user)) await requireAssignment(tx, user, row.id);
      if (action === 'location') {
        if (!isOpen(row)) throw new HttpError(409, 'Incident is closed');
        await tx.update('incidents', row, { latitude: number(body.latitude, 'latitude', -90, 90),
          longitude: number(body.longitude, 'longitude', -180, 180), updated_at: now() });
        return json({ status: 'ok', incident_id: incidentId });
      }
      const status = oneOf(body.status, Object.keys(transitions), 'status');
      if (row.status !== status) {
        if (!transitions[row.status]?.includes(status)) throw new HttpError(409, 'Invalid incident status transition');
        await changeStatus(tx, row, status, user, body.notes == null ? '' : string(body.notes, 'notes', 4000, true), now());
        await writeAudit(tx, user, 'UPDATE_INCIDENT_STATUS', 'INCIDENT', incidentId, status);
        if (row.citizen_id) await tx.insert('notifications', { user_id: row.citizen_id, incident_id: incidentId,
          type: 'INCIDENT_STATUS_UPDATED', payload: `Incident status changed to ${status}` });
      }
      return json({ status: 'ok', incident_id: incidentId, new_status: status });
    });
  }
}

const history = (db, key) => db.list('incident_status_history', {
  where: [eq('incident_id', key)], order: [['created_at', 'asc'], ...byId],
});

export async function firestoreUnits(path, request, db, user) {
  const own = path.match(/^\/(?:rescue-units|responders)\/me(\/dispatches)?$/);
  if (own && request.method === 'GET') {
    const unit = await ownFirestoreUnit(db, user);
    if (own[1]) return json(unit ? await firestoreDispatches(db, [eq('unit_id', unit.id)], [['id', 'desc']]) : []);
    return json(unitView(requireRow(unit, 'No rescue unit assigned')));
  }
  const match = path.match(/^\/(?:rescue-units|responders)\/(\d+)\/(members|dispatches|location)(?:\/(\d+))?$/);
  if (!match) return;
  const [, rawId, action, memberId] = match;
  const unitId = id(rawId);
  const members = [eq('rescue_unit_id', unitId), eq('removed_at', null)];
  if (request.method === 'GET' && !memberId) {
    requireRow(await db.get('rescue_units', unitId), 'Responder unit not found');
    if (action === 'dispatches') return json(await firestoreDispatches(db, [eq('unit_id', unitId)]));
    if (action === 'members') return json((await db.list('rescue_unit_members', { where: members,
      order: [['is_leader', 'desc'], ...byId] })).map((row) => withBooleans(row, 'is_leader')));
  }
  if (request.method === 'PUT' && action === 'location' && !memberId) {
    const body = await readBody(request);
    const status = body.status == null || body.status === '' ? null : oneOf(body.status, ['IDLE', 'DISPATCHED', 'OFFLINE'], 'unit status');
    const stamp = now();
    await db.transaction(async (tx) => {
      const unit = requireRow(await tx.get('rescue_units', unitId), 'Responder unit not found');
      await requireUnit(tx, user, unitId);
      if (status && status !== unit.status && (status === 'DISPATCHED' || await activeForUnit(tx, unitId))) {
        throw new HttpError(409, 'Finish the active dispatch before changing unit status');
      }
      await tx.update('rescue_units', unit, { current_latitude: number(body.latitude, 'latitude', -90, 90),
        current_longitude: number(body.longitude, 'longitude', -180, 180), status: status ?? unit.status,
        last_location_at: stamp, updated_at: stamp });
      await writeAudit(tx, user, 'UPDATE_RESPONDER_LOCATION', 'RESCUE_UNIT', unitId);
    });
    return json({ status: 'ok', responder_id: unitId });
  }
  if (action !== 'members') return;
  if (request.method === 'POST' && !memberId) {
    const body = await readBody(request);
    const responderId = id(body.responder_id);
    if (typeof body.is_leader !== 'boolean') throw new HttpError(400, 'Invalid is_leader');
    return db.transaction(async (tx) => {
      const responder = await tx.get('users', responderId);
      if (responder?.role !== 'RESPONDER' || responder.account_status !== 'APPROVED' ||
          await tx.find('rescue_unit_members', [...members, eq('responder_id', responderId)])) {
        throw new HttpError(409, 'Select an approved responder who is not already assigned to this unit');
      }
      const row = await tx.insert('rescue_unit_members', { rescue_unit_id: unitId, responder_id: responderId, is_leader: Number(body.is_leader) });
      await writeAudit(tx, user, 'ADD_RESCUE_UNIT_MEMBER', 'RESCUE_UNIT', unitId);
      return json(withBooleans(row, 'is_leader'), 201);
    });
  }
  if (request.method === 'DELETE' && memberId) {
    await db.transaction(async (tx) => {
      const active = await tx.list('rescue_unit_members', { where: [...members, eq('responder_id', id(memberId))] });
      if (!active.length) throw new HttpError(404, 'Member not found');
      for (const member of active) await tx.update('rescue_unit_members', member, { removed_at: now() });
      await writeAudit(tx, user, 'REMOVE_RESCUE_UNIT_MEMBER', 'RESCUE_UNIT', unitId);
    });
    return noContent();
  }
}
