import { deny, isAdmin, isCitizen } from './access.mjs';
import { eq } from './firestore-auth.mjs';
import { createFirestoreIncident, firestoreDispatches, incidentView, openStatuses, ownFirestoreUnit, scope } from './firestore-incidents.mjs';
import { date } from './incidents.mjs';
import { reportInput, reportKey, reportRow, uplinkInput } from './mobile.mjs';
import { HttpError, boolean, id, json, noContent, now, number, oneOf, pageNumber, readBody, string, withBooleans } from './http.mjs';

const byId = [['id', 'asc']];
const newest = (field) => [[field, 'desc'], ['id', 'desc']];
const num = (value, field, min, max) => value == null ? null : number(value, field, min, max);
const meshTypes = ['CREATED', 'RECEIVED', 'RELAYED', 'TARGET_REACHED', 'UPLINKED', 'DUPLICATE_DROPPED'];

async function acceptPacket(tx, packet, create = true) {
  const { uuid, key, target, type, channel, delivery, latitude, longitude, altitude, accuracy,
    hops, ttl, device, observed, owner, description, packetId } = packet;
  const prior = await tx.find('mesh_packets', [eq('packet_uuid', packetId)]);
  let current = prior?.incident_id ? await tx.get('incidents', prior.incident_id) : null;
  current ||= await tx.find('incidents', [eq('packet_hash', key)]) || await tx.find('incidents', [eq('incident_uuid', uuid)]);
  const deduplicated = Boolean(current);
  if (!current && create) {
    const [spatial, suffix] = target.split('::');
    const [region = '', province = '', municipality = '', barangay = '', zone = ''] = suffix ? spatial.split('-') : [];
    current = await createFirestoreIncident(tx, { incident_uuid: uuid, packet_hash: key, target_id_raw: target,
      region_code: region, province_code: province, municipality_code: municipality, barangay_code: barangay, zone_code: zone,
      incident_type: type, channel, delivery_path: delivery, latitude, longitude, altitude, gps_accuracy: accuracy,
      hop_count: hops, ttl, sender_device_id: device, event_timestamp: observed, citizen_id: owner, description, received_at: now() });
  }
  if (current) {
    if (!prior) await tx.insert('mesh_packets', { packet_uuid: packetId, packet_type: 'SOS', incident_id: current.id,
      origin_user_id: owner, origin_device_identifier: device, payload_hash: key, created_at: observed, server_received_at: now() });
    else if (prior.incident_id == null && prior.packet_type === 'SOS') await tx.update('mesh_packets', prior, {
      incident_id: current.id, payload_hash: key, server_received_at: prior.server_received_at ?? now(),
    });
  }
  return { current, deduplicated };
}

async function uplink(db, body, user) {
  const packet = await uplinkInput(body, user);
  const { current, deduplicated } = await db.transaction((tx) => acceptPacket(tx, packet));
  if (!current) throw new HttpError(409, 'Conflicting packet identity');
  return { success: true, status: deduplicated ? 'deduplicated' : 'accepted', deduplicated,
    ...(isAdmin(user) || current.citizen_id === user.id ? { incident: incidentView({ ...current, packet_uuid: packet.packetId }) } : {}) };
}

async function submitReport(db, body, user) {
  const { report, packet } = reportInput(body, user);
  const { uuid, key, owner, device } = packet;
  const row = await db.transaction(async (tx) => {
    let existing = await tx.find('incident_reports', [eq('transport_token', key)]) ||
      await tx.find('incident_reports', [eq('report_uuid', uuid)]);
    if (existing?.reporter_user_id != null && owner && existing.reporter_user_id !== owner) deny('This report belongs to another account');
    const { current } = await acceptPacket(tx, packet, !existing);
    let linked = existing?.incident_id ? await tx.get('incidents', existing.incident_id) : current;
    if (!existing) {
      if (!linked || (linked.citizen_id != null && linked.citizen_id !== owner)) deny('This report belongs to another account');
      existing = await tx.insert('incident_reports', { ...report, incident_id: linked.id });
    } else if (owner && existing.reporter_user_id == null && existing.transport_token === key &&
        (existing.report_uuid === uuid || existing.detail_state === 'MINIMAL') && linked &&
        (linked.citizen_id == null || linked.citizen_id === owner)) {
      const knownDevice = !linked.sender_device_id || linked.sender_device_id === device || await tx.find('user_devices',
        [eq('user_id', owner), eq('device_hardware_id', linked.sender_device_id)]);
      if (knownDevice) existing = await tx.update('incident_reports', existing, {
        reporter_user_id: owner, reporter_device_id: device, description: report.description, subtype: report.subtype,
        subject_relation: report.subject_relation, people_affected: report.people_affected, immediate_danger: report.immediate_danger,
        detail_state: 'FULL',
      });
    }
    if (owner && existing.reporter_user_id !== owner) deny('This report belongs to another account');
    if (owner && linked && !linked.visible_to?.includes(owner)) {
      linked = await tx.update('incidents', linked, { citizen_id: linked.citizen_id ?? owner,
        visible_to: [...(linked.visible_to || []), owner], updated_at: now() });
    }
    return existing;
  });
  return json({ success: true, report: isAdmin(user) || owner ? reportRow(row)
    : { report_uuid: row.report_uuid, transport_token: row.transport_token, incident_id: row.incident_id } }, 201);
}

async function meshEvents(db, events, user) {
  if (!Array.isArray(events) || events.length > 200) throw new HttpError(400, 'Send at most 200 mesh events per request');
  const device = user.device_id || `account:${user.id}`;
  const inputs = events.filter((event) => event && meshTypes.includes(event.event_type)).map((event) => {
    const key = reportKey(event.packet_uuid || event.packet_hash, 'packet identity');
    const kind = oneOf(event.packet_type || 'SOS', ['SOS', 'ACK', 'STATUS'], 'packet_type');
    return { key, kind, packetId: kind === 'SOS' ? key : `${kind}:${key}`, values: {
      device_identifier: device, event_type: event.event_type, latitude: num(event.latitude, 'latitude', -90, 90),
      longitude: num(event.longitude, 'longitude', -180, 180), recorded_at: event.recorded_at ? date(event.recorded_at, 'recorded_at') : now(),
    } };
  });
  let recorded = 0;
  for (let offset = 0; offset < inputs.length; offset += 25) {
    const chunk = inputs.slice(offset, offset + 25);
    recorded += await db.transaction(async (tx) => {
      let count = 0;
      for (const { key, kind, packetId, values } of chunk) {
        let packet = await tx.find('mesh_packets', [eq('packet_uuid', packetId)]);
        packet ||= await tx.insert('mesh_packets', { packet_uuid: packetId, packet_type: kind, payload_hash: key });
        const where = Object.entries({ packet_id: packet.id, device_identifier: device,
          event_type: values.event_type, recorded_at: values.recorded_at }).map(([field, value]) => eq(field, value));
        if (!await tx.find('mesh_delivery_events', where)) {
          await tx.insert('mesh_delivery_events', { packet_id: packet.id, ...values, synced_at: now() });
          count++;
        }
      }
      return count;
    });
  }
  return recorded;
}

async function sync(db, user, params, bootstrap) {
  const since = params.get('since') || '1970-01-01T00:00:00.000Z';
  const [value, rawId] = since.split('|');
  const cursor = [date(value, 'since'), rawId === undefined ? 0 : id(rawId)];
  const incidents = bootstrap
    ? db.list('incidents', { where: [...scope(user), ['status', 'in', openStatuses]], order: newest('received_at'), limit: 100 })
    : db.list('incidents', { where: scope(user), order: [['updated_at', 'asc'], ...byId], after: cursor, limit: 200 });
  const [records, catalog, types, messages] = await Promise.all([incidents,
    db.list('agencies', { order: byId }), db.list('emergency_types', { where: [eq('is_active', 1)], order: byId }),
    db.list('predefined_messages', { where: [eq('is_active', 1)], order: byId }),
  ]);
  const active = (items) => items.map((row) => withBooleans(row, 'is_active'));
  const catalogs = { agencies: active(catalog), emergency_types: active(types), predefined_messages: active(messages) };
  if (bootstrap) {
    const unit = user.role === 'RESPONDER' ? await ownFirestoreUnit(db, user) : null;
    return json({ ...catalogs, user, active_incidents: records.map(incidentView), assigned_dispatches: unit
      ? await firestoreDispatches(db, [eq('unit_id', unit.id)], [['id', 'desc']]) : [] });
  }
  const last = records.at(-1);
  return json({ ...catalogs, since, server_time: last ? `${last.updated_at}|${last.id}` : since, incidents: records.map(incidentView) });
}

async function alerts(path, request, db, user, params) {
  if (path === '/alerts' && request.method === 'GET') {
    const limit = pageNumber(params.get('limit'), 50, 1, 200);
    const offset = pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER);
    const includeInactive = isAdmin(user) && params.get('active_only') === 'false';
    const order = [['is_pinned', 'desc'], ['published_at', 'desc'], ['id', 'desc']];
    const [total, records] = includeInactive ? await Promise.all([db.count('admin_alerts'), db.list('admin_alerts', { order, limit, offset })])
      : await activeAlerts(db, order, limit, offset);
    const items = await Promise.all(records.map(async (row) => {
      const [author, read, count] = await Promise.all([db.get('users', row.author_id),
        db.find('admin_alert_reads', [eq('alert_id', row.id), eq('user_id', user.id)]),
        db.count('admin_alert_reads', [eq('alert_id', row.id)]),
      ]);
      return withBooleans({ ...row, author_name: author?.username ?? null, is_read: Boolean(read), read_count: count }, 'is_active', 'is_pinned');
    }));
    return json({ alerts: items, total, limit, offset });
  }
  if (path === '/alerts' && request.method === 'POST') {
    if (!isAdmin(user)) deny();
    const body = await readBody(request);
    const row = await db.transaction((tx) => tx.insert('admin_alerts', {
      author_id: user.id, title: string(body.title, 'title', 255), body: string(body.body, 'body', 5000),
      category: oneOf(body.category, ['WEATHER', 'EVACUATION', 'SAFETY', 'HEALTH', 'INFRASTRUCTURE', 'GENERAL'], 'category'),
      severity: oneOf(body.severity, ['INFO', 'WARNING', 'CRITICAL', 'EMERGENCY'], 'severity'),
      is_pinned: boolean(body.is_pinned ?? false, 'is_pinned'), predefined_message_id: body.predefined_message_id == null ? null : id(body.predefined_message_id),
      ...Object.fromEntries(['target_region_code', 'target_province_code', 'target_muni_code', 'target_barangay_code']
        .map((field) => [field, body[field] ? string(body[field], field, 16) : null])),
    }));
    return json(withBooleans(row, 'is_pinned', 'is_active'), 201);
  }
  const deletion = path.match(/^\/alerts\/(\d+)$/);
  if (deletion && request.method === 'DELETE') {
    if (!isAdmin(user)) deny();
    await db.transaction(async (tx) => {
      const alert = await tx.get('admin_alerts', id(deletion[1]));
      if (!alert) throw new HttpError(404, 'Alert not found');
      await tx.remove('admin_alerts', alert);
    });
    return noContent();
  }
}

async function activeAlerts(db, order, limit, offset) {
  const stamp = now();
  const where = [eq('is_active', 1)];
  const [permanent, temporary] = await Promise.all([
    db.count('admin_alerts', [...where, eq('expires_at', null)]), db.count('admin_alerts', [...where, ['expires_at', '>', stamp]]),
  ]);
  const total = permanent + temporary;
  if (offset >= total) return [total, []];
  const rows = [];
  let remaining = offset;
  let after;
  while (rows.length < limit) {
    const page = await db.list('admin_alerts', { where, order, after, limit: 100 });
    for (const row of page) {
      if (row.expires_at != null && row.expires_at <= stamp) continue;
      if (remaining) remaining--;
      else rows.push(row);
      if (rows.length === limit) break;
    }
    if (page.length < 100) break;
    after = order.map(([field]) => page.at(-1)[field]);
  }
  return [total, rows];
}

export async function firestoreMobile(path, request, db, user, params) {
  const method = request.method;
  if (method === 'GET' && path === '/sync/bootstrap') return sync(db, user, params, true);
  if (method === 'GET' && path === '/sync/changes') return sync(db, user, params, false);
  if (method === 'POST' && path === '/incident-reports') return submitReport(db, await readBody(request), user);
  if (method === 'POST' && path === '/telemetry/uplink') return json(await uplink(db, await readBody(request), user));
  if (method === 'POST' && path === '/mesh/events/batch') {
    return json({ status: 'accepted', recorded: await meshEvents(db, (await readBody(request)).events, user) });
  }
  if (method === 'POST' && path === '/sync/push') {
    const body = await readBody(request);
    const packets = body.telemetry_packets ?? [];
    if (!Array.isArray(packets) || packets.length > 20) throw new HttpError(400, 'Send at most 20 telemetry packets per request');
    const result = { status: 'accepted', telemetry_accepted: 0, telemetry_duplicate: 0, mesh_events_recorded: 0, errors: [] };
    for (const packet of packets) {
      try {
        if (!packet || typeof packet !== 'object') throw new HttpError(400, 'Invalid telemetry packet');
        const outcome = await uplink(db, packet, user);
        result[outcome.deduplicated ? 'telemetry_duplicate' : 'telemetry_accepted']++;
      } catch (error) {
        if (!(error instanceof HttpError)) throw error;
        result.errors.push(error.message);
      }
    }
    result.mesh_events_recorded = await meshEvents(db, body.mesh_events ?? [], user);
    if (result.errors.length) result.status = 'partial';
    return json(result);
  }
  if (method === 'GET' && path === '/users/me/incident-reports') {
    const reports = await db.list('incident_reports', { where: [eq('reporter_user_id', user.id)], order: [['id', 'desc']] });
    return json({ success: true, reports: reports.map(reportRow) });
  }
  if (method === 'GET' && /^\/users\/me\/incident-reports\/[^/]+$/.test(path)) {
    const uuid = reportKey(path.split('/').at(-1), 'report_uuid');
    const row = await db.find('incident_reports', [eq('report_uuid', uuid), eq('reporter_user_id', user.id)]);
    if (!row) throw new HttpError(404, 'Report not found');
    return json({ success: true, report: reportRow(row) });
  }
  if (path === '/alerts' || path.startsWith('/alerts/')) return alerts(path, request, db, user, params);
}
