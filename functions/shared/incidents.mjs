import { hash } from './auth.mjs';
import { activeDispatches, citizenIncidentScope, deny, isAdmin, isCitizen, ownUnit, requireIncidentAssignment, requireUnit } from './access.mjs';
import { audit } from './registries.mjs';
import {
  HttpError, first, id, json, marks, noContent, now, number, oneOf, pageNumber,
  query, readBody, requireRow, rows, string, withBooleans,
} from './http.mjs';

export const types = ['CRIME', 'MEDICAL', 'ACCIDENT', 'DISASTER', 'FIRE'];
export const terminal = "'RESOLVED','CANCELLED','FALSE_ALARM'";
const transitions = {
  CREATED: ['QUEUED', 'RECEIVED', 'CANCELLED'], QUEUED: ['RECEIVED', 'CANCELLED'],
  RECEIVED: ['ACKNOWLEDGED', 'DISPATCHED', 'CANCELLED', 'FALSE_ALARM'],
  ACKNOWLEDGED: ['DISPATCHED', 'CANCELLED', 'FALSE_ALARM'],
  DISPATCHED: ['EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'RESOLVED', 'CANCELLED'], ON_SCENE: ['RESOLVED', 'FALSE_ALARM'],
  RESOLVED: [], CANCELLED: [], FALSE_ALARM: [],
};
const dispatchTransitions = {
  ASSIGNED: ['ACCEPTED', 'CANCELLED'], ACCEPTED: ['EN_ROUTE', 'CANCELLED'],
  EN_ROUTE: ['ON_SCENE', 'COMPLETED', 'CANCELLED'], ON_SCENE: ['COMPLETED'], COMPLETED: [], CANCELLED: [],
};
const agencyMatch = {
  MEDICAL: ['MEDICAL'], ACCIDENT: ['RESCUE', 'MEDICAL', 'POLICE', 'DRRMO'],
  FIRE: ['FIRE', 'RESCUE', 'DRRMO'], DISASTER: ['DRRMO', 'RESCUE'], CRIME: ['POLICE'],
};
export const dispatchSelect = `SELECT d.*, u.unit_code AS unit_callsign, a.name AS agency_name, u.responder_name
  FROM incident_dispatches d JOIN rescue_units u ON d.unit_id = u.id JOIN agencies a ON u.agency_id = a.id`;
const unitSelect = `SELECT u.*, u.unit_code AS unit_callsign, u.current_latitude AS current_lat,
  u.current_longitude AS current_lon, u.last_location_at AS last_seen_at FROM rescue_units u`;
const messageSelect = `SELECT m.*, p.code AS predefined_code, p.message_en, s.username AS sender_name,
  r.username AS receiver_name, s.role AS sender_role,
  CASE WHEN p.message_type = 'STATUS' THEN 'STATUS_UPDATE' ELSE 'CHAT' END AS message_type
  FROM incident_messages m LEFT JOIN predefined_messages p ON m.predefined_message_id = p.id
  LEFT JOIN users s ON m.sender_user_id = s.id LEFT JOIN users r ON m.receiver_user_id = r.id`;

export function incident(row) {
  const { target_id_raw, region_code, province_code, municipality_code, barangay_code, zone_code, ...record } = row;
  return { ...record, target_id: {
    raw: target_id_raw, region: region_code, province: province_code, municipality: municipality_code,
    barangay: barangay_code, zone: zone_code, uuid_hash: target_id_raw.split('::')[1] || '',
  } };
}

export function date(value, field) {
  if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) throw new HttpError(400, `Invalid ${field}`);
  return new Date(value).toISOString();
}

async function list(db, params, user) {
  const filters = [];
  const args = [];
  if (isCitizen(user)) {
    filters.push(citizenIncidentScope);
    args.push(user.id, user.id);
  }
  for (const [key, sql, validate] of [
    ['type', 'incident_type = ?', (v) => oneOf(v, types, 'type')],
    ['status', 'status = ?', (v) => oneOf(v, Object.keys(transitions), 'status')],
    ['region', 'region_code = ?', (v) => string(v, 'region', 16)],
    ['since', 'julianday(received_at) >= julianday(?)', (v) => date(v, 'since')],
  ]) {
    if (!params.has(key)) continue;
    filters.push(sql);
    args.push(validate(params.get(key)));
  }
  if (params.get('exclude_demo') === 'true') filters.push('is_demo = 0');
  if (params.get('closed_only') === 'true') filters.push(`status IN (${terminal})`);
  const where = filters.length ? `WHERE ${filters.join(' AND ')}` : '';
  const limit = pageNumber(params.get('limit'), 50, 1, 200);
  const offset = pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER);
  const [total, page] = await db.batch([
    { sql: `SELECT COUNT(*) AS total FROM incidents i ${where}`, args },
    { sql: `SELECT i.* FROM incidents i ${where} ORDER BY received_at DESC, id DESC LIMIT ? OFFSET ?`,
      args: [...args, limit, offset] },
  ], 'read');
  return json({ total: total.rows[0].total, count: page.rows.length, limit, offset, incidents: page.rows.map(incident) });
}

async function create(db, request, user) {
  const body = await readBody(request);
  const uuid = string(body.incident_uuid, 'incident_uuid', 36).toLowerCase();
  if (!/^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$/.test(uuid)) throw new HttpError(400, 'Invalid incident_uuid');
  const raw = string(body.target_id?.raw, 'target_id.raw', 150);
  const target = raw.match(/^(\d{1,9})-(\d{1,9})-(\d{1,9})-(\d{1,9})(?:-(\d{1,9}))?::([\da-z-]+)$/i);
  if (!target) throw new HttpError(400, 'Invalid target ID');
  const type = oneOf(body.incident_type, types, 'incident type');
  const lat = number(body.latitude, 'latitude', -90, 90);
  const lon = number(body.longitude, 'longitude', -180, 180);
  const timestamp = now();
  const observed = body.event_timestamp == null ? timestamp : date(body.event_timestamp, 'event_timestamp');
  const source = string(body.sender_device_id, 'sender_device_id', 128);
  const description = body.description == null ? '' : string(body.description, 'description', 4000, true);
  const args = [uuid, await hash(uuid), raw, ...target.slice(1, 5), target[5] || '', type, lat, lon,
    source, observed, timestamp, description];
  const [inserted, , , result] = await db.batch([
    { sql: `INSERT INTO incidents (incident_uuid, packet_hash, target_id_raw, region_code, province_code,
        municipality_code, barangay_code, zone_code, incident_type, latitude, longitude, sender_device_id,
        event_timestamp, received_at, description, channel, delivery_path)
        VALUES (${marks(args)}, 'ONLINE', 'DIRECT_UPLINK') ON CONFLICT(incident_uuid) DO NOTHING`, args },
    { sql: `INSERT INTO incident_status_history (incident_id, status, changed_by)
        SELECT id, 'RECEIVED', ? FROM incidents WHERE incident_uuid = ? AND changes() = 1`, args: [user.id, uuid] },
    { sql: `INSERT INTO notifications (user_id, incident_id, type, payload)
        SELECT u.id, i.id, 'NEW_INCIDENT', ? FROM users u JOIN incidents i ON i.incident_uuid = ?
        WHERE changes() = 1 AND u.role = 'ADMIN' AND u.account_status = 'APPROVED'`,
      args: [`New ${type.toLowerCase()} incident received`, uuid] },
    { sql: 'SELECT * FROM incidents WHERE incident_uuid = ?', args: [uuid] },
  ], 'write');
  return json(incident(result.rows[0]), inserted.rowsAffected ? 201 : 200);
}

function statusUpdate(incidentId, status, timestamp, guard, args = []) {
  return {
    sql: `UPDATE incidents SET status = ?, updated_at = ?,
      dispatched_at = CASE WHEN ? IN ('DISPATCHED','EN_ROUTE') THEN COALESCE(dispatched_at, ?) ELSE dispatched_at END,
      resolved_at = CASE WHEN ? = 'RESOLVED' THEN COALESCE(resolved_at, ?) ELSE resolved_at END
      WHERE id = ? AND ${guard}`,
    args: [status, timestamp, status, timestamp, status, timestamp, incidentId, ...args],
  };
}

function history(incidentId, status, actor, notes = '') {
  return { sql: `INSERT INTO incident_status_history (incident_id, status, changed_by, notes)
    SELECT ?, ?, ?, ? WHERE changes() = 1`, args: [incidentId, status, actor, notes] };
}

async function updateStatus(db, request, user, current) {
  await requireIncidentAssignment(db, user, current.id);
  const body = await readBody(request);
  const status = oneOf(body.status, Object.keys(transitions), 'status');
  const result = () => json({ status: 'ok', incident_id: current.id, new_status: status });
  if (current.status === status) return result();
  if (!transitions[current.status]?.includes(status)) throw new HttpError(409, 'Invalid incident status transition');
  const notes = body.notes == null ? '' : string(body.notes, 'notes', 4000, true);
  const [updated] = await db.batch([
    statusUpdate(current.id, status, now(), 'status = ?', [current.status]),
    history(current.id, status, user.id, notes),
    audit(user.id, 'UPDATE_INCIDENT_STATUS', 'INCIDENT', current.id, { detail: status }),
    { sql: `INSERT INTO notifications (user_id, incident_id, type, payload)
      SELECT citizen_id, id, 'INCIDENT_STATUS_UPDATED', ? FROM incidents
      WHERE id = ? AND citizen_id IS NOT NULL AND changes() = 1`,
      args: [`Incident status changed to ${status}`, current.id] },
  ], 'write');
  if (!updated.rowsAffected) throw new HttpError(409, 'Incident changed. Refresh and try again.');
  return result();
}

async function dispatch(db, request, user, current) {
  const unitId = id((await readBody(request)).unit_id);
  await requireUnit(db, user, unitId);
  const unit = requireRow(await first(db, `SELECT u.*, a.type AS agency_type, a.is_active
    FROM rescue_units u JOIN agencies a ON a.id = u.agency_id WHERE u.id = ?`, [unitId]), 'Responder unit not found');
  if (!unit.is_active || !agencyMatch[current.incident_type]?.includes(unit.agency_type)) {
    throw new HttpError(409, 'This agency cannot respond to this incident type');
  }
  const timestamp = now();
  const [created, selected] = await db.batch([
    { sql: `INSERT INTO incident_dispatches (incident_id, unit_id, dispatched_by, dispatched_at)
        SELECT i.id, u.id, ?, ? FROM incidents i JOIN rescue_units u ON u.id = ?
        JOIN agencies a ON a.id = u.agency_id WHERE i.id = ? AND i.status NOT IN (${terminal})
        AND u.status = 'IDLE' AND a.type = ? AND a.is_active = 1
        AND NOT EXISTS (SELECT 1 FROM incident_dispatches WHERE unit_id = u.id AND status IN (${activeDispatches}))`,
      args: [user.id, timestamp, unitId, current.id, unit.agency_type] },
    { sql: `${dispatchSelect} WHERE d.id = last_insert_rowid() AND changes() = 1`, args: [] },
    { sql: `UPDATE rescue_units SET status = 'DISPATCHED', updated_at = ? WHERE id = ? AND changes() = 1`,
      args: [timestamp, unitId] },
    statusUpdate(current.id, 'DISPATCHED', timestamp, "changes() = 1 AND status IN ('RECEIVED','ACKNOWLEDGED')"),
    history(current.id, 'DISPATCHED', user.id, `Unit ${unit.unit_code} dispatched`),
    { sql: `INSERT INTO audit_logs (actor_id, action, entity_type, entity_id)
      SELECT ?, 'CREATE_DISPATCH', 'INCIDENT', ? FROM incident_dispatches
      WHERE id = last_insert_rowid() AND incident_id = ? AND unit_id = ? AND dispatched_at = ?`,
      args: [user.id, current.id, current.id, unitId, timestamp] },
  ], 'write');
  if (!created.rowsAffected) throw new HttpError(409, 'Incident is closed or the unit is no longer available');
  return json(selected.rows[0], 201);
}

async function updateDispatch(db, request, user, current, dispatchId) {
  await requireIncidentAssignment(db, user, current.id, { dispatchId });
  const { status: value } = await readBody(request);
  const status = oneOf(value, Object.keys(dispatchTransitions), 'dispatch status');
  const assignment = requireRow(await first(db,
    'SELECT * FROM incident_dispatches WHERE incident_id = ? AND id = ?', [current.id, dispatchId]), 'Dispatch not found');
  const result = () => json({ status: 'ok', dispatch_id: dispatchId, new_status: status });
  if (assignment.status === status) return result();
  if (!dispatchTransitions[assignment.status]?.includes(status)) throw new HttpError(409, 'Invalid dispatch status transition');
  const timestamp = now();
  const timeColumn = { ACCEPTED: 'accepted_at', EN_ROUTE: 'departed_at', ON_SCENE: 'arrived_at', COMPLETED: 'completed_at' }[status];
  const nextIncident = { EN_ROUTE: 'EN_ROUTE', ON_SCENE: 'ON_SCENE', COMPLETED: 'RESOLVED', CANCELLED: 'RECEIVED' }[status];
  const releaseUnit = status === 'COMPLETED' || status === 'CANCELLED';
  const nextAllowed = {
    EN_ROUTE: "status = 'DISPATCHED'",
    ON_SCENE: "status IN ('DISPATCHED','EN_ROUTE')",
    COMPLETED: "status IN ('DISPATCHED','EN_ROUTE','ON_SCENE')",
    CANCELLED: `status IN ('DISPATCHED','EN_ROUTE','ON_SCENE') AND NOT EXISTS (
      SELECT 1 FROM incident_dispatches WHERE incident_id = ? AND status IN (${activeDispatches}))`,
  }[status];
  const [updated] = await db.batch([
    { sql: `UPDATE incident_dispatches SET status = ? ${timeColumn ? `, ${timeColumn} = COALESCE(${timeColumn}, ?)` : ''}
        WHERE incident_id = ? AND id = ? AND status = ?`,
      args: [status, ...(timeColumn ? [timestamp] : []), current.id, dispatchId, assignment.status] },
    audit(user.id, 'UPDATE_DISPATCH_STATUS', 'INCIDENT_DISPATCH', dispatchId, { detail: status }),
    ...(releaseUnit ? [{
      sql: `UPDATE rescue_units SET status = CASE WHEN status = 'OFFLINE' THEN 'OFFLINE' ELSE 'IDLE' END,
        updated_at = ? WHERE id = ? AND changes() = 1`, args: [timestamp, assignment.unit_id],
    }] : []),
    ...(nextIncident ? [
      statusUpdate(current.id, nextIncident, timestamp, `changes() = 1 AND ${nextAllowed}`, status === 'CANCELLED' ? [current.id] : []),
      history(current.id, nextIncident, user.id, `Dispatch ${dispatchId} moved to ${status}`),
    ] : []),
  ], 'write');
  if (!updated.rowsAffected) throw new HttpError(409, 'Dispatch changed. Refresh and try again.');
  return result();
}

async function messages(db, request, user, incidentId) {
  if (request.method === 'GET') {
    return json(await rows(db, `${messageSelect} WHERE m.incident_id = ? ORDER BY m.sent_at, m.id`, [incidentId]));
  }
  if (request.method !== 'POST') return;
  const body = await readBody(request);
  const text = body.custom_message == null ? null : string(body.custom_message, 'message', 2000);
  const catalog = body.predefined_message_id == null ? null : id(body.predefined_message_id);
  if (!text && !catalog) throw new HttpError(400, 'A message or catalog entry is required');
  const clientId = body.client_message_id == null ? crypto.randomUUID() : string(body.client_message_id, 'client_message_id', 128);
  const receiver = body.receiver_user_id == null ? null : id(body.receiver_user_id);
  const channel = oneOf(body.channel ?? 'ONLINE', ['ONLINE', 'BLE'], 'channel');
  const [inserted, selected] = await db.batch([
    { sql: `INSERT INTO incident_messages (incident_id, sender_user_id, receiver_user_id,
        predefined_message_id, custom_message, channel, client_message_id)
        SELECT ?, ?, ?, ?, ?, ?, ? WHERE ? IS NULL OR EXISTS (SELECT 1 FROM predefined_messages WHERE id = ? AND is_active = 1)
        ON CONFLICT DO NOTHING`,
      args: [incidentId, user.id, receiver, catalog, text, channel, clientId, catalog, catalog] },
    { sql: `${messageSelect} WHERE m.incident_id = ? AND m.sender_user_id = ? AND m.client_message_id = ?`,
      args: [incidentId, user.id, clientId] },
  ], 'write');
  return json(requireRow(selected.rows[0], 'Active catalog entry not found'), inserted.rowsAffected ? 201 : 200);
}

export async function incidentRoute(path, request, db, user, params) {
  const method = request.method;
  if (path === '/incidents') {
    if (method === 'GET') return list(db, params, user);
    if (method === 'POST') {
      if (!isAdmin(user)) deny();
      return create(db, request, user);
    }
  }
  const bulk = path.match(/^\/incidents\/(status-history|dispatches)$/);
  if (bulk && method === 'GET') {
    if (isCitizen(user)) deny();
    const identifiers = (params.get('incident_ids') || '').split(',').filter(Boolean);
    if (identifiers.length > 200) throw new HttpError(400, 'At most 200 incidents can be queried at once');
    const ids = [...new Set(identifiers.map(id))];
    if (!ids.length) return json({});
    const dispatches = bulk[1] === 'dispatches';
    const select = dispatches ? dispatchSelect : 'SELECT * FROM incident_status_history';
    const prefix = dispatches ? 'd.' : '';
    const order = dispatches ? 'd.dispatched_at DESC, d.id DESC' : 'created_at, id';
    const items = await rows(db, `${select} WHERE ${prefix}incident_id IN (${marks(ids)}) ORDER BY ${order}`, ids);
    const grouped = Object.fromEntries(ids.map((key) => [key, []]));
    for (const item of items) grouped[item.incident_id].push(item);
    return json(grouped);
  }
  const match = path.match(/^\/incidents\/(\d+)(?:\/(status|status-history|dispatches|reports|messages|dispatch|location|available-rescue-units)(?:\/(\d+)\/status)?)?$/);
  if (!match) return;
  const incidentId = id(match[1]);
  const current = requireRow(await first(db,
    `SELECT i.* FROM incidents i WHERE i.id = ? ${isCitizen(user) ? `AND ${citizenIncidentScope}` : ''}`,
    [incidentId, ...(isCitizen(user) ? [user.id, user.id] : [])]), 'Incident not found');
  if (match[3]) {
    if (method === 'PUT' && match[2] === 'dispatches') return updateDispatch(db, request, user, current, id(match[3]));
    return;
  }
  switch (match[2]) {
    case undefined: if (method === 'GET') return json(incident(current)); break;
    case 'status': if (method === 'PUT') return updateStatus(db, request, user, current); break;
    case 'dispatch': if (method === 'POST') return dispatch(db, request, user, current); break;
    case 'status-history':
      if (method === 'GET') return json(await rows(db, 'SELECT * FROM incident_status_history WHERE incident_id = ? ORDER BY created_at, id', [incidentId]));
      break;
    case 'dispatches':
      if (method === 'GET') return json(await rows(db, `${dispatchSelect} WHERE d.incident_id = ? ORDER BY d.dispatched_at DESC, d.id DESC`, [incidentId]));
      break;
    case 'messages':
      await requireIncidentAssignment(db, user, incidentId, { activeOnly: true });
      return messages(db, request, user, incidentId);
    case 'available-rescue-units':
      if (!isAdmin(user)) deny();
      if (method === 'GET') {
        const agencies = agencyMatch[current.incident_type] || [];
        if (!agencies.length) return json([]);
        return json(await rows(db, `${unitSelect} JOIN agencies a ON a.id = u.agency_id
          WHERE a.is_active = 1 AND a.type IN (${marks(agencies)}) AND u.status = 'IDLE'
          AND NOT EXISTS (SELECT 1 FROM incident_dispatches WHERE unit_id = u.id AND status IN (${activeDispatches}))
          ORDER BY u.unit_code`, agencies));
      }
      break;
    case 'reports':
      if (isCitizen(user)) deny();
      if (method === 'GET') {
        const reports = await rows(db, 'SELECT * FROM incident_reports WHERE incident_id = ? ORDER BY id', [incidentId]);
        return json({ success: true, reports: reports.map((row) => withBooleans(row, 'immediate_danger')) });
      }
      break;
    case 'location':
      if (method === 'PUT') {
        if (!isCitizen(user)) await requireIncidentAssignment(db, user, incidentId);
        const body = await readBody(request);
        const updated = await query(db, `UPDATE incidents SET latitude = ?, longitude = ?, updated_at = ?
          WHERE id = ? AND status NOT IN (${terminal})`,
        [number(body.latitude, 'latitude', -90, 90), number(body.longitude, 'longitude', -180, 180), now(), incidentId]);
        if (!updated.rowsAffected) throw new HttpError(409, 'Incident is closed');
        return json({ status: 'ok', incident_id: incidentId });
      }
      break;
  }
}

export async function unitRoute(path, request, db, user) {
  const own = path.match(/^\/(?:rescue-units|responders)\/me(\/dispatches)?$/);
  if (own && request.method === 'GET') {
    const unit = await ownUnit(db, user);
    if (own[1]) return json(unit ? await rows(db, `${dispatchSelect} WHERE d.unit_id = ? ORDER BY d.id DESC`, [unit.id]) : []);
    if (!unit) throw new HttpError(404, 'No rescue unit assigned');
    return json(await first(db, `${unitSelect} WHERE u.id = ?`, [unit.id]));
  }
  const match = path.match(/^\/(?:rescue-units|responders)\/(\d+)\/(members|dispatches|location)(?:\/(\d+))?$/);
  if (!match) return;
  const unit = requireRow(await first(db, 'SELECT * FROM rescue_units WHERE id = ?', [id(match[1])]), 'Responder unit not found');
  const method = request.method;
  if (match[2] === 'dispatches' && method === 'GET' && !match[3]) {
    return json(await rows(db, `${dispatchSelect} WHERE d.unit_id = ? ORDER BY d.dispatched_at DESC, d.id DESC`, [unit.id]));
  }
  if (match[2] === 'location' && method === 'PUT' && !match[3]) {
    await requireUnit(db, user, unit.id);
    const body = await readBody(request);
    const status = body.status == null || body.status === '' ? null : oneOf(body.status, ['IDLE', 'DISPATCHED', 'OFFLINE'], 'unit status');
    const timestamp = now();
    const [updated] = await db.batch([
      { sql: `UPDATE rescue_units SET current_latitude = ?, current_longitude = ?, status = COALESCE(?, status),
        last_location_at = ?, updated_at = ? WHERE id = ? AND
        (? IS NULL OR status = ? OR (? <> 'DISPATCHED' AND NOT EXISTS (
          SELECT 1 FROM incident_dispatches WHERE unit_id = ? AND status IN (${activeDispatches}))))`,
        args: [number(body.latitude, 'latitude', -90, 90), number(body.longitude, 'longitude', -180, 180),
          status, timestamp, timestamp, unit.id, status, status, status, unit.id] },
      audit(user.id, 'UPDATE_RESPONDER_LOCATION', 'RESCUE_UNIT', unit.id),
    ], 'write');
    if (!updated.rowsAffected) throw new HttpError(409, 'Finish the active dispatch before changing unit status');
    return json({ status: 'ok', responder_id: unit.id });
  }
  if (match[2] !== 'members') return;
  if (method === 'GET' && !match[3]) {
    return json((await rows(db, `SELECT * FROM rescue_unit_members WHERE rescue_unit_id = ? AND removed_at IS NULL
      ORDER BY is_leader DESC, id`, [unit.id])).map((row) => withBooleans(row, 'is_leader')));
  }
  if (method === 'POST' && !match[3]) {
    const body = await readBody(request);
    const responderId = id(body.responder_id);
    if (typeof body.is_leader !== 'boolean') throw new HttpError(400, 'Invalid is_leader');
    const [inserted] = await db.batch([
      { sql: `INSERT INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader)
        SELECT ?, id, ? FROM users WHERE id = ? AND role = 'RESPONDER' AND account_status = 'APPROVED'
        AND NOT EXISTS (SELECT 1 FROM rescue_unit_members WHERE rescue_unit_id = ? AND responder_id = ? AND removed_at IS NULL)
        RETURNING *`, args: [unit.id, Number(body.is_leader), responderId, unit.id, responderId] },
      audit(user.id, 'ADD_RESCUE_UNIT_MEMBER', 'RESCUE_UNIT', unit.id),
    ], 'write');
    if (!inserted.rows.length) throw new HttpError(409, 'Select an approved responder who is not already assigned to this unit');
    return json(withBooleans(inserted.rows[0], 'is_leader'), 201);
  }
  if (method === 'DELETE' && match[3]) {
    const [updated] = await db.batch([
      { sql: 'UPDATE rescue_unit_members SET removed_at = ? WHERE rescue_unit_id = ? AND responder_id = ? AND removed_at IS NULL',
        args: [now(), unit.id, id(match[3])] },
      audit(user.id, 'REMOVE_RESCUE_UNIT_MEMBER', 'RESCUE_UNIT', unit.id),
    ], 'write');
    if (!updated.rowsAffected) throw new HttpError(404, 'Member not found');
    return noContent();
  }
}
