import { bearer, demoAdminHash, demoPasswords, hash, makeSession, publicUser, responseTokens, tokenPattern, userColumns } from './auth.mjs';
import { HttpError, id, json, noContent, now, oneOf, readBody, string } from './http.mjs';

export const eq = (field, value) => [field, '==', value];
export const fields = (row, columns) => Object.fromEntries(columns.map((column) => [column, row[column]]));
export const safeUser = (row) => publicUser(fields(row, userColumns.split(',').map((column) => column.trim())));
const approved = (user) => user?.is_verified === 1 && user.account_status === 'APPROVED';
const invalid = () => new HttpError(401, 'Invalid or expired session');

export async function authenticateFirestore(db, request) {
  const token = bearer(request);
  if (!tokenPattern.test(token || '')) throw invalid();
  const session = await db.get('hosted_sessions', await hash(token));
  if (!session || session.access_expires_at <= now()) throw invalid();
  const user = await db.get('users', session.user_id);
  if (!approved(user) || (session.session_version ?? 0) !== (user.session_version ?? 0)) throw invalid();
  return safeUser(user);
}

async function limitAttempt(db, name, maximum) {
  const loginKey = await hash(name);
  const window = Math.floor(Date.now() / 300_000);
  return db.transaction(async (tx) => {
    const attempt = await tx.get('hosted_login_attempts', loginKey);
    const count = attempt?.window === window ? attempt.count + 1 : 1;
    if (count > maximum) throw new HttpError(429, 'Too many attempts. Try again in a few minutes.');
    if (attempt) await tx.update('hosted_login_attempts', attempt, { window, count });
    else await tx.insert('hosted_login_attempts', { login_key: loginKey, window, count });
    return loginKey;
  });
}

async function privateSession(session, userId, expiry = session.refreshExpiresAt) {
  const [access, refresh] = await Promise.all([hash(session.token), hash(session.refresh_token)]);
  return { user_id: userId, access_hash: access, refresh_hash: refresh,
    access_expires_at: session.expires_at, refresh_expires_at: expiry };
}

export async function firestoreAuth(path, request, db, { verifyPassword, hashPassword }) {
  if (request.method !== 'POST') throw new HttpError(405, 'Use POST for this endpoint');
  const body = await readBody(request);
  if (path === '/auth/register') {
    const username = string(body.username, 'username', 64);
    const password = body.password;
    if (username.length < 3 || typeof password !== 'string' || password.length < 8 ||
        new TextEncoder().encode(password).length > 72 || demoPasswords.has(password)) {
      throw new HttpError(400, 'Use a username of at least 3 characters and a unique password of at least 8 characters');
    }
    if (body.role === 'ADMIN') throw new HttpError(403, 'Administrator accounts require provisioning');
    const role = oneOf(body.role || 'CITIZEN', ['CITIZEN', 'RESPONDER'], 'role');
    const device = body.device_id ? string(body.device_id, 'device_id', 128) : '';
    const name = body.name ? string(body.name, 'name') : '';
    const phone = body.phone_number ? string(body.phone_number, 'phone_number', 64) : '';
    const agency = role === 'RESPONDER' && body.agency_id != null ? id(body.agency_id) : null;
    const unit = role === 'RESPONDER' && body.rescue_unit_id != null ? id(body.rescue_unit_id) : null;
    await limitAttempt(db, 'registration', 50);
    const passwordHash = await hashPassword(password, 10);
    const user = await db.transaction(async (tx) => {
      if (unit && (await tx.get('rescue_units', unit))?.agency_id !== agency) {
        throw new HttpError(400, 'Select a rescue unit from the chosen agency');
      }
      const account = await tx.insert('users', { username, password_hash: passwordHash, role, device_id: device,
        name, phone_number: phone, agency_id: agency });
      await tx.insert('account_verifications', { user_id: account.id, status: 'PENDING' });
      if (device) await tx.insert('user_devices', { user_id: account.id, device_hardware_id: device, platform: 'android' });
      if (unit) await tx.insert('rescue_unit_members', { rescue_unit_id: unit, responder_id: account.id });
      return safeUser(account);
    });
    return json({ message: 'Account registered and pending admin verification', user }, 201);
  }
  if (path === '/auth/login') {
    const { username, password, device_id: deviceId } = body;
    const error = new HttpError(401, 'Invalid username or password');
    if (typeof username !== 'string' || !username.trim() || username.length > 128 ||
        typeof password !== 'string' || !password || new TextEncoder().encode(password).length > 72) throw error;
    const account = await db.find('users', [eq('username', username.trim())]);
    const loginKey = await limitAttempt(db, account ? `account:${account.id}` : 'unknown-account', 8);
    const matches = await verifyPassword(password, account?.password_hash || demoAdminHash);
    if (!account || !matches || demoPasswords.has(password) || account.password_hash === demoAdminHash) throw error;
    if (!approved(account)) {
      const message = { REJECTED: 'Account registration was rejected', SUSPENDED: 'Account is suspended' }[account.account_status];
      throw new HttpError(403, message || 'Account is pending admin verification');
    }
    const session = makeSession();
    const record = await privateSession(session, account.id);
    const device = deviceId ? string(deviceId, 'device_id', 128) : '';
    const user = await db.transaction(async (tx) => {
      const current = await tx.get('users', account.id);
      if (!approved(current) || current.password_hash !== account.password_hash) throw error;
      const attempt = await tx.get('hosted_login_attempts', loginKey);
      if (attempt) await tx.remove('hosted_login_attempts', attempt);
      await tx.insert('hosted_sessions', { ...record, session_version: current.session_version ?? 0 });
      if (device) {
        const linked = await tx.find('user_devices', [eq('device_hardware_id', device)]);
        if (!linked) await tx.insert('user_devices', { user_id: account.id, device_hardware_id: device, platform: 'android' });
        else if (linked.user_id === account.id) await tx.update('user_devices', linked, { last_seen_at: now() });
      }
      return safeUser(await tx.update('users', current, { last_login_at: now() }));
    });
    return json({ ...responseTokens(session), user });
  }
  const refreshHash = tokenPattern.test(body.refresh_token || '') ? await hash(body.refresh_token) : null;
  if (path === '/auth/refresh') {
    if (!refreshHash) throw invalid();
    const session = makeSession();
    await db.transaction(async (tx) => {
      const old = await tx.find('hosted_sessions', [eq('refresh_hash', refreshHash)]);
      const user = old && await tx.get('users', old.user_id);
      if (!old || old.refresh_expires_at <= now() || !approved(user) || (old.session_version ?? 0) !== (user.session_version ?? 0)) throw invalid();
      await tx.remove('hosted_sessions', old);
      await tx.insert('hosted_sessions', { ...await privateSession(session, old.user_id, old.refresh_expires_at),
        session_version: user.session_version ?? 0 });
    });
    return json(responseTokens(session));
  }
  const token = bearer(request);
  if (refreshHash || tokenPattern.test(token || '')) {
    await db.transaction(async (tx) => {
      const access = tokenPattern.test(token || '') ? await tx.get('hosted_sessions', await hash(token)) : null;
      if (access) await tx.remove('hosted_sessions', access);
      const refresh = refreshHash ? await tx.find('hosted_sessions', [eq('refresh_hash', refreshHash)]) : null;
      if (refresh) await tx.remove('hosted_sessions', refresh);
    });
  }
  return noContent();
}
