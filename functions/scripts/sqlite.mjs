import { readdirSync, readFileSync } from 'node:fs';

const migrationDirectory = new URL('../../backend/internal/repository/sqlite/migrations/', import.meta.url);
const migrations = readdirSync(migrationDirectory).filter((name) => name.endsWith('.sql')).sort();

export function prepareSchema(db, fresh) {
  if (fresh) {
    db.exec(`CREATE TABLE schema_migrations (
      filename TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
    )`);
    for (const name of migrations) {
      const sql = readFileSync(new URL(name, migrationDirectory), 'utf8');
      const rebuild = /PRAGMA\s+foreign_keys\s*=\s*OFF/i.test(sql);
      if (rebuild) db.exec('PRAGMA foreign_keys = OFF');
      db.exec('BEGIN');
      try {
        db.exec(sql);
        db.prepare('INSERT INTO schema_migrations (filename) VALUES (?)').run(name);
        db.exec('COMMIT');
      } catch (error) {
        db.exec('ROLLBACK');
        throw new Error(`Could not apply ${name}`, { cause: error });
      } finally {
        if (rebuild) db.exec('PRAGMA foreign_keys = ON');
      }
    }
  } else {
    const recorded = new Set(db.prepare('SELECT filename FROM schema_migrations').all().map((row) => row.filename));
    const missing = migrations.filter((name) => !recorded.has(name));
    if (missing.length) throw new Error(`The source needs AGAP migrations: ${missing.join(', ')}`);
  }
  db.exec(readFileSync(new URL('../shared/hosted-schema.sql', import.meta.url), 'utf8'));
}
