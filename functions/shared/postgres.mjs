import { HttpError } from './http.mjs';

const clock = "to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')";
const trackedInserts = new Set(['users', 'incidents', 'incident_dispatches']);

function sqlForPostgres(sql, args, state) {
  let position = 0;
  let query = sql
    .replaceAll("strftime('%Y-%m-%dT%H:%M:%fZ','now')", clock)
    // Postgres writes and validated imports store UTC ISO timestamps with fixed precision.
    .replace(/julianday\('now'\)/g, clock)
    .replace(/julianday\(([^)]+)\)/g, '$1')
    .replace(/\bwindow\b/g, '"window"')
    .replace(/\bchanges\(\)/g, String(state.changes))
    .replace(/\blast_insert_rowid\(\)/g, state.insertedId == null ? 'NULL' : String(state.insertedId))
    .replace(/'[^']*(?:''[^']*)*'|\?/g, (token) => token === '?' ? `$${++position}` : token)
    .replace(/(\$\d+) IS NULL/g, '$1::text IS NULL')
    .replace(/(,\s*)NOT EXISTS (\(SELECT 1 FROM user_emergency_contacts WHERE user_id = \$\d+\))/g,
      '$1CASE WHEN NOT EXISTS $2 THEN 1 ELSE 0 END');
  if (position !== args.length) throw new Error('Incorrect PostgreSQL statement parameter count');
  const insertedInto = query.match(/^\s*INSERT INTO (\w+)/i)?.[1];
  const trackId = trackedInserts.has(insertedInto) || (insertedInto && /\bRETURNING\b/i.test(query));
  if (trackId && !/\bRETURNING\b/i.test(query)) query += ' RETURNING id';
  return { query, trackId };
}

const schemaPath = 'agap, public';

export function postgresFactory(connect, connectionString) {
  let client;
  // Resolved once per instance: true when the login role already defaults to
  // the agap schema (ALTER ROLE ... SET search_path), so queries need no
  // per-transaction SET LOCAL and single reads can skip the transaction.
  let roleSearchPath;
  return () => {
    if (!connectionString) throw new HttpError(503, 'Hosted database configuration is missing');
    client ??= connect(connectionString);

    async function hasRoleSearchPath(tx) {
      if (roleSearchPath === undefined) {
        try {
          const { rows } = await tx.query("SELECT current_setting('search_path') AS path");
          roleSearchPath = /^\s*"?agap"?\s*,/.test(rows[0]?.path ?? '');
        } catch {
          roleSearchPath = false;
        }
      }
      return roleSearchPath;
    }

    // One round trip for a lone SELECT. Each pooler statement is its own
    // snapshot already, so a read-only transaction adds nothing but latency.
    async function readOne(statement) {
      const tx = await client.connect();
      let broken;
      try {
        if (!await hasRoleSearchPath(tx)) return null;
        const { query } = sqlForPostgres(statement.sql, statement.args ?? [], { changes: 0, insertedId: null });
        const result = await tx.query(query, statement.args ?? []);
        return { rows: result.rows, rowsAffected: 0 };
      } catch (error) {
        // SQL errors carry a SQLSTATE and leave the socket usable; anything
        // else (network, TLS) means the connection should be discarded.
        if (!error?.code) broken = error;
        throw error;
      } finally {
        tx.release(broken);
      }
    }

    async function run(statements, mode = 'read') {
      for (let retry = 0; ; retry++) {
        const tx = await client.connect();
        let broken;
        try {
          const begin = mode === 'write' ? 'BEGIN ISOLATION LEVEL SERIALIZABLE' : 'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY';
          // A parameterless multi-statement query costs a single round trip.
          await tx.query(roleSearchPath ? begin : `${begin}; SET LOCAL search_path = ${schemaPath}`);
          const state = { changes: 0, insertedId: null };
          const results = [];
          for (const { sql, args = [] } of statements) {
            const { query, trackId } = sqlForPostgres(sql, args, state);
            const result = await tx.query(query, args);
            if (result.command !== 'SELECT') state.changes = result.rowCount ?? 0;
            // SQLite SELECTs leave changes() and last_insert_rowid() intact.
            if (trackId && Number.isSafeInteger(result.rows[0]?.id)) state.insertedId = result.rows[0].id;
            results.push({ rows: result.rows, rowsAffected: result.command === 'SELECT' ? 0 : result.rowCount ?? 0 });
          }
          await tx.query('COMMIT');
          return results;
        } catch (error) {
          try { await tx.query('ROLLBACK'); } catch (rollbackError) { broken = rollbackError; }
          if (!['40001', '40P01'].includes(error.code) || retry === 3) throw error;
        } finally {
          tx.release(broken);
        }
        await new Promise((resolve) => setTimeout(resolve, 10 * 2 ** retry + Math.random() * 10));
      }
    }

    return {
      execute: async (statement) => {
        if (/^\s*SELECT\b/i.test(statement.sql)) return (await readOne(statement)) ?? (await run([statement], 'read'))[0];
        return (await run([statement], 'write'))[0];
      },
      batch: async (statements, mode = 'read') => {
        if (mode === 'read' && statements.length === 1) {
          const result = await readOne(statements[0]);
          if (result) return [result];
        }
        return run(statements, mode);
      },
    };
  };
}

export function connectPostgres(pg, connectionString, caPem) {
  let ssl;
  if (caPem) {
    const url = new URL(connectionString);
    // node-postgres replaces its SSL options if any of these appear in the URL.
    for (const key of ['ssl', 'sslmode', 'sslrootcert', 'sslcert', 'sslkey']) url.searchParams.delete(key);
    connectionString = url.href;
    ssl = { ca: caPem.replaceAll('\\n', '\n'), rejectUnauthorized: true };
  }
  const pool = new pg.Pool({
    connectionString, ...(ssl ? { ssl } : {}),
    // The dashboard issues several requests at once, and polls every 30-60 s;
    // keep a few connections and outlive the poll interval so requests neither
    // queue behind one socket nor pay a fresh TLS handshake on every refresh.
    max: 4, connectionTimeoutMillis: 5000, idleTimeoutMillis: 120000,
    types: { getTypeParser: (type, format) => type === 20 && format !== 'binary' ? safeInteger : pg.types.getTypeParser(type, format) },
  });
  pool.on('error', (error) => console.error('AGAP PostgreSQL idle connection failed', error.code || error.name));
  return pool;
}

function safeInteger(value) {
  const number = Number(value);
  if (!Number.isSafeInteger(number)) throw new Error('Database integer exceeds the supported range');
  return number;
}
