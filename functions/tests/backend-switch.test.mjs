import assert from 'node:assert/strict';
import { readFileSync, readdirSync, mkdtempSync, rmSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import { PGlite } from '@electric-sql/pglite';
import { Firestore } from '@google-cloud/firestore';
import pg from 'pg';
import { createGateway } from '../cloudflare/gateway.mjs';
import { createApi } from '../shared/api.mjs';
import { createFirestoreApi } from '../shared/firestore-api.mjs';
import { activeFirestoreStore } from '../shared/firestore-store.mjs';
import { connectPostgres, postgresFactory } from '../shared/postgres.mjs';
import { prepareSchema } from '../scripts/sqlite.mjs';
import { exportPostgres, exportSqlite, importPostgres } from '../scripts/snapshot.mjs';
import { activateFirestore, exportFirestore, importFirestore } from '../scripts/firestore-migration.mjs';

const origin = 'https://agap.example';
const secret = 'test-gateway-secret';
const options = { gatewaySecret: secret, verifyPassword: async (password, hash) => password === 'test-password' && hash.startsWith('test-'),
  hashPassword: async () => 'test-registered-hash' };

function sourceSnapshot(t) {
  const directory = mkdtempSync(join(tmpdir(), 'agap-migration-'));
  const file = join(directory, 'source.db');
  t.after(() => { rmSync(file, { force: true }); rmdirSync(directory); });
  const sqlite = new DatabaseSync(file);
  prepareSchema(sqlite, true);
  sqlite.exec("UPDATE users SET password_hash = 'test-admin-hash' WHERE username = 'admin';");
  sqlite.close();
  return exportSqlite(file);
}

function gatewayFor(postgres, firestore) {
  return createGateway((target, init) => (target.hostname === 'postgres.example' ? postgres : firestore)(new Request(target, init)));
}

function client(fetch, mode) {
  const env = { AGAP_BACKEND: mode, AGAP_GATEWAY_SECRET: secret, AGAP_ALLOWED_ORIGINS: origin,
    AGAP_SUPABASE_ORIGIN: 'https://postgres.example/functions/v1/agap-postgres',
    AGAP_FIRESTORE_ORIGIN: 'https://firestore.example/.netlify/functions/agap-api' };
  return async (path, method = 'GET', body, token, expected = 200) => {
    const response = await fetch(new Request(`https://api.example${path}`, { method, body: body && JSON.stringify(body),
      headers: { Origin: origin, ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}), 'X-AGAP-Gateway': 'untrusted-client-value' },
    }), env);
    const data = response.status === 204 ? null : await response.json();
    assert.equal(response.status, expected, JSON.stringify(data));
    return data;
  };
}

async function scenario(call, database) {
  const api = (path, ...args) => call(`/api/v1${path}`, ...args);
  assert.equal((await call('/health')).db, database);
  const login = (username, device_id, status) => api('/auth/login', 'POST', { username, password: 'test-password', device_id }, undefined, status);
  const admin = await login('admin');
  const register = async (username) => {
    const device_id = `device-${username}`;
    const member = await api('/auth/register', 'POST', { username, password: 'test-password', device_id }, undefined, 201);
    await login(username, device_id, 403);
    await api(`/admin/users/${member.user.id}/verify`, 'PUT', undefined, admin.token);
    return login(username, device_id);
  };
  const citizen = await register('test-citizen');
  const other = await register('another-citizen');
  const firstContact = await api('/users/me/contacts', 'POST', { contact_name: 'First', phone_number: '100' }, citizen.token, 201);
  const secondContact = await api('/users/me/contacts', 'POST', { contact_name: 'Second', phone_number: '200' }, citizen.token, 201);
  assert.equal(firstContact.is_primary, true);
  assert.equal(secondContact.is_primary, false);
  await api(`/users/me/contacts/${secondContact.id}/primary`, 'PUT', undefined, citizen.token);
  await api(`/users/me/contacts/${secondContact.id}`, 'DELETE', undefined, citizen.token, 204);
  assert.equal((await api('/users/me/contacts', 'GET', undefined, citizen.token))[0].is_primary, true);
  const packet = { packet_uuid: 'testpacket', packet_hash: 'testpacket', target_id: { raw: 'BLE-BLE-BLE-BLE::testpacket' },
    incident_type: 'MEDICAL', channel: 'BLE_MESH', latitude: 13, longitude: 123, sender_device_id: 'device-test-citizen' };
  const events = { events: [{ packet_hash: packet.packet_hash, event_type: 'RELAYED', recorded_at: '2026-11-19T00:00:00Z' }] };
  assert.equal((await api('/mesh/events/batch', 'POST', events, admin.token)).recorded, 1);
  assert.equal((await api('/mesh/events/batch', 'POST', events, admin.token)).recorded, 0);
  const attempts = await Promise.all(Array.from({ length: 2 }, () => api('/telemetry/uplink', 'POST', packet, admin.token)));
  assert.equal(attempts.filter((result) => !result.deduplicated).length, 1);
  const report = { report_uuid: 'test-report', transport_token: 'testpacket', emergency_type: 'MEDICAL',
    latitude: 13, longitude: 123, description: 'Synthetic report' };
  const sent = await api('/incident-reports', 'POST', report, citizen.token, 201);
  await api('/incident-reports', 'POST', report, other.token, 403);
  const key = sent.report.incident_id;
  await api(`/incidents/${key}`, 'GET', undefined, other.token, 404);
  assert.equal((await api('/incidents?type=MEDICAL&exclude_demo=true', 'GET', undefined, citizen.token)).total, 1);
  const changes = await api('/sync/changes', 'GET', undefined, citizen.token);
  assert.equal(changes.incidents[0].id, key);
  assert.deepEqual((await api(`/sync/changes?since=${encodeURIComponent(changes.server_time)}`, 'GET', undefined, citizen.token)).incidents, []);
  const agency = await api('/agencies', 'POST', { code: 'T-MED', name: 'Testing', type: 'MEDICAL', region_code: '05',
    province_code: '05', muni_code: '12', barangay_code: '01' }, admin.token, 201);
  const unit = await api('/rescue-units', 'POST', { agency_id: agency.id, target_id: 'test-unit', unit_callsign: 'T-01' }, admin.token, 201);
  await api(`/rescue-units/${unit.id}/location`, 'PUT', { latitude: 13, longitude: 123 }, admin.token);
  const dispatch = await api(`/incidents/${key}/dispatch`, 'POST', { unit_id: unit.id }, admin.token, 201);
  assert.equal(dispatch.unit_id, unit.id);
  await api(`/incidents/${key}/dispatches/${dispatch.id}/status`, 'PUT', { status: 'ACCEPTED' }, admin.token);
  const message = { custom_message: 'Help is on the way', client_message_id: 'message-1' };
  await api(`/incidents/${key}/messages`, 'POST', message, admin.token, 201);
  await api(`/incidents/${key}/messages`, 'POST', message, admin.token);
  assert.equal((await api(`/incidents/${key}/messages`, 'GET', undefined, admin.token)).length, 1);
  const alert = await api('/alerts', 'POST', { title: 'Test only', body: 'Synthetic alert', category: 'GENERAL', severity: 'INFO' }, admin.token, 201);
  assert.ok((await api('/alerts', 'GET', undefined, admin.token)).alerts.some((entry) => entry.id === alert.id));
  if (database !== 'firestore') {
    const listed = await api('/incidents?exclude_demo=true&limit=1', 'GET', undefined, admin.token);
    assert.equal(listed.incidents.length, Math.min(1, listed.total));
    const pastEnd = await api(`/incidents?exclude_demo=true&offset=${listed.total + 5}`, 'GET', undefined, admin.token);
    assert.deepEqual([pastEnd.total, pastEnd.incidents.length], [listed.total, 0]);
    const report = await api('/reports/operations', 'GET', undefined, admin.token);
    assert.equal(report.total_incidents, listed.total);
    assert.equal(report.agency_dispatches.find((row) => row.label === 'Testing')?.count, 1);
    assert.equal(report.unit_deployments.find((row) => row.label === 'T-01')?.count, 1);
    assert.equal(typeof report.average_dispatch_minutes, 'number');
    assert.equal(report.total_responders >= 1 && report.active_responders >= 1, true);
    await api('/reports/operations', 'GET', undefined, citizen.token, 403);
  }
  return { admin, citizen, incidentId: key };
}

async function postgresPool(t) {
  const pg = new PGlite();
  t.after(() => pg.close());
  await pg.exec('CREATE ROLE agap_api LOGIN');
  const migrations = new URL('../supabase/supabase/migrations/', import.meta.url);
  for (const name of readdirSync(migrations).filter(name => name.endsWith('.sql')).sort()) {
    await pg.exec(readFileSync(new URL(name, migrations), 'utf8'));
  }
  const { rows } = await pg.query("SELECT has_table_privilege('agap_api', 'agap.hosted_sessions', 'SELECT') AS granted");
  assert.equal(rows[0].granted, true);
  let queue = Promise.resolve();
  return { connect: async () => {
    const previous = queue;
    let release;
    queue = new Promise((resolve) => { release = resolve; });
    await previous;
    return { release, query: async (sql, values) => {
      if (!values?.length) {
        const [result] = await pg.exec(sql);
        return { ...result, rowCount: result.affectedRows, command: sql.split(/\s/)[0] };
      }
      const result = await pg.query(sql, values);
      return { ...result, rowCount: result.affectedRows, command: sql.split(/\s/)[0] };
    } };
  } };
}

const postgresApi = (pool) => createApi({ ...options, provider: 'supabase', databaseType: 'postgres',
  mounts: ['/functions/v1/agap-postgres'], getDatabase: postgresFactory(() => pool, 'local-postgres') });

test('PostgreSQL uses the supplied root CA with TLS verification enabled', async (t) => {
  const url = 'postgresql://agap_api.project:placeholder@pooler.example:6543/postgres?sslmode=verify-full&ssl=0';
  const pool = connectPostgres(pg, url, '-----BEGIN CERTIFICATE-----\\nfixture\\n-----END CERTIFICATE-----');
  t.after(() => pool.end());
  const { host, port, ssl } = new pg.Client(pool.options).connectionParameters;
  assert.equal(host, 'pooler.example');
  assert.equal(port, 6543);
  assert.equal(ssl.rejectUnauthorized, true);
  assert.equal(ssl.ca, '-----BEGIN CERTIFICATE-----\nfixture\n-----END CERTIFICATE-----');
});

test('PostgreSQL schema, SQLite import, gateway and hosted API', async (t) => {
  const snapshot = sourceSnapshot(t);
  const clientPool = await postgresPool(t);
  await importPostgres(clientPool, snapshot);
  assert.equal((await exportPostgres(clientPool)).digest, snapshot.digest);
  await assert.rejects(importPostgres(clientPool, snapshot), /not empty/);
  const backend = postgresApi(clientPool);
  assert.equal((await backend(new Request('https://postgres.example/health'))).status, 404);
  await scenario(client(gatewayFor(backend), 'supabase'), 'postgres');
});

test('PostgreSQL single reads skip the transaction once the role defaults to the agap schema', async (t) => {
  const snapshot = sourceSnapshot(t);
  const clientPool = await postgresPool(t);
  await importPostgres(clientPool, snapshot);
  const setup = await clientPool.connect();
  // Stands in for ALTER ROLE agap_api SET search_path on the shared PGlite session.
  await setup.query('SET search_path = agap, public');
  setup.release();
  const statements = [];
  const recordingPool = { connect: async () => {
    const tx = await clientPool.connect();
    return { release: tx.release, query: (sql, values) => { statements.push(sql); return tx.query(sql, values); } };
  } };
  await scenario(client(gatewayFor(postgresApi(recordingPool)), 'supabase'), 'postgres');
  assert.ok(statements.some((sql) => /current_setting\('search_path'\)/.test(sql)));
  assert.ok(!statements.some((sql) => /SET LOCAL search_path/.test(sql)));
  // Every authenticated request runs the session lookup; it must run bare, not inside BEGIN.
  const authLookups = statements.flatMap((sql, index) =>
    /FROM hosted_sessions WHERE access_hash/.test(sql) ? [statements[index - 1] ?? ''] : []);
  assert.ok(authLookups.length > 0);
  assert.ok(authLookups.every((previous) => !/^BEGIN/.test(previous)));
});

test('Firestore migration resumes, switches the gateway and supports the hosted API', { skip: !process.env.FIRESTORE_EMULATOR_HOST }, async (t) => {
  const snapshot = sourceSnapshot(t);
  const pg = await postgresPool(t);
  await importPostgres(pg, snapshot);
  const db = new Firestore({ projectId: `demo-agap-${Date.now()}` });
  t.after(() => db.terminate());
  const backend = createFirestoreApi({ ...options, getStore: () => activeFirestoreStore(db), mounts: ['/.netlify/functions/agap-api'] });
  const gateway = gatewayFor(postgresApi(pg), backend);
  const primary = client(gateway, 'supabase');
  const live = client(gateway, 'firestore');
  const admin = await primary('/api/v1/auth/login', 'POST', { username: 'admin', password: 'test-password' });
  await live('/health', 'GET', undefined, undefined, 503);
  const partial = await importFirestore(db, snapshot, { maxWrites: 12 });
  assert.equal(partial.complete, false);
  await assert.rejects(activateFirestore(db, snapshot), /not completed/);
  const resumed = await importFirestore(db, snapshot);
  assert.equal(resumed.complete, true);
  await activateFirestore(db, snapshot);
  assert.equal((await exportFirestore(db)).digest, snapshot.digest);
  await client(gateway, 'maintenance')('/health', 'GET', undefined, undefined, 503);
  await live('/api/v1/users/me', 'GET', undefined, admin.token, 401);
  const { incidentId } = await scenario(live, 'firestore');
  const recovery = await exportFirestore(db);
  assert.equal(recovery.tables.incidents.length, 1);
  await importPostgres(pg, recovery, { replace: true });
  assert.equal((await exportPostgres(pg)).digest, recovery.digest);
  const relogin = await primary('/api/v1/auth/login', 'POST', { username: 'admin', password: 'test-password' });
  assert.equal((await primary(`/api/v1/incidents/${incidentId}`, 'GET', undefined, relogin.token)).status, 'DISPATCHED');
});

test('gateway and API report Server-Timing for each hop', async (t) => {
  const clientPool = await postgresPool(t);
  await importPostgres(clientPool, sourceSnapshot(t));
  const env = { AGAP_BACKEND: 'supabase', AGAP_GATEWAY_SECRET: secret, AGAP_ALLOWED_ORIGINS: origin,
    AGAP_SUPABASE_ORIGIN: 'https://postgres.example/functions/v1/agap-postgres' };
  const response = await gatewayFor(postgresApi(clientPool))(new Request('https://api.example/api/v1/agencies',
    { headers: { Origin: origin, Authorization: `Bearer ${'0'.repeat(64)}` } }), env);
  assert.equal(response.status, 401);
  const timing = response.headers.get('server-timing');
  assert.match(timing, /^upstream;dur=[\d.]+, auth;dur=[\d.]+, db;dur=[\d.]+;desc="1 statements", total;dur=[\d.]+$/);
  assert.match(response.headers.get('access-control-expose-headers'), /Server-Timing/);
});

test('gateway forwards quota errors without changing the write destination', async () => {
  const calls = [];
  const gateway = createGateway(async (url, request) => {
    calls.push(url.hostname);
    assert.equal(request.redirect, 'manual');
    assert.equal(request.headers.get('X-AGAP-Gateway'), secret);
    return Response.json({ error: 'Quota reached' }, { status: 429, headers: { 'Retry-After': '60' } });
  });
  await client(gateway, 'supabase')('/api/v1/incident-reports', 'POST', { report_uuid: 'test' }, 'session', 429);
  assert.deepEqual(calls, ['postgres.example']);
});
