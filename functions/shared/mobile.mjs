import { citizenIncidentScope, deny, isAdmin, isCitizen, ownUnit } from './access.mjs';
import { hash } from './auth.mjs';
import { date, dispatchSelect, incident, terminal, types } from './incidents.mjs';
import {
  HttpError, boolean, first, id, json, marks, noContent, now, number, oneOf, pageNumber, query, readBody, rows,
  string, withBooleans,
} from './http.mjs';

const sqliteNow = "strftime('%Y-%m-%dT%H:%M:%fZ','now')";
const reportRow = (row) => withBooleans(row, 'immediate_danger');
const reportKey = (value, field) => {
  const key = string(value, field, 128).toLowerCase();
  if (!/^[a-z0-9_-]+$/.test(key)) throw new HttpError(400, `Invalid ${field}`);
  return key;
};
const incidentByKey = 'SELECT id FROM incidents WHERE packet_hash = ? OR incident_uuid = ? ORDER BY id LIMIT 1';

function incidentWrites(packet, report = false) {
  const { uuid, key, target, type, channel, delivery, latitude, longitude, altitude, accuracy,
    hops, ttl, device, observed, owner, description, packetId } = packet;
  const [spatial, suffix] = target.split('::');
  const [region = '', province = '', muni = '', barangay = '', zone = ''] = suffix ? spatial.split('-') : [];
  const fields = [uuid, key, target, region, province, muni, barangay, zone, type, channel, delivery,
    latitude, longitude, altitude, accuracy, hops, ttl, device, observed, owner, description];
  return [
    { sql: `INSERT INTO incidents (incident_uuid, packet_hash, target_id_raw, region_code, province_code,
      municipality_code, barangay_code, zone_code, incident_type, channel, delivery_path,
      latitude, longitude, altitude, gps_accuracy, hop_count, ttl, sender_device_id, event_timestamp,
      citizen_id, description, received_at)
      SELECT ${marks(fields)}, ${sqliteNow} WHERE NOT EXISTS (
        SELECT 1 FROM incidents WHERE packet_hash = ? OR incident_uuid = ?
        UNION ALL SELECT 1 FROM mesh_packets WHERE packet_uuid = ? AND incident_id IS NOT NULL
        ${report ? 'UNION ALL SELECT 1 FROM incident_reports WHERE transport_token = ? OR report_uuid = ?' : ''}
      ) ON CONFLICT DO NOTHING`, args: [...fields, key, uuid, packetId, ...(report ? [key, uuid] : [])] },
    { sql: `INSERT INTO incident_status_history (incident_id, status)
      SELECT id, 'RECEIVED' FROM incidents WHERE id = last_insert_rowid() AND changes() = 1`, args: [] },
    { sql: `INSERT INTO notifications (user_id, incident_id, type, payload)
      SELECT u.id, i.id, 'NEW_INCIDENT', ? FROM users u JOIN incidents i ON i.incident_uuid = ?
      WHERE changes() = 1 AND u.role = 'ADMIN' AND u.account_status = 'APPROVED'`,
      args: [`New ${type.toLowerCase()} incident received`, uuid] },
    { sql: `INSERT INTO mesh_packets (packet_uuid, packet_type, incident_id, origin_user_id,
      origin_device_identifier, payload_hash, created_at, server_received_at)
      SELECT ?, 'SOS', id, ?, ?, ?, ?, ${sqliteNow} FROM incidents WHERE id = (${incidentByKey})
      ON CONFLICT(packet_uuid) DO UPDATE SET incident_id = excluded.incident_id,
      payload_hash = excluded.payload_hash, server_received_at = COALESCE(mesh_packets.server_received_at, excluded.server_received_at)
      WHERE mesh_packets.incident_id IS NULL AND mesh_packets.packet_type = 'SOS'`,
      args: [packetId, owner, device, key, observed, key, uuid] },
  ];
}

async function uplink(db, body, user) {
  const packetId = reportKey(body.packet_uuid, 'packet_uuid');
  const uuid = reportKey(body.incident_uuid || packetId, 'incident_uuid');
  const key = body.packet_hash ? reportKey(body.packet_hash, 'packet_hash') : await hash(packetId);
  const channel = oneOf(body.channel || 'ONLINE', ['ONLINE', 'BLE_MESH'], 'channel');
  const target = string(body.target_id?.raw, 'target_id.raw', 150);
  if (!/^(?:[\w]+-){3,4}[\w]+::[\w-]+$/.test(target)) throw new HttpError(400, 'Invalid target ID');
  const relay = channel === 'BLE_MESH';
  const owner = !relay && isCitizen(user) ? user.id : null;
  const device = relay
    ? (body.sender_device_id ? string(body.sender_device_id, 'sender_device_id', 128) : '')
    : (user.device_id || '');
  const packet = {
    uuid, key, target, packetId, channel, owner, device,
    type: oneOf(body.incident_type, types, 'incident_type'),
    delivery: relay ? 'BLE_CLOUD_SHORTCUT' : 'DIRECT_UPLINK',
    latitude: number(body.latitude, 'latitude', -90, 90),
    longitude: number(body.longitude, 'longitude', -180, 180),
    altitude: number(body.altitude ?? 0, 'altitude', -12000, 100000),
    accuracy: number(body.gps_accuracy ?? 0, 'gps_accuracy', 0, 100000),
    hops: number(body.hop_count ?? 0, 'hop_count', 0, 255, true),
    ttl: number(body.ttl ?? 10, 'ttl', 0, 255, true),
    observed: body.timestamp ? date(body.timestamp, 'timestamp') : now(), description: '',
  };
  const results = await db.batch([...incidentWrites(packet), {
    sql: `SELECT i.*, ? AS packet_uuid FROM incidents i WHERE i.id = COALESCE(
      (SELECT incident_id FROM mesh_packets WHERE packet_uuid = ?), (${incidentByKey}))`,
    args: [packetId, packetId, key, uuid],
  }], 'write');
  const row = results.at(-1).rows[0];
  if (!row) throw new HttpError(409, 'Conflicting packet identity');
  const deduplicated = results[0].rowsAffected === 0;
  return {
    success: true, status: deduplicated ? 'deduplicated' : 'accepted', deduplicated,
    ...(isAdmin(user) || row.citizen_id === user.id ? { incident: incident(row) } : {}),
  };
}

async function submitReport(db, body, user) {
  const uuid = reportKey(body.report_uuid || body.transport_token, 'report_uuid');
  const key = reportKey(body.transport_token || uuid, 'transport_token');
  const owner = isCitizen(user) ? user.id : null;
  const device = user.device_id || '';
  const origin = oneOf(body.origin_channel || 'CELLULAR', ['ONLINE', 'CELLULAR', 'BLE_MESH'], 'origin_channel');
  const lat = body.latitude == null ? null : number(body.latitude, 'latitude', -90, 90);
  const lon = body.longitude == null ? null : number(body.longitude, 'longitude', -180, 180);
  if ((lat == null) !== (lon == null)) throw new HttpError(400, 'Latitude and longitude are both required when supplying coordinates');
  const accuracy = body.gps_accuracy_meters == null ? null : number(body.gps_accuracy_meters, 'gps_accuracy_meters', 0, 100000);
  const type = oneOf(body.emergency_type, types, 'emergency_type');
  const description = body.description == null ? '' : string(body.description, 'description', 4000, true);
  const observed = now();
  const optional = (field, fallback) => body[field] == null ? fallback : string(body[field], field, 128, true);
  const report = [
    uuid, key, owner, owner ? device : null, owner ? null : device,
    optional('submission_type', 'CITIZEN_REPORT'), optional('subject_relation', 'SELF'), type,
    optional('subtype', 'GENERAL'), description, number(body.people_affected ?? 1, 'people_affected', 0, 100000, true),
    boolean(body.immediate_danger ?? false, 'immediate_danger'), optional('location_source', 'GPS'),
    optional('selected_barangay_code', null), lat, lon, accuracy, observed, origin,
    owner || description ? 'FULL' : 'MINIMAL',
  ];
  const lookupReport = `SELECT * FROM incident_reports WHERE transport_token = ? OR report_uuid = ?
    ORDER BY CASE WHEN transport_token = ? THEN 0 ELSE 1 END, id LIMIT 1`;
  const params = [key, uuid, key];
  const packet = { uuid, key, owner, device, type, description, observed, packetId: key,
    channel: origin === 'BLE_MESH' ? 'BLE_MESH' : 'ONLINE', delivery: 'DIRECT_UPLINK', target: 'BLE_P2P',
    latitude: lat ?? 0, longitude: lon ?? 0, altitude: 0, accuracy: accuracy ?? 0, hops: 0, ttl: 10 };
  const results = await db.batch([
    ...incidentWrites(packet, true),
    { sql: `INSERT INTO incident_reports (report_uuid, transport_token, reporter_user_id, reporter_device_id,
      gateway_device_id, submission_type, subject_relation, emergency_type, subtype, description, people_affected,
      immediate_danger, location_source, selected_barangay_code, latitude, longitude, gps_accuracy_meters,
      observed_at, origin_channel, detail_state, incident_id)
      SELECT ${marks(report)}, i.id FROM incidents i WHERE i.id = (${incidentByKey})
      AND (i.citizen_id IS NULL OR i.citizen_id = ?) ON CONFLICT DO NOTHING`, args: [...report, key, uuid, owner] },
    ...(owner ? [
      { sql: `UPDATE incident_reports SET reporter_user_id = ?, reporter_device_id = ?,
          description = ?, subtype = ?, subject_relation = ?, people_affected = ?, immediate_danger = ?, detail_state = 'FULL'
        WHERE transport_token = ? AND (report_uuid = ? OR detail_state = 'MINIMAL')
        AND reporter_user_id IS NULL AND EXISTS (SELECT 1 FROM incidents i WHERE i.id = incident_id
          AND (i.citizen_id IS NULL OR i.citizen_id = ?) AND (i.sender_device_id = '' OR i.sender_device_id = ?
            OR EXISTS (SELECT 1 FROM user_devices d WHERE d.user_id = ? AND d.device_hardware_id = i.sender_device_id)))`,
        args: [owner, device, description, report[8], report[6], report[10], report[11], key, uuid, owner, device, owner] },
      { sql: `UPDATE incidents SET citizen_id = ?, updated_at = ${sqliteNow} WHERE citizen_id IS NULL AND id = (
        SELECT incident_id FROM incident_reports WHERE transport_token = ? AND reporter_user_id = ?)`, args: [owner, key, owner] },
    ] : []),
    { sql: lookupReport, args: params },
  ], 'write');
  const row = results.at(-1).rows[0];
  if (!row || (owner && row.reporter_user_id !== owner)) deny('This report belongs to another account');
  return json({
    success: true,
    report: isAdmin(user) || owner ? reportRow(row)
      : { report_uuid: row.report_uuid, transport_token: row.transport_token, incident_id: row.incident_id },
  }, 201);
}

async function meshEvents(db, events, user) {
  if (!Array.isArray(events) || events.length > 200) throw new HttpError(400, 'Send at most 200 mesh events per request');
  const supported = ['CREATED', 'RECEIVED', 'RELAYED', 'TARGET_REACHED', 'UPLINKED', 'DUPLICATE_DROPPED'];
  const statements = [];
  for (const event of events) {
    if (!event || !supported.includes(event.event_type)) continue;
    const key = reportKey(event.packet_uuid || event.packet_hash, 'packet identity');
    const packetType = oneOf(event.packet_type || 'SOS', ['SOS', 'ACK', 'STATUS'], 'packet_type');
    const packetId = packetType === 'SOS' ? key : `${packetType}:${key}`;
    const device = user.device_id || `account:${user.id}`;
    const latitude = event.latitude == null ? null : number(event.latitude, 'latitude', -90, 90);
    const longitude = event.longitude == null ? null : number(event.longitude, 'longitude', -180, 180);
    const timestamp = event.recorded_at ? date(event.recorded_at, 'recorded_at') : now();
    statements.push(
      { sql: `INSERT INTO mesh_packets (packet_uuid, packet_type, payload_hash)
        VALUES (?, ?, ?) ON CONFLICT(packet_uuid) DO NOTHING`, args: [packetId, packetType, key] },
      { sql: `INSERT INTO mesh_delivery_events (packet_id, device_identifier, event_type, latitude, longitude, recorded_at, synced_at)
        SELECT p.id, ?, ?, ?, ?, ?, ${sqliteNow} FROM mesh_packets p WHERE p.packet_uuid = ?
        AND NOT EXISTS (SELECT 1 FROM mesh_delivery_events e WHERE e.packet_id = p.id
          AND e.device_identifier = ? AND e.event_type = ? AND e.recorded_at = ?)`,
        args: [device, event.event_type, latitude, longitude, timestamp, packetId, device, event.event_type, timestamp] },
    );
  }
  if (!statements.length) return 0;
  const results = await db.batch(statements, 'write');
  return results.reduce((count, result, index) => count + (index % 2 ? result.rowsAffected : 0), 0);
}

async function sync(db, user, params, bootstrap) {
  const userScope = isCitizen(user) ? `AND ${citizenIncidentScope}` : '';
  const scopeArgs = isCitizen(user) ? [user.id, user.id] : [];
  const since = params.get('since') || '1970-01-01T00:00:00.000Z';
  const [sinceDate, sinceId] = since.split('|');
  const timestamp = date(sinceDate, 'since');
  const cursor = sinceId === undefined ? 0 : id(sinceId);
  const [incidents, agencies, emergencyTypes, messages] = await db.batch([
    bootstrap
      ? { sql: `SELECT i.* FROM incidents i WHERE i.status NOT IN (${terminal}) ${userScope}
        ORDER BY i.received_at DESC, i.id DESC LIMIT 100`, args: scopeArgs }
      : { sql: `SELECT i.* FROM incidents i WHERE
        (julianday(i.updated_at) > julianday(?) OR (julianday(i.updated_at) = julianday(?) AND i.id > ?))
        ${userScope} ORDER BY julianday(i.updated_at), i.id LIMIT 200`, args: [timestamp, timestamp, cursor, ...scopeArgs] },
    { sql: 'SELECT * FROM agencies ORDER BY id', args: [] },
    { sql: 'SELECT * FROM emergency_types WHERE is_active = 1 ORDER BY id', args: [] },
    { sql: 'SELECT * FROM predefined_messages WHERE is_active = 1 ORDER BY id', args: [] },
  ], 'read');
  const catalogs = {
    agencies: agencies.rows.map((row) => withBooleans(row, 'is_active')),
    emergency_types: emergencyTypes.rows.map((row) => withBooleans(row, 'is_active')),
    predefined_messages: messages.rows.map((row) => withBooleans(row, 'is_active')),
  };
  if (bootstrap) {
    const unit = user.role === 'RESPONDER' ? await ownUnit(db, user) : null;
    return json({ ...catalogs, user, active_incidents: incidents.rows.map(incident),
      assigned_dispatches: unit ? await rows(db, `${dispatchSelect} WHERE d.unit_id = ? ORDER BY d.id DESC`, [unit.id]) : [] });
  }
  const last = incidents.rows.at(-1);
  return json({ ...catalogs, since, server_time: last ? `${last.updated_at}|${last.id}` : since,
    incidents: incidents.rows.map(incident) });
}

async function alerts(path, request, db, user, params) {
  if (path === '/alerts' && request.method === 'GET') {
    const limit = pageNumber(params.get('limit'), 50, 1, 200);
    const offset = pageNumber(params.get('offset'), 0, 0, Number.MAX_SAFE_INTEGER);
    const filter = isAdmin(user) && params.get('active_only') === 'false' ? ''
      : `WHERE a.is_active = 1 AND (a.expires_at IS NULL OR julianday(a.expires_at) > julianday('now'))`;
    const [count, page] = await db.batch([
      { sql: `SELECT COUNT(*) AS total FROM admin_alerts a ${filter}`, args: [] },
      { sql: `SELECT a.*, u.username AS author_name,
        (SELECT COUNT(*) FROM admin_alert_reads WHERE alert_id = a.id) AS read_count,
        EXISTS (SELECT 1 FROM admin_alert_reads WHERE alert_id = a.id AND user_id = ?) AS is_read
        FROM admin_alerts a JOIN users u ON u.id = a.author_id ${filter}
        ORDER BY a.is_pinned DESC, a.published_at DESC LIMIT ? OFFSET ?`, args: [user.id, limit, offset] },
    ], 'read');
    return json({ alerts: page.rows.map((row) => withBooleans(row, 'is_active', 'is_pinned', 'is_read')),
      total: count.rows[0].total, limit, offset });
  }
  if (path === '/alerts' && request.method === 'POST') {
    if (!isAdmin(user)) deny();
    const body = await readBody(request);
    const row = await first(db, `INSERT INTO admin_alerts (author_id, title, body, category, severity, is_pinned,
      predefined_message_id, target_region_code, target_province_code, target_muni_code, target_barangay_code)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *`, [
      user.id, string(body.title, 'title', 255), string(body.body, 'body', 5000),
      oneOf(body.category, ['WEATHER', 'EVACUATION', 'SAFETY', 'HEALTH', 'INFRASTRUCTURE', 'GENERAL'], 'category'),
      oneOf(body.severity, ['INFO', 'WARNING', 'CRITICAL', 'EMERGENCY'], 'severity'),
      boolean(body.is_pinned ?? false, 'is_pinned'), body.predefined_message_id == null ? null : id(body.predefined_message_id),
      ...['target_region_code', 'target_province_code', 'target_muni_code', 'target_barangay_code']
        .map((field) => body[field] ? string(body[field], field, 16) : null),
    ]);
    return json(withBooleans(row, 'is_pinned', 'is_active'), 201);
  }
  const match = path.match(/^\/alerts\/(\d+)$/);
  if (match && request.method === 'DELETE') {
    if (!isAdmin(user)) deny();
    if (!(await query(db, 'DELETE FROM admin_alerts WHERE id = ?', [id(match[1])])).rowsAffected) {
      throw new HttpError(404, 'Alert not found');
    }
    return noContent();
  }
}

export async function mobileRoute(path, request, db, user, params) {
  const method = request.method;
  if (method === 'GET' && path === '/sync/bootstrap') return sync(db, user, params, true);
  if (method === 'GET' && path === '/sync/changes') return sync(db, user, params, false);
  if (method === 'POST' && path === '/incident-reports') return submitReport(db, await readBody(request), user);
  if (method === 'POST' && path === '/telemetry/uplink') return json(await uplink(db, await readBody(request), user), 200);
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
    return json({ success: true, reports: (await rows(db,
      'SELECT * FROM incident_reports WHERE reporter_user_id = ? ORDER BY id DESC', [user.id])).map(reportRow) });
  }
  if (method === 'GET' && path.startsWith('/users/me/incident-reports/')) {
    const uuid = reportKey(path.split('/').at(-1), 'report_uuid');
    const row = await first(db, 'SELECT * FROM incident_reports WHERE report_uuid = ? AND reporter_user_id = ?', [uuid, user.id]);
    if (!row) throw new HttpError(404, 'Report not found');
    return json({ success: true, report: reportRow(row) });
  }
  if (path === '/alerts' || path.startsWith('/alerts/')) return alerts(path, request, db, user, params);
}
