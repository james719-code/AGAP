import { createFirestoreStore, firestoreControl, uniqueFields } from '../shared/firestore-store.mjs';
import { schema } from '../shared/schema.mjs';
import { buildSnapshot, snapshotRow, snapshotTables, validateSnapshot } from './snapshot.mjs';

const commitSize = 200;
const dailyLimit = 10_000;
const dayFormat = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Los_Angeles', dateStyle: 'short' });

export function firestoreWrites(snapshot) {
  const readers = new Map();
  for (const report of snapshot.tables.incident_reports) {
    if (report.incident_id == null || report.reporter_user_id == null) continue;
    if (!readers.has(report.incident_id)) readers.set(report.incident_id, new Set());
    readers.get(report.incident_id).add(report.reporter_user_id);
  }
  const writes = new Map();
  const add = (table, keys, row) => {
    const key = JSON.stringify([table, keys]);
    if (writes.has(key)) throw new Error(`Duplicate unique key in ${table}`);
    writes.set(key, { table, keys, row });
  };
  for (const [table, records] of Object.entries(snapshot.tables)) {
    for (const row of records) {
      let data = row;
      if (table === 'incidents') {
        const visible = new Set(readers.get(row.id));
        if (row.citizen_id != null) visible.add(row.citizen_id);
        data = { ...row, visible_to: [...visible].sort((a, b) => a - b) };
      }
      const keys = schema[table].keys.map((key) => row[key]);
      add(table, keys, data);
      for (const field of uniqueFields(table, row)) add('_unique', [table, ...field], { keys });
    }
  }
  for (const [table, value] of Object.entries(snapshot.sequences)) add('_sequences', [table], { value });
  return [...writes.values()];
}

export async function importFirestore(db, input, { maxWrites = dailyLimit } = {}) {
  const snapshot = validateSnapshot(input);
  if (!Number.isSafeInteger(maxWrites) || maxWrites < 3 || maxWrites > dailyLimit) throw new Error('Invalid Firestore write budget');
  const store = createFirestoreStore(db, snapshot.digest);
  const writes = firestoreWrites(snapshot);
  const progressRef = firestoreControl(db, `import-${snapshot.digest}`);
  const dailyRef = firestoreControl(db, `daily-${dayFormat.format(new Date()).replaceAll('/', '-')}`);
  const readyRef = store.reference('_migration', ['ready']);
  let budget = maxWrites;
  for (;;) {
    const result = await db.runTransaction(async (tx) => {
      const [progress, daily] = await Promise.all([tx.get(progressRef), tx.get(dailyRef)]);
      const done = progress.data()?.done ?? 0;
      const used = daily.data()?.used ?? 0;
      if (progress.data()?.complete) return { done, complete: true, cost: 0 };
      const available = Math.min(budget, dailyLimit - used);
      const batch = writes.slice(done, done + Math.max(0, Math.min(commitSize, available - 3)));
      const complete = done + batch.length === writes.length;
      const cost = batch.length + 2 + Number(complete);
      if (cost > available || (!batch.length && !complete)) return { done, complete: false, cost: 0 };
      for (const { table, keys, row } of batch) tx.set(store.reference(table, keys), row);
      if (complete) tx.set(readyRef, { digest: snapshot.digest });
      tx.set(progressRef, { done: done + batch.length, complete });
      tx.set(dailyRef, { used: used + cost });
      return { done: done + batch.length, complete, cost };
    });
    if (!result.cost || result.complete) return { ...result, total: writes.length };
    budget -= result.cost;
  }
}

export async function exportFirestore(db, generation) {
  if (!generation) generation = (await firestoreControl(db, 'active').get()).data()?.generation;
  if (!generation || !/^[a-f0-9]{64}$/.test(generation)) throw new Error('No valid Firestore snapshot generation selected');
  const store = createFirestoreStore(db, generation);
  if (!(await store.get('_migration', 'ready'))?.digest) throw new Error('Firestore import has not completed');
  const tables = {};
  for (const table of snapshotTables) {
    tables[table] = [];
    for await (const record of store.collection(table).stream()) tables[table].push(snapshotRow(table, record.data()));
  }
  const sequences = {};
  for (const row of await store.collection('_sequences').get().then((data) => data.docs)) {
    const [table] = JSON.parse(decodeURIComponent(row.id));
    sequences[table] = row.data().value;
  }
  return buildSnapshot(tables, sequences);
}

export async function activateFirestore(db, input) {
  const snapshot = validateSnapshot(input);
  const copy = await exportFirestore(db, snapshot.digest);
  if (copy.digest !== snapshot.digest) throw new Error('Firestore read-back did not match the source. Active database is unchanged.');
  await firestoreControl(db, 'active').set({ generation: snapshot.digest, active: true });
}
