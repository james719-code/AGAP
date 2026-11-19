import { readFileSync, writeFileSync } from 'node:fs';
import { parseArgs } from 'node:util';
import { Firestore } from '@google-cloud/firestore';
import pg from 'pg';
import { connectPostgres } from '../shared/postgres.mjs';
import { activateFirestore, exportFirestore, firestoreWrites, importFirestore } from './firestore-migration.mjs';
import { exportPostgres, exportSqlite, importPostgres, validateSnapshot } from './snapshot.mjs';

const { positionals: [command], values } = parseArgs({ allowPositionals: true, options: {
  input: { type: 'string' }, output: { type: 'string' }, project: { type: 'string' },
  replace: { type: 'boolean' }, 'max-writes': { type: 'string' },
} });

function required(field) {
  if (!values[field]) throw new Error(`Supply --${field}`);
  return values[field];
}

const readSnapshot = () => validateSnapshot(JSON.parse(readFileSync(required('input'), 'utf8')));
const writeSnapshot = (snapshot) => writeFileSync(required('output'), JSON.stringify(snapshot), { flag: 'wx', mode: 0o600 });

async function main() {
  if (command === 'export-sqlite') return writeSnapshot(exportSqlite(required('input')));
  if (command === 'inspect') {
    const snapshot = readSnapshot();
    console.log(JSON.stringify({ digest: snapshot.digest, firestoreDocumentWrites: firestoreWrites(snapshot).length,
      counts: Object.fromEntries(Object.entries(snapshot.tables).map(([table, rows]) => [table, rows.length])) }, null, 2));
    return;
  }
  if (command === 'import-postgres' || command === 'export-postgres') {
    if (!process.env.AGAP_MIGRATION_POSTGRES_URL) throw new Error('Set AGAP_MIGRATION_POSTGRES_URL');
    const client = connectPostgres(pg, process.env.AGAP_MIGRATION_POSTGRES_URL, process.env.AGAP_POSTGRES_CA_PEM);
    try {
      if (command === 'import-postgres') await importPostgres(client, readSnapshot(), { replace: values.replace });
      else writeSnapshot(await exportPostgres(client));
    } finally {
      await client.end();
    }
    return;
  }
  if (!['import-firestore', 'export-firestore', 'activate-firestore'].includes(command)) {
    throw new Error('Choose export-sqlite, inspect, import-postgres, export-postgres, import-firestore, activate-firestore or export-firestore');
  }
  const db = new Firestore({ projectId: required('project') });
  try {
    if (command === 'import-firestore') {
      const result = await importFirestore(db, readSnapshot(), { maxWrites: values['max-writes'] ? Number(values['max-writes']) : undefined });
      console.log(`${result.done}/${result.total} Firestore documents written. ${result.complete ? 'Import complete.' : 'Run again to resume.'}`);
      if (!result.complete) process.exitCode = 2;
    } else if (command === 'activate-firestore') await activateFirestore(db, readSnapshot());
    else writeSnapshot(await exportFirestore(db));
  } finally {
    await db.terminate();
  }
}

try {
  await main();
} catch (error) {
  console.error(error.code && error.code !== 'ENOENT' ? `Migration failed (${error.code})` : error.message);
  process.exitCode = 1;
}
