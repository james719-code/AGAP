import { isAdmin } from './access.mjs';
import { eq, fields, safeUser } from './firestore-auth.mjs';
import { activeForUnit, unitView, writeAudit } from './firestore-incidents.mjs';
import { accountStatuses, contactFields, enumeration, optionalId, profileFields, registries, roles, text, valuesFor } from './registries.mjs';
import { HttpError, id, json, noContent, now, oneOf, pageNumber, readBody, requireRow, string, withBooleans } from './http.mjs';

const byId = [['id', 'asc']];
const newest = (field) => [[field, 'desc'], ['id', 'desc']];
const responderColumns = ['id', 'agency_id', 'user_id', 'target_id', 'unit_callsign', 'device_id', 'status', 'current_lat',
  'current_lon', 'last_seen_at', 'responder_name', 'responder_phone', 'created_at', 'updated_at'];

export async function firestoreRegistry(path, request, db, user, params) {
  const method = request.method;
  const base = path.replace(/^\/admin(?=\/)/, '').replace(/\/\d+$/, '');
  const config = registries[base];
  if (!config) return;
  const key = path.match(/\/(\d+)$/)?.[1];
  const recordId = key === undefined ? null : id(key);
  const { table, entity, booleans = [], required, fields: inputs, aliases } = config;
  const convert = (row) => table === 'rescue_units' ? fields(unitView(row), responderColumns) : withBooleans(row, ...booleans);
  if (method === 'GET') {
    if (recordId) return json(convert(requireRow(await db.get(table, recordId))));
    const where = [];
    if (table === 'rescue_units' && params.has('agency_id')) where.push(eq('agency_id', id(params.get('agency_id'))));
    if (['/emergency-types', '/messages/catalog'].includes(base) && (!isAdmin(user ?? {}) || params.get('include_inactive') !== 'true')) {
      where.push(eq('is_active', 1));
    }
    return json((await db.list(table, { where, order: byId })).map(convert));
  }
  const deleting = method === 'DELETE' && recordId;
  const creating = method === 'POST' && !recordId;
  if (!deleting && !creating && !(method === 'PUT' && recordId)) return;
  const input = deleting ? {} : Object.fromEntries(valuesFor(await readBody(request), inputs, creating ? required : [], aliases));
  if (!deleting && !Object.keys(input).length) throw new HttpError(400, 'No editable fields supplied');
  return db.transaction(async (tx) => {
    const row = creating ? null : requireRow(await tx.get(table, recordId));
    if (table === 'rescue_units') {
      if (creating && input.status === 'DISPATCHED') throw new HttpError(409, 'Assign the unit through an incident dispatch');
      if (input.user_id && (await tx.get('users', input.user_id))?.role !== 'RESPONDER') throw new HttpError(400, 'Select a responder account');
      if (row && (deleting || (input.status && input.status !== row.status)) &&
          (input.status === 'DISPATCHED' || await activeForUnit(tx, recordId))) throw new HttpError(409, 'This record has an active dispatch');
    }
    if (table === 'agencies' && deleting) {
      const units = await tx.list('rescue_units', { where: [eq('agency_id', recordId)] });
      for (const unit of units) if (await activeForUnit(tx, unit.id)) {
        throw new HttpError(409, 'This record has an active dispatch');
      }
    }
    if (deleting) {
      await tx.remove(table, row);
      await writeAudit(tx, user, 'DELETE', entity, recordId);
      return noContent();
    }
    const saved = creating ? await tx.insert(table, input) : await tx.update(table, row, { ...input, updated_at: now() });
    await writeAudit(tx, user, creating ? 'CREATE' : 'UPDATE', entity, saved.id);
    return json(convert(saved), creating ? 201 : 200);
  });
}

export async function firestoreAccounts(path, request, db, user, params) {
  const method = request.method;
  if (path === '/users/me' && method === 'GET') return json(user);
  if (path === '/users' && method === 'GET') {
    const role = params.get('role');
    return json((await db.list('users', { where: role ? [eq('role', oneOf(role, roles, 'role'))] : [], order: byId })).map(safeUser));
  }
  const admin = path.match(/^\/admin\/users\/(\d+)(?:\/(status|verify))?$/);
  if (admin || path === '/users/me') {
    const target = admin ? id(admin[1]) : user.id;
    if (method === 'GET' && !admin?.[2]) return json(safeUser(requireRow(await db.get('users', target))));
    if (method === 'PUT' && admin?.[2]) {
      const body = admin[2] === 'status' ? await readBody(request) : { status: 'APPROVED' };
      const status = oneOf(body.status, accountStatuses, 'account status');
      if (target === user.id && status !== 'APPROVED') throw new HttpError(409, 'You cannot suspend or unverify your own account');
      await db.transaction(async (tx) => {
        const row = requireRow(await tx.get('users', target), 'User not found');
        const updated_at = now();
        await tx.update('users', row, { account_status: status, is_verified: Number(status === 'APPROVED'), updated_at,
          session_version: (row.session_version ?? 0) + Number(status !== 'APPROVED') });
        await tx.insert('account_verifications', { user_id: target, status, reviewed_by: user.id, reviewed_at: updated_at,
          remarks: body.remarks == null ? '' : string(body.remarks, 'remarks', 4000, true) });
        await writeAudit(tx, user, 'UPDATE_STATUS', 'USER', target, JSON.stringify({ status }));
        if (status !== 'APPROVED') {
          const sessions = await tx.list('hosted_sessions', { where: [eq('user_id', target)], limit: 125 });
          for (const session of sessions) await tx.remove('hosted_sessions', session);
        }
      });
      return json({ message: 'Account updated', user_id: target, status });
    }
    if (method === 'PUT' && !admin?.[2]) {
      const allowed = admin ? { ...profileFields, role: enumeration(roles), agency_id: optionalId } : profileFields;
      const input = Object.fromEntries(valuesFor(await readBody(request), allowed));
      if (admin && target === user.id && input.role && input.role !== 'ADMIN') throw new HttpError(409, 'You cannot remove your own admin role');
      if (!admin && !Object.keys(input).length) return json(user);
      return db.transaction(async (tx) => {
        const current = requireRow(await tx.get('users', target));
        const saved = await tx.update('users', current, { ...input, updated_at: now() });
        await writeAudit(tx, user, 'UPDATE', 'USER', target);
        return json(safeUser(saved));
      });
    }
    if (method === 'DELETE' && admin && !admin[2]) {
      if (target === user.id) throw new HttpError(409, 'You cannot delete your own account');
      await db.transaction(async (tx) => {
        const visible = await tx.list('incidents', { where: [['visible_to', 'array-contains', target]], limit: 401 });
        for (const row of visible) {
          await tx.update('incidents', row, { visible_to: row.visible_to.filter((id) => id !== target) });
        }
        await tx.remove('users', requireRow(await tx.get('users', target), 'User not found'));
        await writeAudit(tx, user, 'DELETE', 'USER', target);
      });
      return noContent();
    }
  }
  const owned = path.match(/^\/users\/me\/(contacts|devices)(?:\/(\d+)(\/primary)?)?$/);
  if (!owned) return;
  const contact = owned[1] === 'contacts';
  const table = contact ? 'user_emergency_contacts' : 'user_devices';
  const convert = (row) => contact ? withBooleans(row, 'is_primary') : row;
  const owner = eq('user_id', user.id);
  if (method === 'GET' && !owned[2]) return json((await db.list(table, { where: [owner], order: byId })).map(convert));
  if (method === 'POST' && !owned[2]) {
    const inputs = contact ? contactFields : { device_hardware_id: text(128, false), device_name: text(), platform: text(64) };
    const body = Object.fromEntries(valuesFor(await readBody(request), inputs, contact ? ['contact_name', 'phone_number'] : ['device_hardware_id']));
    return db.transaction(async (tx) => {
      if (contact) {
        requireRow(await tx.get('users', user.id));
        body.is_primary = Number(!await tx.find(table, [owner]));
      }
      return json(convert(await tx.insert(table, { ...body, user_id: user.id })), 201);
    });
  }
  if (owned[2] && ((method === 'DELETE' && !owned[3]) || (method === 'PUT' && contact))) {
    const inputs = method === 'PUT' && !owned[3] ? Object.fromEntries(valuesFor(await readBody(request), contactFields)) : null;
    if (inputs && !Object.keys(inputs).length) throw new HttpError(400, 'No editable fields supplied');
    return db.transaction(async (tx) => {
      const row = await tx.get(table, id(owned[2]));
      if (row?.user_id !== user.id) throw new HttpError(404, 'Record not found');
      if (method === 'DELETE') {
        await tx.remove(table, row);
        if (contact && !await tx.find(table, [owner, eq('is_primary', 1)])) {
          const first = (await tx.list(table, { where: [owner], limit: 1, order: byId }))[0];
          if (first) await tx.update(table, first, { is_primary: 1 });
        }
        return noContent();
      }
      if (inputs) return json(convert(await tx.update(table, row, inputs)));
      const primaries = await tx.list(table, { where: [owner, eq('is_primary', 1)] });
      for (const primary of primaries) if (primary.id !== row.id) await tx.update(table, primary, { is_primary: 0 });
      return json(convert(await tx.update(table, row, { is_primary: 1 })));
    });
  }
}

export async function firestoreDirectory(path, request, db, user, params) {
  const method = request.method;
  if (path === '/admin/audit-logs' && method === 'GET') {
    const where = [];
    for (const field of ['entity_type', 'entity_id', 'action']) {
      if (params.has(field)) where.push(eq(field, field === 'entity_id' ? id(params.get(field)) : params.get(field)));
    }
    if (params.has('date_from')) where.push(['created_at', '>=', params.get('date_from')]);
    if (params.has('date_to')) where.push(['created_at', '<=', params.get('date_to')]);
    return json(await db.list('audit_logs', { where, order: newest('created_at'), limit: pageNumber(params.get('limit'), 20, 1, 200) }));
  }
  if (path === '/notifications' && method === 'GET') {
    const where = [eq('user_id', user.id)];
    if (params.get('unread') === 'true') where.push(eq('is_read', 0));
    const records = await db.list('notifications', { where, order: newest('sent_at'),
      limit: pageNumber(params.get('limit'), 200, 1, 200), offset: pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER) });
    return json(records.map((row) => withBooleans(row, 'is_read')));
  }
  const read = path.match(/^\/notifications\/(\d+)\/read$/);
  if (read && method === 'PUT') {
    await db.transaction(async (tx) => {
      const row = await tx.get('notifications', id(read[1]));
      if (row?.user_id !== user.id) throw new HttpError(404, 'Notification not found');
      await tx.update('notifications', row, { is_read: 1, read_at: row.read_at ?? now() });
    });
    return noContent();
  }
  const geo = { '/geo/provinces': ['provinces', null, 'name'], '/geo/municipalities': ['municipalities', 'province_id', 'name'],
    '/geo/barangays': ['barangays', 'municipality_id', 'name'], '/geo/addresses': ['addresses', 'barangay_id', 'street_address'] }[path];
  if (geo && method === 'GET') {
    const [table, parent, key] = geo;
    const filter = parent && params.get(parent);
    return json(await db.list(table, { where: filter ? [eq(parent, id(filter))] : [], order: [[key, 'asc']] }));
  }
}
