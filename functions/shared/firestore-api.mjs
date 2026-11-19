import { authorizeRoute, isCitizen } from './access.mjs';
import { authPaths, createHttpHandler, dashboardTables, routingResponse, uptime } from './api.mjs';
import { authenticateFirestore, eq, fields, firestoreAuth } from './firestore-auth.mjs';
import { firestoreAccounts, firestoreDirectory, firestoreRegistry } from './firestore-directory.mjs';
import { firestoreIncidents, firestoreUnits, unitView } from './firestore-incidents.mjs';
import { firestoreMobile } from './firestore-mobile.mjs';
import { HttpError, id, json, now } from './http.mjs';

export function createFirestoreApi({ getStore, verifyPassword, hashPassword, routingPackage, ...options }) {
  return createHttpHandler(async (request, url, pathname) => {
    const start = Date.now();
    const db = await getStore();
    if (pathname === '/health' && request.method === 'GET') {
      const migration = await db.get('_migration', 'ready');
      if (!migration?.digest) throw new HttpError(503, 'Firestore standby is not ready');
      return json({ status: 'ok', db: 'firestore', uptime: uptime(), provider: 'firebase' });
    }
    if (!pathname.startsWith('/api/v1/')) throw new HttpError(404, 'Endpoint not found');
    const path = pathname.slice('/api/v1'.length);
    if (authPaths.includes(path)) return firestoreAuth(path, request, db, { verifyPassword, hashPassword });
    const publicCatalog = request.method === 'GET' && ['/agencies', '/messages/catalog', '/rescue-units'].includes(path);
    const user = publicCatalog && !request.headers.has('authorization') ? null : await authenticateFirestore(db, request);
    if (publicCatalog && path === '/rescue-units' && (!user || isCitizen(user))) {
      const agency = url.searchParams.get('agency_id');
      const records = await db.list('rescue_units', { where: agency ? [eq('agency_id', id(agency))] : [], order: [['id', 'asc']] });
      return json(records.map((row) => fields(unitView(row), ['id', 'agency_id', 'unit_callsign', 'target_id'])));
    }
    if (!user) return firestoreRegistry(path, request, db, null, new URLSearchParams());
    authorizeRoute(user, path, request.method);
    const routing = routingResponse(path, request.method, user, routingPackage);
    if (routing) return routing;
    if (path === '/info' && request.method === 'GET') {
      return json({ status: 'ok', api: { name: 'AGAP', version: 'v1', environment: 'firebase', server_time: now(), uptime: uptime(),
        websocket_endpoint: '', rate_limiting: { login_attempts_per_account: 8 } },
      database: { type: 'firestore', status: 'connected', ping: `${Date.now() - start}ms`, statistics: {} } });
    }
    if (path === '/system/db/tables' && request.method === 'GET') {
      return json({ tables: await Promise.all(dashboardTables.map(async (name) => ({ name, count: await db.count(name) }))) });
    }
    for (const handler of [firestoreMobile, firestoreAccounts, firestoreRegistry, firestoreDirectory, firestoreIncidents, firestoreUnits]) {
      const response = await handler(path, request, db, user, url.searchParams);
      if (response) return response;
    }
    throw new HttpError(404, 'Endpoint not found');
  }, options);
}
