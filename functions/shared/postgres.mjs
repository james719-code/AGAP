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

export function postgresFactory(connect, connectionString) {
  let client;
  return () => {
    if (!connectionString) throw new HttpError(503, 'Hosted database configuration is missing');
    client ??= connect(connectionString);

    async function run(statements, mode = 'read') {
      for (let retry = 0; ; retry++) {
        const tx = await client.connect();
        let broken;
        try {
          await tx.query(mode === 'write' ? 'BEGIN ISOLATION LEVEL SERIALIZABLE' : 'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
          await tx.query('SET LOCAL search_path = agap, public');
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
      execute: async (statement) => (await run([statement], /^\s*SELECT\b/i.test(statement.sql) ? 'read' : 'write'))[0],
      batch: run,
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
    connectionString, ...(ssl ? { ssl } : {}), max: 1, connectionTimeoutMillis: 5000, idleTimeoutMillis: 10000,
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
