import { createHash } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import { schema } from '../shared/schema.mjs';

export const transient = new Set(['hosted_sessions', 'hosted_login_attempts', 'refresh_tokens']);
export const snapshotTables = Object.keys(schema).filter((table) => !transient.has(table));
export const quote = (name) => `"${name.replaceAll('"', '""')}"`;
const keys = (table, row) => JSON.stringify(schema[table].keys.map((key) => row[key]));

function timestamp(value) {
  const utc = /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d(?:\.\d+)?$/.test(value) ? value.replace(' ', 'T') + 'Z' : value;
  return Number.isFinite(Date.parse(utc)) ? new Date(utc).toISOString() : value;
}

export function snapshotRow(table, row) {
  return Object.fromEntries(Object.keys(schema[table].defaults).map((field) => {
    if (!Object.hasOwn(row, field)) throw new Error(`Missing column ${table}.${field}`);
    const value = row[field];
    if (typeof value === 'number' && (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value)))) {
      throw new Error(`Unsupported number in ${table}.${field}`);
    }
    return [field, typeof value === 'string' && (field.endsWith('_at') || field === 'event_timestamp') ? timestamp(value) : value];
  }));
}

export function buildSnapshot(input, counters = {}) {
  const tables = {};
  const sequences = {};
  const ids = new Map();
  for (const table of snapshotTables) {
    if (!Array.isArray(input[table])) throw new Error(`Missing table ${table}`);
    tables[table] = input[table].map((row) => snapshotRow(table, row)).sort((a, b) => {
      const left = keys(table, a), right = keys(table, b);
      return left < right ? -1 : left > right ? 1 : 0;
    });
    const primary = new Set();
    let maxId = 0;
    for (const row of tables[table]) {
      for (const field of schema[table].required) if (row[field] == null) throw new Error(`Missing data in ${table}.${field}`);
      const key = keys(table, row);
      if (primary.has(key)) throw new Error(`Duplicate primary key in ${table}`);
      primary.add(key);
      if (schema[table].generatedId) {
        if (!Number.isSafeInteger(row.id) || row.id < 1) throw new Error(`Invalid ID in ${table}`);
        maxId = Math.max(maxId, row.id);
      }
    }
    if (schema[table].generatedId) {
      const sequence = Number(counters[table] ?? 0);
      if (!Number.isSafeInteger(sequence) || sequence < 0) throw new Error(`Invalid sequence for ${table}`);
      sequences[table] = Math.max(maxId, sequence);
    }
    ids.set(table, primary);
  }
  for (const [table, records] of Object.entries(tables)) {
    for (const [field, target, column] of schema[table].references) {
      if (schema[target].keys.join(',') !== column) throw new Error(`Unsupported foreign key in ${table}`);
      if (records.some((row) => row[field] != null && !ids.get(target)?.has(JSON.stringify([row[field]])))) {
        throw new Error(`Broken foreign key ${table}.${field}`);
      }
    }
  }
  const contents = { version: 1, sequences, tables };
  return { ...contents, digest: createHash('sha256').update(JSON.stringify(contents)).digest('hex') };
}

export function validateSnapshot(snapshot) {
  const rebuilt = buildSnapshot(snapshot.tables, snapshot.sequences);
  if (snapshot.version !== rebuilt.version || snapshot.digest !== rebuilt.digest) throw new Error('Snapshot format or checksum does not match');
  return rebuilt;
}

export function exportSqlite(input) {
  const db = new DatabaseSync(input, { readOnly: true });
  try {
    const broken = db.prepare('PRAGMA foreign_key_check').get();
    if (broken) throw new Error(`Invalid SQLite foreign key in ${broken.table}`);
    return buildSnapshot(Object.fromEntries(snapshotTables.map((table) => [table, db.prepare(`SELECT * FROM ${quote(table)}`).all()])),
      Object.fromEntries(db.prepare('SELECT name, seq FROM sqlite_sequence').all().map(({ name, seq }) => [name, seq])));
  } finally {
    db.close();
  }
}

export async function exportPostgres(client) {
  const tx = await client.connect();
  try {
    await tx.query('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
    const tables = {};
    for (const table of snapshotTables) tables[table] = (await tx.query(`SELECT * FROM agap.${quote(table)}`)).rows;
    const sequences = {};
    for (const table of snapshotTables.filter((name) => schema[name].generatedId)) {
      const { last_value, is_called } = (await tx.query(`SELECT last_value, is_called FROM agap.${quote(`${table}_id_seq`)}`)).rows[0];
      sequences[table] = Number(last_value) - Number(!is_called);
    }
    const result = buildSnapshot(tables, sequences);
    await tx.query('COMMIT');
    return result;
  } catch (error) {
    await tx.query('ROLLBACK');
    throw error;
  } finally {
    tx.release();
  }
}

export async function importPostgres(client, input, { replace = false } = {}) {
  const snapshot = validateSnapshot(input);
  const tx = await client.connect();
  try {
    await tx.query('BEGIN');
    const allTables = Object.keys(schema).map((table) => `agap.${quote(table)}`).join(', ');
    await tx.query(`LOCK TABLE ${allTables} IN ACCESS EXCLUSIVE MODE`);
    if (replace) await tx.query(`TRUNCATE ${allTables} RESTART IDENTITY`);
    else {
      for (const table of Object.keys(schema)) {
        if ((await tx.query(`SELECT 1 FROM agap.${quote(table)} LIMIT 1`)).rows.length) {
          throw new Error('PostgreSQL is not empty. Freeze writes and use --replace only after backing it up.');
        }
      }
    }
    await tx.query('SET CONSTRAINTS ALL DEFERRED');
    for (const [table, sequence] of Object.entries(snapshot.sequences)) {
      await tx.query(`ALTER TABLE agap.${quote(table)} ALTER COLUMN id RESTART WITH ${sequence + 1}`);
    }
    for (const [table, records] of Object.entries(snapshot.tables)) {
      const fields = Object.keys(schema[table].defaults);
      for (let position = 0; position < records.length; position += 200) {
        const values = records.slice(position, position + 200).flatMap((row) => fields.map((field) => row[field]));
        const tuples = Array.from({ length: values.length / fields.length }, (_, row) =>
          `(${fields.map((_, column) => `$${row * fields.length + column + 1}`).join(', ')})`);
        await tx.query(`INSERT INTO agap.${quote(table)} (${fields.map(quote).join(', ')}) VALUES ${tuples.join(', ')}`, values);
      }
    }
    await tx.query('COMMIT');
  } catch (error) {
    await tx.query('ROLLBACK');
    throw error;
  } finally {
    tx.release();
  }
}
