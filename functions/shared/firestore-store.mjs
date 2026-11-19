import { HttpError, now } from './http.mjs';
import { schema } from './schema.mjs';

export const activeStatuses = ['ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE'];
const mutationsLimit = 400;
const collectionPrefix = 'agap_';
const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const documentId = (keys) => encodeURIComponent(JSON.stringify(keys));
const conflict = (message) => new HttpError(409, message);

export function uniqueFields(table, row) {
  return Object.entries(schema[table].unique).flatMap(([fields, condition]) => {
    const columns = fields.split(',');
    const values = columns.map((field) => row[field]);
    if (values.some((value) => value == null)) return [];
    if (condition === 'idx_users_device_id_unique_nonempty' && !row.device_id.trim()) return [];
    if (condition === 'idx_incident_messages_incident_sender_client_message' && !row.client_message_id.trim()) return [];
    if (condition === 'idx_incident_dispatches_one_active_per_unit' && !activeStatuses.includes(row.status)) return [];
    return [[fields, ...values]];
  });
}

function matches(row, [field, operator, value]) {
  switch (operator) {
    case '==': return row[field] === value;
    case 'in': return value.includes(row[field]);
    case 'array-contains': return row[field]?.includes(value) ?? false;
    case '>': return row[field] > value;
    case '<': return row[field] < value;
    case '>=': return row[field] >= value;
    case '<=': return row[field] <= value;
    default: throw new Error(`Unsupported transaction filter: ${operator}`);
  }
}

export function ordered(rows, order) {
  return rows.sort((first, second) => {
    for (const [field, direction] of order) {
      if (first[field] === second[field]) continue;
      return (first[field] < second[field] ? -1 : 1) * (direction === 'desc' ? -1 : 1);
    }
    return 0;
  });
}

export function createFirestoreStore(database, generation) {
  const root = generation ? database.collection('agap_snapshots').doc(generation) : database;
  const collection = (name) => root.collection(`${collectionPrefix}${name}`);
  const reference = (table, keys) => collection(table).doc(documentId(keys));
  const keyOf = (table, row) => schema[table].keys.map((field) => row[field]);

  function scope(transaction) {
    // Firestore transactions require all reads before writes. Buffer mutations and overlay
    // them on subsequent reads until the application operation has validated successfully.
    const pending = new Map();
    const read = (ref) => transaction ? transaction.get(ref) : ref.get();
    const write = (ref, data) => {
      if (!transaction) throw new Error('Database mutations require a transaction');
      pending.set(ref.path, { ref, data });
      if (pending.size > mutationsLimit) throw conflict('Too many related records for one change');
    };
    async function byRef(ref) {
      if (pending.has(ref.path)) return pending.get(ref.path).data;
      return (await read(ref)).data() ?? null;
    }
    const get = (table, ...keys) => byRef(reference(table, keys));
    function filtered(table, where) {
      return where.reduce((query, [field, op, value]) => query.where(field, op, value), collection(table));
    }
    async function list(table, { where = [], order = [], limit, offset = 0, after } = {}) {
      let query = filtered(table, where);
      for (const [field, direction] of order) query = query.orderBy(field, direction);
      if (after) query = query.startAfter(...after);
      if (!transaction && offset) query = query.offset(offset);
      if (limit) query = query.limit(limit + (transaction ? pending.size + offset : 0));
      const result = await read(query);
      if (!transaction || !pending.size) return result.docs.map((doc) => doc.data()).slice(transaction ? offset : 0);

      const prefix = `${collection(table).path}/`;
      const rows = new Map(result.docs.map((doc) => [doc.ref.path, doc.data()]));
      for (const [path, { data }] of pending) {
        if (!path.startsWith(prefix)) continue;
        if (data && where.every((filter) => matches(data, filter))) rows.set(path, data);
        else rows.delete(path);
      }
      const values = ordered([...rows.values()], order);
      return values.slice(offset, limit ? offset + limit : undefined);
    }
    const find = async (table, where) => (await list(table, { where, limit: 1 }))[0] ?? null;
    async function count(table, where = [], order = []) {
      let query = filtered(table, where);
      for (const [field, direction] of order) query = query.orderBy(field, direction);
      return (await query.count().get()).data().count;
    }

    async function save(table, next, previous) {
      const definition = schema[table];
      for (const field of definition.required) {
        if (next[field] == null) throw conflict(`Missing required field ${field}`);
      }
      for (const [field, target, key] of definition.references) {
        if (next[field] == null || next[field] === previous?.[field]) continue;
        const referenced = schema[target].keys[0] === key
          ? await get(target, next[field]) : await find(target, [[key, '==', next[field]]]);
        if (!referenced) throw conflict(`Related ${target} record was not found`);
      }
      const keys = keyOf(table, next);
      const current = uniqueFields(table, next);
      const old = previous ? uniqueFields(table, previous) : [];
      for (const field of current) {
        if (old.some((prior) => same(prior, field))) continue;
        const ref = reference('_unique', [table, ...field]);
        const claimed = await byRef(ref);
        if (claimed && !same(claimed.keys, keys)) throw conflict('The change conflicts with an existing record');
        write(ref, { keys });
      }
      for (const field of old) {
        if (!current.some((entry) => same(entry, field))) write(reference('_unique', [table, ...field]), null);
      }
      write(reference(table, keys), next);
      return next;
    }

    async function insert(table, values) {
      const definition = schema[table];
      const timestamp = now();
      const row = Object.fromEntries(Object.entries(definition.defaults).map(([field, value]) => [field,
        value?.generated === 'now' ? timestamp : value?.generated === 'uuid' ? crypto.randomUUID() : value]));
      Object.assign(row, values);
      if (definition.generatedId && row.id == null) {
        const ref = reference('_sequences', [table]);
        row.id = ((await byRef(ref))?.value ?? 0) + 1;
        write(ref, { value: row.id });
      }
      if (await get(table, ...keyOf(table, row))) throw conflict('The change conflicts with an existing record');
      return save(table, row, null);
    }

    async function remove(table, row) {
      const ref = reference(table, keyOf(table, row));
      write(ref, null);
      for (const field of uniqueFields(table, row)) write(reference('_unique', [table, ...field]), null);
      for (const [source, definition] of Object.entries(schema)) {
        for (const [column, target, key, action] of definition.references) {
          if (target !== table) continue;
          const children = await list(source, { where: [[column, '==', row[key]]], limit: mutationsLimit + 1 });
          for (const child of children) {
            if (action === 'CASCADE') await remove(source, child);
            else if (action === 'SET NULL') await save(source, { ...child, [column]: null }, child);
            else throw conflict('This record is still in use');
          }
        }
      }
    }

    return {
      get, list, find, count, insert, remove,
      update: (table, row, changes) => save(table, { ...row, ...changes }, row),
      flush() {
        for (const { ref, data } of pending.values()) {
          if (data) transaction.set(ref, data);
          else transaction.delete(ref);
        }
      },
    };
  }

  return {
    ...scope(null),
    async transaction(run) {
      return database.runTransaction(async (transaction) => {
        const operations = scope(transaction);
        const response = await run(operations);
        operations.flush();
        return response;
      });
    },
    collection, reference,
  };
}

export const firestoreControl = (database, name) => database.collection('agap_control').doc(name);

export async function activeFirestoreStore(database) {
  const active = (await firestoreControl(database, 'active').get()).data();
  if (!active?.generation || !active.active) throw new HttpError(503, 'Firestore standby is not ready');
  return createFirestoreStore(database, active.generation);
}
