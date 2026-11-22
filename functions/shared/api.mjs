import { authenticate, authRoute } from './auth.mjs';
import { HttpError, first, id, json, noContent, now, rows } from './http.mjs';
import { incidentRoute, unitRoute } from './incidents.mjs';
import { accountRoute, directoryRoute, registryRoute } from './registries.mjs';
import { authorizeRoute, isCitizen } from './access.mjs';
import { mobileRoute } from './mobile.mjs';
import { reportRoute } from './reports.mjs';

export const authPaths = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];
export const dashboardTables = ['agencies', 'rescue_units', 'incidents', 'incident_dispatches', 'incident_reports'];
/** @type {string[]} */
const noMounts = [];
const started = Date.now();
export const uptime = () => `${Math.floor((Date.now() - started) / 1000)}s`;

function databaseError(error) {
  if (/^(SQLITE_CONSTRAINT|23\d{3}$)/.test(error.code || '')) return json({ error: 'The change conflicts with an existing record' }, 409);
  console.error('AGAP API request failed', error.code || error.name);
  return json({ error: 'The API is temporarily unavailable' }, 503);
}

// Wraps a SQL client so each request reports how long it spent waiting on the
// database, and on how many statements, through the Server-Timing header.
export function timedDatabase(db, timing) {
  const track = async (count, work) => {
    const start = performance.now();
    try {
      return await work();
    } finally {
      timing.db = (timing.db ?? 0) + performance.now() - start;
      timing.statements = (timing.statements ?? 0) + count;
    }
  };
  return {
    execute: (statement) => track(1, () => db.execute(statement)),
    batch: (statements, mode) => track(statements.length, () => db.batch(statements, mode)),
  };
}

export function createApi({
  provider, getDatabase, verifyPassword, hashPassword, origins = '', mounts = noMounts, routingPackage = '',
  databaseType = 'libsql', gatewaySecret = /** @type {string | null} */ (null),
}) {
  return createHttpHandler(async (request, url, pathname, timing) => {
    const getTimedDatabase = () => timedDatabase(getDatabase(), timing);
    if (pathname === '/health' && request.method === 'GET') {
      const db = getTimedDatabase();
      // Check the required schema along with connectivity.
      await first(db, 'SELECT access_hash FROM hosted_sessions LIMIT 1');
      return json({ status: 'ok', db: databaseType, uptime: uptime(), provider });
    }
    if (!pathname.startsWith('/api/v1/')) throw new HttpError(404, 'Endpoint not found');
    const path = pathname.slice('/api/v1'.length);
    const db = getTimedDatabase();
    if (authPaths.includes(path)) {
      if (request.method !== 'POST') throw new HttpError(405, 'Use POST for this endpoint');
      return authRoute(path, request, db, verifyPassword, hashPassword);
    }
    const publicCatalog = request.method === 'GET' && ['/agencies', '/messages/catalog', '/rescue-units'].includes(path);
    const authStart = performance.now();
    let user;
    try {
      user = publicCatalog && !request.headers.has('authorization') ? null : await authenticate(db, request);
    } finally {
      timing.auth = performance.now() - authStart;
    }
    if (publicCatalog && path === '/rescue-units' && (!user || isCitizen(user))) {
      const agency = url.searchParams.get('agency_id');
      return json(await rows(db, `SELECT id, agency_id, unit_code AS unit_callsign, target_id FROM rescue_units
        ${agency ? 'WHERE agency_id = ?' : ''} ORDER BY id`, agency ? [id(agency)] : []));
    }
    if (!user) {
      const catalog = await registryRoute(path, request, db, null, new URLSearchParams());
      if (catalog) return catalog;
      throw new HttpError(404, 'Catalog not found');
    }
    authorizeRoute(user, path, request.method);
    const routing = routingResponse(path, request.method, user, routingPackage);
    if (routing) return routing;
    if (path === '/info' && request.method === 'GET') {
      const start = Date.now();
      await first(db, 'SELECT 1');
      return json({
        status: 'ok',
        api: { name: 'AGAP', version: 'v1', environment: provider, server_time: now(), uptime: uptime(),
          websocket_endpoint: '', rate_limiting: { login_attempts_per_account: 8 } },
        database: { type: databaseType, status: 'connected', ping: `${Date.now() - start}ms`, statistics: {} },
      });
    }
    if (path === '/system/db/tables' && request.method === 'GET') {
      const tables = await Promise.all(dashboardTables.map(async (name) =>
        ({ name, count: (await first(db, `SELECT COUNT(*) AS count FROM ${name}`)).count })));
      return json({ tables });
    }
    for (const handler of [mobileRoute, accountRoute, registryRoute, directoryRoute, reportRoute, incidentRoute, unitRoute]) {
      const response = await handler(path, request, db, user, url.searchParams);
      if (response) return response;
    }
    throw new HttpError(404, 'Endpoint not found');
  }, { origins, mounts, gatewaySecret });
}

export function routingResponse(path, method, user, routingPackage) {
  if (method !== 'GET' || !['/maps/routing-graph/metadata', '/maps/routing-graph'].includes(path)) return;
  if (isCitizen(user)) throw new HttpError(403, 'Responder access is required');
  const metadata = path.endsWith('/metadata');
  if (!routingPackage) {
    if (metadata) return json({ available: false });
    throw new HttpError(404, 'Routing package is unavailable');
  }
  const { url: download, file_name, size_bytes, sha256, modified_at } = JSON.parse(routingPackage);
  const location = new URL(download);
  if (location.protocol !== 'https:' || location.username || location.password ||
      typeof file_name !== 'string' || !Number.isSafeInteger(size_bytes) || size_bytes < 1 ||
      !/^[a-f\d]{64}$/i.test(sha256) || !Number.isFinite(Date.parse(modified_at))) {
    throw new HttpError(503, 'Routing package configuration is invalid');
  }
  if (metadata) return json({ available: true, file_name, size_bytes, sha256, modified_at });
  return new Response(null, { status: 302, headers: { Location: location.href } });
}

// auth: session lookup; db: all database waits (includes auth); total: whole handler.
export function serverTiming(timing, total) {
  const ms = (value) => value.toFixed(1);
  const entries = [];
  if (timing.auth != null) entries.push(`auth;dur=${ms(timing.auth)}`);
  if (timing.db != null) entries.push(`db;dur=${ms(timing.db)};desc="${timing.statements} statements"`);
  entries.push(`total;dur=${ms(total)}`);
  return entries.join(', ');
}

export function createHttpHandler(route, { origins = '', mounts = noMounts, gatewaySecret = /** @type {string | null} */ (null) } = {}) {
  const allowedOrigins = new Set(origins.split(',').map((item) => item.trim()).filter(Boolean));

  return async (request) => {
    if (gatewaySecret !== null && (!gatewaySecret || request.headers.get('x-agap-gateway') !== gatewaySecret)) {
      return json({ error: 'Endpoint not found' }, 404);
    }
    const url = new URL(request.url);
    const mount = mounts.find((prefix) => url.pathname.startsWith(prefix + '/'));
    const pathname = (mount ? url.pathname.slice(mount.length) : url.pathname).replace(/\/+$/, '');
    const origin = request.headers.get('origin');
    if (origin && origin !== url.origin && !allowedOrigins.has(origin)) {
      return json({ error: 'Origin is not allowed' }, 403);
    }
    let response;
    const started = performance.now();
    const timing = {};
    try {
      response = request.method === 'OPTIONS' ? noContent() : await route(request, url, pathname, timing);
    } catch (error) {
      response = error instanceof HttpError ? json({ error: error.message }, error.status) : databaseError(error);
    }
    response.headers.set('Server-Timing', serverTiming(timing, performance.now() - started));
    response.headers.set('Cache-Control', 'no-store');
    response.headers.set('Vary', 'Origin');
    response.headers.set('X-Content-Type-Options', 'nosniff');
    if (origin) {
      response.headers.set('Access-Control-Allow-Origin', origin);
      response.headers.set('Access-Control-Expose-Headers', 'Server-Timing');
      response.headers.set('Timing-Allow-Origin', origin);
      response.headers.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
      response.headers.set('Access-Control-Allow-Headers', 'Authorization, Content-Type, apikey');
      response.headers.set('Access-Control-Max-Age', '600');
    }
    return response;
  };
}
