package sqlite

import (
	"context"
	"database/sql"
	"embed"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	_ "modernc.org/sqlite"
)

//go:embed migrations/*.sql
var migrationFS embed.FS

func Open(dbPath string) (*sql.DB, error) {
	dir := filepath.Dir(dbPath)
	if dbPath != ":memory:" && dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, fmt.Errorf("failed to create database directory: %w", err)
		}
	}

	dsn := sqliteDSN(dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite database: %w", err)
	}

	// :memory: databases are private per SQLite connection. Keep a single pooled
	// connection so tests and local ephemeral runs always see one consistent DB.
	if dbPath == ":memory:" {
		db.SetMaxOpenConns(1)
		db.SetMaxIdleConns(1)
	} else {
		// SQLite permits multiple readers but only one writer. A modest pool avoids
		// unbounded connection growth while WAL + busy_timeout absorb short bursts.
		db.SetMaxOpenConns(8)
		db.SetMaxIdleConns(4)
	}

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to ping sqlite database: %w", err)
	}

	if err := runMigrations(db); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to run migrations: %w", err)
	}

	return db, nil
}

func sqliteDSN(dbPath string) string {
	var dsn string
	if dbPath == ":memory:" {
		dsn = "file:agap-memory?mode=memory&cache=shared"
	} else if strings.HasPrefix(dbPath, "file:") {
		dsn = dbPath
	} else {
		dsn = "file:" + filepath.ToSlash(dbPath)
	}

	separator := "?"
	if strings.Contains(dsn, "?") {
		separator = "&"
	}

	// modernc.org/sqlite applies every _pragma value when each pooled connection
	// opens, keeping foreign keys, busy timeout, and memory caches consistent.
	return dsn + separator +
		"_pragma=foreign_keys(1)" +
		"&_pragma=busy_timeout(5000)" +
		"&_pragma=journal_mode(WAL)" +
		"&_pragma=synchronous(NORMAL)" +
		"&_pragma=temp_store(MEMORY)" +
		"&_pragma=mmap_size(268435456)" +
		"&_pragma=cache_size(-64000)" +
		"&_pragma=wal_autocheckpoint(1000)"
}

func runMigrations(db *sql.DB) error {
	ctx := context.Background()
	conn, err := db.Conn(ctx)
	if err != nil {
		return fmt.Errorf("failed to reserve migration connection: %w", err)
	}
	defer conn.Close()

	if _, err := conn.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (
		filename TEXT PRIMARY KEY,
		applied_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
	);`); err != nil {
		return fmt.Errorf("failed to create schema_migrations table: %w", err)
	}

	entries, err := migrationFS.ReadDir("migrations")
	if err != nil {
		return err
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}

		var count int
		if err := conn.QueryRowContext(ctx, "SELECT COUNT(*) FROM schema_migrations WHERE filename = ?", entry.Name()).Scan(&count); err != nil {
			return fmt.Errorf("failed to inspect migration state for %s: %w", entry.Name(), err)
		}
		if count > 0 {
			continue
		}

		content, err := migrationFS.ReadFile("migrations/" + entry.Name())
		if err != nil {
			return fmt.Errorf("failed to read migration file %s: %w", entry.Name(), err)
		}

		migrationSQL := string(content)
		needsForeignKeysDisabled := strings.Contains(strings.ToUpper(strings.ReplaceAll(migrationSQL, " ", "")), "PRAGMAFOREIGN_KEYS=OFF")
		if needsForeignKeysDisabled {
			if _, err := conn.ExecContext(ctx, "PRAGMA foreign_keys=OFF;"); err != nil {
				return fmt.Errorf("failed to disable foreign keys for migration %s: %w", entry.Name(), err)
			}
		}

		tx, err := conn.BeginTx(ctx, nil)
		if err != nil {
			restoreForeignKeys(conn, ctx, needsForeignKeysDisabled)
			return fmt.Errorf("failed to begin migration %s: %w", entry.Name(), err)
		}

		if _, err := tx.ExecContext(ctx, migrationSQL); err != nil {
			_ = tx.Rollback()
			restoreForeignKeys(conn, ctx, needsForeignKeysDisabled)
			return fmt.Errorf("failed to execute migration %s: %w", entry.Name(), err)
		}

		if _, err := tx.ExecContext(ctx, "INSERT INTO schema_migrations (filename) VALUES (?)", entry.Name()); err != nil {
			_ = tx.Rollback()
			restoreForeignKeys(conn, ctx, needsForeignKeysDisabled)
			return fmt.Errorf("failed to record migration %s: %w", entry.Name(), err)
		}

		if err := tx.Commit(); err != nil {
			restoreForeignKeys(conn, ctx, needsForeignKeysDisabled)
			return fmt.Errorf("failed to commit migration %s: %w", entry.Name(), err)
		}
		restoreForeignKeys(conn, ctx, needsForeignKeysDisabled)
	}

	return nil
}

func restoreForeignKeys(conn *sql.Conn, ctx context.Context, restore bool) {
	if restore {
		_, _ = conn.ExecContext(ctx, "PRAGMA foreign_keys=ON;")
	}
}
