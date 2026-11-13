import { existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { prepareSchema } from './sqlite.mjs';
import { demoAdminHash } from '../shared/auth.mjs';

const [outputFile, sourceFile] = process.argv.slice(2);
if (!outputFile) throw new Error('Usage: npm run prepare:db -- <new.db> [existing-agap.db]');
const output = resolve(outputFile);
if (existsSync(output)) throw new Error('Choose a new output file');
if (sourceFile && !existsSync(sourceFile)) throw new Error('Source SQLite file does not exist');

const username = process.env.AGAP_ADMIN_USERNAME?.trim();
const password = process.env.AGAP_ADMIN_PASSWORD;
if ((username || password || !sourceFile) &&
    (!username || username.length > 128 || !password || password.length < 12 || Buffer.byteLength(password) > 72)) {
  throw new Error('Set AGAP_ADMIN_USERNAME and an AGAP_ADMIN_PASSWORD of at least 12 characters and at most 72 bytes');
}
const passwordHash = password ? await (await import('bcryptjs')).hash(password, 10) : null;
mkdirSync(dirname(output), { recursive: true });

if (sourceFile) {
  const source = new DatabaseSync(resolve(sourceFile), { readOnly: true });
  try {
    source.prepare('VACUUM INTO ?').run(output);
  } finally {
    source.close();
  }
}
const db = new DatabaseSync(output);
try {
  prepareSchema(db, !sourceFile);
  db.exec(`BEGIN;
    DELETE FROM hosted_sessions;
    DELETE FROM hosted_login_attempts;
    DELETE FROM refresh_tokens;`);
  db.prepare(`UPDATE users SET account_status = 'SUSPENDED', is_verified = 0
    WHERE username = 'admin' AND password_hash = ?`).run(demoAdminHash);
  if (passwordHash) {
    const result = db.prepare(`INSERT INTO users (username, password_hash, role, name, account_status, is_verified)
      VALUES (?, ?, 'ADMIN', 'AGAP Administrator', 'APPROVED', 1)
      ON CONFLICT(username) DO UPDATE SET password_hash = excluded.password_hash,
        account_status = 'APPROVED', is_verified = 1, updated_at = excluded.updated_at
      WHERE users.role = 'ADMIN'`).run(username, passwordHash);
    if (!result.changes) throw new Error('Choose an unused username or an existing administrator');
  }
  db.exec('COMMIT');
  const broken = db.prepare('PRAGMA foreign_key_check').get();
  if (broken) throw new Error(`The SQLite snapshot has a foreign key violation in ${broken.table}`);
} finally {
  db.close();
}
console.log(`Prepared ${output} for import`);
