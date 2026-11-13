import { HttpError, first, id, json, noContent, now, oneOf, query, readBody, string, withBooleans } from './http.mjs';

export const userColumns = `id, username, role, device_id, agency_id, name, birthday, gender,
  phone_number, email, medical_info, national_id, is_verified, image_url, address_id,
  last_login_at, account_status, created_at, updated_at`;
export const publicUser = (row) => withBooleans(row, 'is_verified');
const approved = "account_status = 'APPROVED' AND is_verified = 1";
const accessMs = 15 * 60 * 1000;
const refreshMs = 7 * 24 * 60 * 60 * 1000;
const tokenPattern = /^[a-f0-9]{64}$/;
const demoPasswords = new Set(['admin123', 'responder123', 'citizen123']);
export const demoAdminHash = '$2a$10$.qmQtqkhx..iPxr8xTyixOO8yQCSW3eb6Sr8vz5pgqGh/SolUNXMC';
const hex = (bytes) => Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
const randomToken = () => hex(crypto.getRandomValues(new Uint8Array(32)));
export const hash = async (value) => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))));

function invalidSession() {
  return new HttpError(401, 'Invalid or expired session');
}

function makeSession() {
  const timestamp = Date.now();
  return {
    token: randomToken(),
    refresh_token: randomToken(),
    expires_at: new Date(timestamp + accessMs).toISOString(),
    refreshExpiresAt: new Date(timestamp + refreshMs).toISOString(),
  };
}

const responseTokens = ({ token, refresh_token, expires_at }) => ({ token, refresh_token, expires_at });
export const bearer = (request) => request.headers.get('authorization')?.match(/^Bearer (\S+)$/i)?.[1];

export async function authenticate(db, request) {
  const token = bearer(request);
  if (!tokenPattern.test(token || '')) throw invalidSession();
  const user = await first(db, `SELECT ${userColumns} FROM users WHERE ${approved}
    AND id = (SELECT user_id FROM hosted_sessions WHERE access_hash = ? AND access_expires_at > ?)`,
  [await hash(token), now()]);
  if (!user) throw invalidSession();
  return publicUser(user);
}

async function limitAttempt(db, key, max) {
  const window = Math.floor(Date.now() / 300_000);
  const attempt = await first(db, `INSERT INTO hosted_login_attempts (login_key, window, count) VALUES (?, ?, 1)
    ON CONFLICT(login_key) DO UPDATE SET window = excluded.window,
    count = CASE WHEN hosted_login_attempts.window = excluded.window THEN hosted_login_attempts.count + 1 ELSE 1 END
    WHERE hosted_login_attempts.window <> excluded.window OR hosted_login_attempts.count < ?
    RETURNING count`, [key, window, max]);
  if (!attempt) throw new HttpError(429, 'Too many attempts. Try again in a few minutes.');
  return window;
}

export async function authRoute(path, request, db, verifyPassword, hashPassword) {
  if (path === '/auth/register') {
    const body = await readBody(request);
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
    if (unit && !await first(db, 'SELECT id FROM rescue_units WHERE id = ? AND agency_id = ?', [unit, agency])) {
      throw new HttpError(400, 'Select a rescue unit from the chosen agency');
    }
    await limitAttempt(db, await hash('registration'), 50);
    const passwordHash = await hashPassword(password, 10);
    const [inserted] = await db.batch([
      { sql: `INSERT INTO users (username, password_hash, role, device_id, name, phone_number, agency_id)
        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING ${userColumns}`,
        args: [username, passwordHash, role, device, name, phone, agency] },
      { sql: "INSERT INTO account_verifications (user_id, status) VALUES (last_insert_rowid(), 'PENDING')", args: [] },
      ...(device ? [{ sql: "INSERT INTO user_devices (user_id, device_hardware_id, platform) SELECT id, ?, 'android' FROM users WHERE username = ?",
        args: [device, username] }] : []),
      ...(unit ? [{ sql: `INSERT INTO rescue_unit_members (rescue_unit_id, responder_id)
        SELECT ?, id FROM users WHERE username = ?`, args: [unit, username] }] : []),
    ], 'write');
    return json({ message: 'Account registered and pending admin verification', user: publicUser(inserted.rows[0]) }, 201);
  }

  if (path === '/auth/login') {
    const { username, password, device_id: deviceId } = await readBody(request);
    const error = new HttpError(401, 'Invalid username or password');
    if (typeof username !== 'string' || !username.trim() || username.length > 128 ||
        typeof password !== 'string' || !password || new TextEncoder().encode(password).length > 72) {
      throw error;
    }
    const name = username.trim();
    const account = await first(db, 'SELECT id, password_hash, account_status, is_verified FROM users WHERE username = ?', [name]);
    const loginKey = await hash(account ? `account:${account.id}` : 'unknown-account');
    const window = await limitAttempt(db, loginKey, 8);

    // Use the same bcrypt work for an unknown account. Seed credentials can never sign in here.
    const matches = await verifyPassword(password, account?.password_hash || demoAdminHash);
    if (!account || !matches || demoPasswords.has(password) || account.password_hash === demoAdminHash) throw error;
    if (!account.is_verified || account.account_status !== 'APPROVED') {
      const message = { REJECTED: 'Account registration was rejected', SUSPENDED: 'Account is suspended' }[account.account_status];
      throw new HttpError(403, message || 'Account is pending admin verification');
    }

    const session = makeSession();
    const [accessHash, refreshHash] = await Promise.all([hash(session.token), hash(session.refresh_token)]);
    const timestamp = now();
    const device = deviceId ? string(deviceId, 'device_id', 128) : '';
    const [inserted, updated] = await db.batch([
      { sql: `INSERT INTO hosted_sessions (user_id, access_hash, refresh_hash, access_expires_at, refresh_expires_at)
        SELECT id, ?, ?, ?, ? FROM users WHERE id = ? AND password_hash = ? AND ${approved}`,
        args: [accessHash, refreshHash, session.expires_at, session.refreshExpiresAt, account.id, account.password_hash] },
      { sql: `UPDATE users SET last_login_at = ? WHERE changes() = 1 AND id = ? RETURNING ${userColumns}`,
        args: [timestamp, account.id] },
      ...(device ? [{
        sql: `INSERT INTO user_devices (user_id, device_hardware_id, platform, last_seen_at)
          SELECT ?, ?, 'android', ? WHERE changes() = 1
          ON CONFLICT(device_hardware_id) DO UPDATE SET last_seen_at = excluded.last_seen_at
          WHERE user_devices.user_id = excluded.user_id`, args: [account.id, device, timestamp],
      }] : []),
      { sql: 'DELETE FROM hosted_sessions WHERE refresh_expires_at <= ?', args: [timestamp] },
      { sql: 'DELETE FROM hosted_login_attempts WHERE window < ? OR login_key = ?', args: [window, loginKey] },
    ], 'write');
    if (!inserted.rowsAffected) throw error;
    return json({ ...responseTokens(session), user: publicUser(updated.rows[0]) });
  }

  if (path === '/auth/refresh') {
    const { refresh_token: refreshToken } = await readBody(request);
    if (typeof refreshToken !== 'string' || !tokenPattern.test(refreshToken)) throw invalidSession();
    const session = makeSession();
    const [accessHash, refreshHash, oldHash] = await Promise.all([
      hash(session.token), hash(session.refresh_token), hash(refreshToken),
    ]);
    const result = await query(db, `UPDATE hosted_sessions
      SET access_hash = ?, refresh_hash = ?, access_expires_at = ?
      WHERE refresh_hash = ? AND refresh_expires_at > ?
      AND user_id IN (SELECT id FROM users WHERE ${approved})`,
    [accessHash, refreshHash, session.expires_at, oldHash, now()]);
    if (!result.rowsAffected) throw invalidSession();
    return json(responseTokens(session));
  }

  const token = bearer(request);
  const { refresh_token: refreshToken } = await readBody(request);
  const [accessHash, refreshHash] = await Promise.all([
    tokenPattern.test(token || '') ? hash(token) : null,
    typeof refreshToken === 'string' && tokenPattern.test(refreshToken) ? hash(refreshToken) : null,
  ]);
  if (accessHash || refreshHash) {
    await query(db, 'DELETE FROM hosted_sessions WHERE access_hash = ? OR refresh_hash = ?', [accessHash, refreshHash]);
  }
  return noContent();
}
