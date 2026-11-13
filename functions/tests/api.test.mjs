import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import { prepareSchema } from '../scripts/sqlite.mjs';
import { createApi } from '../shared/api.mjs';

function fixture(t, provider = 'cloudflare', mounts = []) {
  const sqlite = new DatabaseSync(':memory:');
  t.after(() => sqlite.close());
  prepareSchema(sqlite, true);
  sqlite.exec(`UPDATE users SET password_hash = 'test-admin-hash' WHERE username = 'admin';
    INSERT INTO users (username, password_hash, role, account_status, is_verified)
      VALUES ('test-responder', 'test-responder-hash', 'RESPONDER', 'APPROVED', 1)`);
  function run({ sql, args = [] }) {
    const statement = sqlite.prepare(sql);
    const before = sqlite.prepare('SELECT total_changes() AS total').get().total;
    const rows = statement.all(...args);
    const after = sqlite.prepare('SELECT total_changes() AS total').get().total;
    return { rows, rowsAffected: after - before };
  }
  const db = {
    execute: async (statement) => run(statement),
    batch: async (statements, mode) => {
      sqlite.exec(mode === 'write' ? 'BEGIN IMMEDIATE' : 'BEGIN');
      try {
        const results = statements.map(run);
        sqlite.exec('COMMIT');
        return results;
      } catch (error) {
        sqlite.exec('ROLLBACK');
        throw error;
      }
    },
  };
  const handler = createApi({
    provider, mounts, getDatabase: () => db, origins: 'https://agap.example',
    verifyPassword: async (password, hash) => password === 'a-test-password' && hash.startsWith('test-'),
    hashPassword: async () => 'test-registered-hash',
  });
  const call = (path, { method = 'GET', body, token, origin = 'https://agap.example' } = {}) =>
    handler(new Request(`https://api.example${path}`, {
      method,
      headers: {
        Origin: origin,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    }));
  return { sqlite, call };
}

async function expectJson(response, status) {
  const data = await response.json();
  assert.equal(response.status, status, JSON.stringify(data));
  return data;
}

const credentials = { username: 'admin', password: 'a-test-password' };
const endpoint = '/api/v1';

for (const [provider, prefix, mounts] of [
  ['cloudflare', '', []],
  ['supabase', '/functions/v1/agap-api', ['/functions/v1/agap-api', '/agap-api']],
  ['firebase', '/agapApi', ['/agapApi']],
]) {
  test(`${provider} health, CORS, sign-in and session rotation`, async (t) => {
    const { call, sqlite } = fixture(t, provider, mounts);
    const health = await expectJson(await call(`${prefix}/health`), 200);
    assert.equal(health.provider, provider);
    const base = `${prefix}${endpoint}`;
    const preflight = await call(`${base}/users/me`, { method: 'OPTIONS' });
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers.get('access-control-allow-origin'), 'https://agap.example');
    assert.equal((await call(`${base}/users/me`, { origin: 'https://other.example' })).status, 403);
    assert.equal((await call(`${base}/users/me`)).status, 401);
    const responder = await expectJson(await call(`${base}/auth/login`, {
      method: 'POST', body: { ...credentials, username: 'test-responder' },
    }), 200);
    assert.equal((await call(`${base}/users`, { token: responder.token })).status, 403);
    const session = await expectJson(await call(`${base}/auth/login`, { method: 'POST', body: credentials }), 200);
    assert.equal(session.user.role, 'ADMIN');
    assert.equal(session.user.is_verified, true);
    assert.equal('password_hash' in session.user, false);
    assert.equal(sqlite.prepare('SELECT access_hash FROM hosted_sessions').get().access_hash === session.token, false);
    const profile = await expectJson(await call(`${base}/users/me`, { token: session.token }), 200);
    assert.equal(profile.id, session.user.id);
    const refresh = () => call(`${base}/auth/refresh`, { method: 'POST', body: { refresh_token: session.refresh_token } });
    const refreshed = await expectJson(await refresh(), 200);
    assert.equal((await refresh()).status, 401);
    assert.equal((await call(`${base}/users/me`, { token: session.token })).status, 401);
    assert.equal((await call(`${base}/auth/logout`, {
      method: 'POST', token: refreshed.token, body: { refresh_token: refreshed.refresh_token },
    })).status, 204);
    assert.equal((await call(`${base}/users/me`, { token: refreshed.token })).status, 401);
  });
}

test('web console registries, incident intake, dispatch and chat share durable records', async (t) => {
  const { call, sqlite } = fixture(t);
  const { token } = await expectJson(await call(`${endpoint}/auth/login`, { method: 'POST', body: credentials }), 200);
  const api = (path, method = 'GET', body) => call(`${endpoint}${path}`, { token, method, body });
  const agency = await expectJson(await api('/agencies', 'POST', {
    code: 'QA-EMS', name: 'Test EMS', type: 'MEDICAL', region_code: '05', province_code: '05',
    muni_code: '12', barangay_code: '01', is_active: true,
  }), 201);
  const target = '05-05-12-01::01d7da90-0f95-4653-9a16-419803290fec';
  const unit = await expectJson(await api('/responders', 'POST', {
    agency_id: agency.id, unit_callsign: 'QA-UNIT', target_id: target,
  }), 201);
  const [responder] = await expectJson(await api('/users?role=RESPONDER'), 200);
  const member = await expectJson(await api(`/rescue-units/${unit.id}/members`, 'POST',
    { responder_id: responder.id, is_leader: true }), 201);
  assert.equal(member.is_leader, true);
  const intake = {
    incident_uuid: '01d7da90-0f95-4653-9a16-419803290fec', target_id: { raw: target },
    incident_type: 'MEDICAL', latitude: 13.14, longitude: 123.74, sender_device_id: 'integration',
  };
  const record = await expectJson(await api('/incidents', 'POST', intake), 201);
  const duplicate = await expectJson(await api('/incidents', 'POST', intake), 200);
  assert.equal(duplicate.id, record.id);
  assert.equal(record.target_id.region, '05');
  const notifications = await expectJson(await api('/notifications'), 200);
  assert.equal(notifications.length, 1);
  assert.equal((await api(`/notifications/${notifications[0].id}/read`, 'PUT')).status, 204);
  assert.equal((await expectJson(await api('/notifications?unread=true'), 200)).length, 0);
  const page = await expectJson(await api('/incidents?exclude_demo=true&limit=10&offset=0'), 200);
  assert.equal(page.total, 1);
  assert.equal(page.incidents[0].status, 'RECEIVED');
  const path = `/incidents/${record.id}`;
  await expectJson(await api(`${path}/status`, 'PUT', { status: 'ACKNOWLEDGED' }), 200);
  const assignment = await expectJson(await api(`${path}/dispatch`, 'POST', { unit_id: unit.id }), 201);
  assert.equal(assignment.unit_callsign, 'QA-UNIT');
  assert.equal((await api(`${path}/dispatch`, 'POST', { unit_id: unit.id })).status, 409);
  assert.equal((await api(`/responders/${unit.id}`, 'DELETE')).status, 409);
  const dispatchStatus = `${path}/dispatches/${assignment.id}/status`;
  for (const status of ['ACCEPTED', 'EN_ROUTE', 'ON_SCENE', 'COMPLETED']) {
    await expectJson(await api(dispatchStatus, 'PUT', { status }), 200);
  }
  const history = await expectJson(await api(`/incidents/status-history?incident_ids=${record.id}`), 200);
  assert.deepEqual(history[record.id].map((row) => row.status),
    ['RECEIVED', 'ACKNOWLEDGED', 'DISPATCHED', 'EN_ROUTE', 'ON_SCENE', 'RESOLVED']);
  const message = { custom_message: 'Test message', client_message_id: 'message-1' };
  const sent = await expectJson(await api(`${path}/messages`, 'POST', message), 201);
  const replay = await expectJson(await api(`${path}/messages`, 'POST', message), 200);
  assert.equal(sent.id, replay.id);
  assert.equal((await expectJson(await api(`${path}/messages`), 200)).length, 1);
  assert.equal((await expectJson(await api(`/responders/${unit.id}`), 200)).status, 'IDLE');
  assert.ok((await expectJson(await api('/admin/audit-logs'), 200)).length > 0);
  assert.deepEqual(sqlite.prepare('PRAGMA foreign_key_check').all(), []);
});

test('Android reports, BLE retries and incremental sync respect account ownership', async (t) => {
  const { call, sqlite } = fixture(t);
  const login = async (username, expected = 200) => expectJson(await call(`${endpoint}/auth/login`, {
    method: 'POST', body: { ...credentials, username, device_id: `device-${username}` },
  }), expected);
  const api = (token, path, method = 'GET', body) => call(`${endpoint}${path}`, { token, method, body });
  const admin = await login('admin');
  const responder = await login('test-responder');
  const citizenRegistration = async (username) => {
    const registered = await expectJson(await api(null, '/auth/register', 'POST', {
      ...credentials, username, role: 'CITIZEN', device_id: `device-${username}`,
    }), 201);
    assert.equal(registered.user.account_status, 'PENDING');
    assert.match((await login(username, 403)).error, /pending/);
    await expectJson(await api(admin.token, `/admin/users/${registered.user.id}/verify`, 'PUT'), 200);
    return login(username);
  };
  assert.equal((await api(null, '/auth/register', 'POST', { ...credentials, role: 'ADMIN' })).status, 403);
  const citizen = await citizenRegistration('citizen-a');
  const other = await citizenRegistration('citizen-b');
  const packet = {
    incident_uuid: '001122334455', packet_uuid: '001122334455', packet_hash: '001122334455',
    target_id: { raw: 'BLE-BLE-BLE-BLE::001122334455' }, incident_type: 'MEDICAL', channel: 'BLE_MESH',
    latitude: 13.14, longitude: 123.74, sender_device_id: 'device-citizen-a', hop_count: 2,
    timestamp: '2026-11-04T05:30:00Z',
  };
  const events = [{ packet_hash: packet.packet_hash, packet_type: 'SOS', event_type: 'RELAYED', recorded_at: packet.timestamp }];
  assert.equal((await expectJson(await api(responder.token, '/mesh/events/batch', 'POST', { events }), 200)).recorded, 1);
  assert.equal((await expectJson(await api(responder.token, '/mesh/events/batch', 'POST', { events }), 200)).recorded, 0);
  const upload = () => api(responder.token, '/telemetry/uplink', 'POST', packet);
  assert.equal((await expectJson(await upload(), 200)).deduplicated, false);
  assert.equal((await expectJson(await upload(), 200)).deduplicated, true);
  const report = {
    report_uuid: 'f081bdcc-fd9d-4dea-bbf8-7961096003ae', transport_token: packet.packet_hash,
    emergency_type: 'MEDICAL', origin_channel: 'BLE_MESH', latitude: packet.latitude, longitude: packet.longitude,
    description: 'Citizen report after a BLE relay',
  };
  const firstReport = await expectJson(await api(citizen.token, '/incident-reports', 'POST', report), 201);
  const again = await expectJson(await api(citizen.token, '/incident-reports', 'POST', report), 201);
  const incidentId = firstReport.report.incident_id;
  assert.equal(again.report.incident_id, incidentId);
  assert.equal((await api(other.token, '/incident-reports', 'POST', report)).status, 403);
  assert.equal((await api(other.token, `/incidents/${incidentId}`)).status, 404);
  assert.equal((await api(citizen.token, `/incidents/${incidentId}/status`, 'PUT', { status: 'CANCELLED' })).status, 403);
  assert.equal((await api(responder.token, `/incidents/${incidentId}/status`, 'PUT', { status: 'ACKNOWLEDGED' })).status, 403);
  assert.equal((await api(responder.token, `/incidents/${incidentId}/messages`)).status, 403);
  assert.equal((await expectJson(await api(admin.token, '/incidents'), 200)).incidents[0].id, incidentId);
  assert.deepEqual((await expectJson(await api(other.token, '/sync/bootstrap'), 200)).active_incidents, []);
  const changes = await expectJson(await api(citizen.token, '/sync/changes'), 200);
  assert.equal(changes.incidents[0].id, incidentId);
  assert.equal(changes.incidents[0].hop_count, 2);
  const cursor = encodeURIComponent(changes.server_time);
  assert.deepEqual((await expectJson(await api(citizen.token, `/sync/changes?since=${cursor}`), 200)).incidents, []);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS count FROM incidents').get().count, 1);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS count FROM notifications WHERE type = ?').get('NEW_INCIDENT').count, 1);
  assert.equal(sqlite.prepare('SELECT incident_id FROM mesh_packets WHERE packet_uuid = ?').get(packet.packet_uuid).incident_id, incidentId);
});
