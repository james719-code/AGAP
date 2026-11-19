import { publicUser, userColumns } from './auth.mjs';
import { activeDispatches } from './access.mjs';
import {
  HttpError, boolean, first, id, json, marks, noContent, now, number, oneOf, pageNumber,
  query, readBody, requireRow, rows, string, withBooleans,
} from './http.mjs';

export const roles = ['CITIZEN', 'RESPONDER', 'ADMIN'];
export const accountStatuses = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'];
const agencyTypes = ['RESCUE', 'DRRMO', 'POLICE', 'FIRE', 'MEDICAL', 'VOLUNTEER'];
const unitStatuses = ['IDLE', 'OFFLINE', 'DISPATCHED'];
export const text = (max = 255, optional = true) => (value, field) => string(value, field, max, optional);
export const enumeration = (values) => (value, field) => oneOf(value, values, field);
const bounded = (min, max, integer = false) => (value, field) => number(value, field, min, max, integer);
export const optionalId = (value) => value == null ? null : id(value);
const latitude = bounded(-90, 90);
const longitude = bounded(-180, 180);
export const profileFields = {
  name: text(), email: text(), birthday: text(40), gender: text(40), phone_number: text(64),
  medical_info: text(4000), image_url: text(2048), address_id: optionalId,
  national_id: text(128),
};
export const contactFields = { contact_name: text(255, false), phone_number: text(64, false), relationship: text(120) };

export const registries = {
  '/agencies': {
    table: 'agencies', entity: 'AGENCY', booleans: ['is_active'],
    required: ['code', 'name', 'type', 'region_code', 'province_code', 'muni_code', 'barangay_code'],
    fields: {
      code: text(64, false), name: text(255, false), type: enumeration(agencyTypes),
      region_code: text(16, false), province_code: text(16, false), muni_code: text(16, false),
      barangay_code: text(16, false), contact_number: text(64), latitude, longitude,
      is_active: boolean, operating_hours: text(),
    },
  },
  '/responders': {
    table: 'rescue_units', entity: 'RESCUE_UNIT',
    columns: `id, agency_id, user_id, target_id, unit_code AS unit_callsign, device_id, status,
      current_latitude AS current_lat, current_longitude AS current_lon, last_location_at AS last_seen_at,
      responder_name, responder_phone, created_at, updated_at`,
    aliases: { unit_callsign: 'unit_code', current_lat: 'current_latitude', current_lon: 'current_longitude' },
    required: ['agency_id', 'target_id', 'unit_callsign'],
    fields: {
      agency_id: id, user_id: optionalId, target_id: text(128, false), unit_callsign: text(120, false),
      device_id: text(128), status: enumeration(unitStatuses), current_lat: latitude, current_lon: longitude,
      responder_name: text(), responder_phone: text(64),
    },
  },
  '/emergency-types': {
    table: 'emergency_types', entity: 'EMERGENCY_TYPE', booleans: ['is_active'],
    required: ['code', 'name'],
    fields: {
      code: text(64, false), name: text(255, false), description: text(4000), default_priority: bounded(1, 5, true),
      protocol_code: bounded(0, 255, true), target_agency_type: (value) => value == null || value === ''
        ? null : oneOf(value, agencyTypes, 'target_agency_type'), is_active: boolean,
    },
  },
  '/messages/catalog': {
    table: 'predefined_messages', entity: 'PREDEFINED_MESSAGE', booleans: ['is_active'],
    required: ['code', 'message_en', 'message_type'],
    fields: {
      code: text(64, false), protocol_code: bounded(0, 255, true), message_en: text(2000, false),
      message_fil: text(2000), message_bikol: text(2000), message_type: enumeration(['SOS', 'ACK', 'STATUS', 'ANNOUNCEMENT']),
      emergency_type_id: optionalId, is_active: boolean,
    },
  },
};

registries['/rescue-units'] = registries['/responders'];

export function valuesFor(body, fields, required = [], aliases = {}) {
  for (const field of required) {
    if (body[field] === undefined) throw new HttpError(400, `${field} is required`);
  }
  return Object.entries(fields).flatMap(([field, validate]) => body[field] === undefined
    ? [] : [[aliases[field] || field, validate(body[field], field)]]);
}

export function audit(actor, action, entity, entityId, { inserted = false, detail = null } = {}) {
  return {
    sql: `INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, new_value)
      SELECT ?, ?, ?, ${inserted ? 'last_insert_rowid()' : '?'}, ? WHERE changes() > 0`,
    args: [actor, action, entity, ...(inserted ? [] : [entityId]), detail],
  };
}

function update(table, values, where, args, columns = '*') {
  return {
    sql: `UPDATE ${table} SET ${values.map(([field]) => `${field} = ?`).join(', ')} WHERE ${where} RETURNING ${columns}`,
    args: [...values.map(([, value]) => value), ...args],
  };
}

export async function registryRoute(path, request, db, user, params) {
  const method = request.method;
  const base = path.replace(/^\/admin(?=\/)/, '').replace(/\/\d+$/, '');
  const config = registries[base];
  if (!config) return;
  const key = path.match(/\/(\d+)$/)?.[1];
  const recordId = key === undefined ? null : id(key);
  const { table, entity, columns = '*', booleans = [], required, fields, aliases } = config;
  const convert = (row) => withBooleans(row, ...booleans);

  if (method === 'GET') {
    const filters = [];
    const args = [];
    if (recordId) {
      const row = await first(db, `SELECT ${columns} FROM ${table} WHERE id = ?`, [recordId]);
      return json(convert(requireRow(row)));
    }
    if (table === 'rescue_units' && params.has('agency_id')) {
      filters.push('agency_id = ?');
      args.push(id(params.get('agency_id')));
    }
    if (['/emergency-types', '/messages/catalog'].includes(base) &&
        (user?.role !== 'ADMIN' || params.get('include_inactive') !== 'true')) {
      filters.push('is_active = 1');
    }
    const where = filters.length ? ` WHERE ${filters.join(' AND ')}` : '';
    const result = await rows(db, `SELECT ${columns} FROM ${table}${where} ORDER BY id`, args);
    return json(result.map(convert));
  }

  const deleting = method === 'DELETE' && recordId;
  const creating = method === 'POST' && !recordId;
  if (!deleting && !creating && !(method === 'PUT' && recordId)) return;
  let statement;
  if (deleting) {
    const unitCondition = table === 'rescue_units'
      ? ` AND NOT EXISTS (SELECT 1 FROM incident_dispatches WHERE unit_id = ? AND status IN (${activeDispatches}))`
      : table === 'agencies'
        ? ` AND NOT EXISTS (SELECT 1 FROM rescue_units u JOIN incident_dispatches d ON u.id = d.unit_id
          WHERE u.agency_id = ? AND d.status IN (${activeDispatches}))` : '';
    statement = { sql: `DELETE FROM ${table} WHERE id = ?${unitCondition} RETURNING id`,
      args: unitCondition ? [recordId, recordId] : [recordId] };
  } else {
    const values = valuesFor(await readBody(request), fields, creating ? required : [], aliases);
    if (!values.length) throw new HttpError(400, 'No editable fields supplied');
    if (table === 'rescue_units') {
      const status = values.find(([field]) => field === 'status')?.[1];
      if (creating && status === 'DISPATCHED') throw new HttpError(409, 'Assign the unit through an incident dispatch');
      const assignedUser = values.find(([field]) => field === 'user_id')?.[1];
      if (assignedUser && !await first(db, "SELECT id FROM users WHERE id = ? AND role = 'RESPONDER'", [assignedUser])) {
        throw new HttpError(400, 'Select a responder account');
      }
    }
    if (creating) {
      statement = {
        sql: `INSERT INTO ${table} (${values.map(([field]) => field).join(', ')})
          VALUES (${marks(values)}) RETURNING ${columns}`,
        args: values.map(([, value]) => value),
      };
    } else {
      const status = values.find(([field]) => field === 'status')?.[1];
      const guard = table === 'rescue_units' && status
        ? ` AND (status = ? OR (? <> 'DISPATCHED' AND NOT EXISTS (
          SELECT 1 FROM incident_dispatches WHERE unit_id = ? AND status IN (${activeDispatches}))))` : '';
      statement = update(table, [...values, ['updated_at', now()]], `id = ?${guard}`,
        guard ? [recordId, status, status, recordId] : [recordId], columns);
    }
  }
  const action = deleting ? 'DELETE' : creating ? 'CREATE' : 'UPDATE';
  const [result] = await db.batch([statement, audit(user.id, action, entity, recordId, { inserted: creating })], 'write');
  if (!result.rows.length) {
    if (await first(db, `SELECT id FROM ${table} WHERE id = ?`, [recordId])) {
      throw new HttpError(409, 'This record has an active dispatch');
    }
    throw new HttpError(404, 'Record not found');
  }
  return deleting ? noContent() : json(convert(result.rows[0]), creating ? 201 : 200);
}

export async function accountRoute(path, request, db, user, params) {
  const method = request.method;
  if (path === '/users/me') {
    if (method === 'GET') return json(user);
    if (method === 'PUT') {
      const values = valuesFor(await readBody(request), profileFields);
      if (!values.length) return json(user);
      const [result] = await db.batch([
        update('users', [...values, ['updated_at', now()]], 'id = ?', [user.id], userColumns),
        audit(user.id, 'UPDATE', 'USER', user.id),
      ], 'write');
      return json(publicUser(requireRow(result.rows[0])));
    }
    return;
  }
  if (path === '/users' && method === 'GET') {
    const role = params.get('role');
    return json((await rows(db, `SELECT ${userColumns} FROM users ${role ? 'WHERE role = ?' : ''} ORDER BY id`,
      role ? [oneOf(role, roles, 'role')] : [])).map(publicUser));
  }
  const admin = path.match(/^\/admin\/users\/(\d+)(?:\/(status|verify))?$/);
  if (admin) {
    const target = id(admin[1]);
    if (method === 'PUT' && admin[2]) {
      const body = admin[2] === 'status' ? await readBody(request) : { status: 'APPROVED' };
      const status = oneOf(body.status, accountStatuses, 'account status');
      if (target === user.id && status !== 'APPROVED') throw new HttpError(409, 'You cannot suspend or unverify your own account');
      const timestamp = now();
      const [result] = await db.batch([
        { sql: 'UPDATE users SET account_status = ?, is_verified = ?, updated_at = ? WHERE id = ?',
          args: [status, Number(status === 'APPROVED'), timestamp, target] },
        { sql: `INSERT INTO account_verifications (user_id, status, reviewed_by, reviewed_at, remarks)
          SELECT ?, ?, ?, ?, ? WHERE changes() = 1`,
          args: [target, status, user.id, timestamp, body.remarks == null ? '' : string(body.remarks, 'remarks', 4000, true)] },
        audit(user.id, 'UPDATE_STATUS', 'USER', target, { detail: JSON.stringify({ status }) }),
        { sql: "DELETE FROM hosted_sessions WHERE user_id = ? AND ? <> 'APPROVED'", args: [target, status] },
      ], 'write');
      if (!result.rowsAffected) throw new HttpError(404, 'User not found');
      return json({ message: 'Account updated', user_id: target, status });
    }
    if (admin[2]) return;
    if (method === 'GET') return json(publicUser(requireRow(await first(db, `SELECT ${userColumns} FROM users WHERE id = ?`, [target]))));
    if (method === 'PUT') {
      const values = valuesFor(await readBody(request), {
        ...profileFields, role: enumeration(roles), agency_id: optionalId,
      });
      if (target === user.id && values.some(([field, value]) => field === 'role' && value !== 'ADMIN')) {
        throw new HttpError(409, 'You cannot remove your own admin role');
      }
      const [result] = await db.batch([
        update('users', [...values, ['updated_at', now()]], 'id = ?', [target], userColumns),
        audit(user.id, 'UPDATE', 'USER', target),
      ], 'write');
      return json(publicUser(requireRow(result.rows[0])));
    }
    if (method === 'DELETE') {
      if (target === user.id) throw new HttpError(409, 'You cannot delete your own account');
      const [result] = await db.batch([
        { sql: 'DELETE FROM users WHERE id = ?', args: [target] },
        audit(user.id, 'DELETE', 'USER', target),
      ], 'write');
      if (!result.rowsAffected) throw new HttpError(404, 'User not found');
      return noContent();
    }
    return;
  }

  const owned = path.match(/^\/users\/me\/(contacts|devices)(?:\/(\d+)(\/primary)?)?$/);
  if (!owned) return;
  const isContact = owned[1] === 'contacts';
  const table = isContact ? 'user_emergency_contacts' : 'user_devices';
  const convert = (row) => isContact ? withBooleans(row, 'is_primary') : row;
  const recordId = owned[2] ? id(owned[2]) : null;
  if (method === 'GET' && !recordId) {
    return json((await rows(db, `SELECT * FROM ${table} WHERE user_id = ? ORDER BY id`, [user.id])).map(convert));
  }
  if (method === 'POST' && !recordId) {
    const body = await readBody(request);
    const values = isContact ? valuesFor(body, contactFields, ['contact_name', 'phone_number'])
      : valuesFor(body, { device_hardware_id: text(128, false), device_name: text(), platform: text(64) }, ['device_hardware_id']);
    const fields = values.map(([field]) => field).join(', ');
    const args = [user.id, ...values.map(([, value]) => value)];
    const sql = isContact
      ? `INSERT INTO ${table} (user_id, ${fields}, is_primary) SELECT ${marks(args)},
        NOT EXISTS (SELECT 1 FROM ${table} WHERE user_id = ?) RETURNING *`
      : `INSERT INTO ${table} (user_id, ${fields}) VALUES (${marks(args)}) RETURNING *`;
    if (isContact) args.push(user.id);
    return json(convert(await first(db, sql, args)), 201);
  }
  if (method === 'PUT' && isContact && recordId) {
    if (owned[3]) {
      // Clear the current primary first so the partial unique index is respected.
      const [, result] = await db.batch([
        { sql: `UPDATE ${table} SET is_primary = 0 WHERE user_id = ?
          AND EXISTS (SELECT 1 FROM ${table} WHERE user_id = ? AND id = ?)`, args: [user.id, user.id, recordId] },
        { sql: `UPDATE ${table} SET is_primary = 1 WHERE user_id = ? AND id = ? RETURNING *`, args: [user.id, recordId] },
      ], 'write');
      return json(convert(requireRow(result.rows[0])));
    }
    const values = valuesFor(await readBody(request), contactFields);
    if (!values.length) throw new HttpError(400, 'No editable fields supplied');
    const statement = update(table, values, 'user_id = ? AND id = ?', [user.id, recordId]);
    return json(convert(requireRow((await db.execute(statement)).rows[0])));
  }
  if (method === 'DELETE' && recordId && !owned[3]) {
    const [result] = await db.batch([
      { sql: `DELETE FROM ${table} WHERE user_id = ? AND id = ?`, args: [user.id, recordId] },
      ...(isContact ? [{
        sql: `UPDATE ${table} SET is_primary = 1 WHERE changes() > 0 AND user_id = ?
          AND id = (SELECT MIN(id) FROM ${table} WHERE user_id = ?)
          AND NOT EXISTS (SELECT 1 FROM ${table} WHERE user_id = ? AND is_primary = 1)`,
        args: [user.id, user.id, user.id],
      }] : []),
    ], 'write');
    if (!result.rowsAffected) throw new HttpError(404, 'Record not found');
    return noContent();
  }
}

export async function directoryRoute(path, request, db, user, params) {
  if (path === '/admin/audit-logs' && request.method === 'GET') {
    const filters = [];
    const args = [];
    for (const [field, column, operator] of [
      ['entity_type', 'entity_type', '='], ['entity_id', 'entity_id', '='], ['action', 'action', '='],
      ['date_from', 'created_at', '>='], ['date_to', 'created_at', '<='],
    ]) {
      if (!params.has(field)) continue;
      filters.push(`${column} ${operator} ?`);
      args.push(field === 'entity_id' ? id(params.get(field)) : params.get(field));
    }
    const where = filters.length ? `WHERE ${filters.join(' AND ')}` : '';
    return json(await rows(db, `SELECT * FROM audit_logs ${where} ORDER BY created_at DESC, id DESC LIMIT ?`,
      [...args, pageNumber(params.get('limit'), 20, 1, 200)]));
  }
  if (path === '/notifications' && request.method === 'GET') {
    const limit = pageNumber(params.get('limit'), 200, 1, 200);
    const offset = pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER);
    return json((await rows(db, `SELECT * FROM notifications WHERE user_id = ?
      ${params.get('unread') === 'true' ? 'AND is_read = 0' : ''} ORDER BY sent_at DESC, id DESC LIMIT ? OFFSET ?`,
      [user.id, limit, offset]))
      .map((row) => withBooleans(row, 'is_read')));
  }
  const read = path.match(/^\/notifications\/(\d+)\/read$/);
  if (read && request.method === 'PUT') {
    const result = await query(db, 'UPDATE notifications SET is_read = 1, read_at = COALESCE(read_at, ?) WHERE user_id = ? AND id = ?',
      [now(), user.id, id(read[1])]);
    if (!result.rowsAffected) throw new HttpError(404, 'Notification not found');
    return noContent();
  }
  const geography = {
    '/geo/provinces': ['provinces', null, 'name'],
    '/geo/municipalities': ['municipalities', 'province_id', 'name'],
    '/geo/barangays': ['barangays', 'municipality_id', 'name'],
    '/geo/addresses': ['addresses', 'barangay_id', 'street_address'],
  }[path];
  if (geography && request.method === 'GET') {
    const [table, parent, order] = geography;
    const filter = parent && params.get(parent);
    return json(await rows(db, `SELECT * FROM ${table} ${filter ? `WHERE ${parent} = ?` : ''} ORDER BY ${order}`,
      filter ? [id(filter)] : []));
  }
}
