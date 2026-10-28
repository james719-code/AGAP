package sqlite

import (
	"context"
	"database/sql"
	"path/filepath"
	"strings"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

func historyPerformanceDB(tb testing.TB, incidents int) *sql.DB {
	tb.Helper()
	db, err := Open(filepath.Join(tb.TempDir(), "history.db"))
	if err != nil {
		tb.Fatal(err)
	}
	tb.Cleanup(func() { db.Close() })
	_, err = db.Exec(`
		WITH RECURSIVE ids(id) AS (
			SELECT 1 UNION ALL SELECT id + 1 FROM ids WHERE id < ?
		)
		INSERT INTO incidents (
			id, incident_uuid, packet_hash, target_id_raw, region_code, province_code,
			municipality_code, barangay_code, zone_code, incident_type, channel,
			delivery_path, latitude, longitude, sender_device_id, event_timestamp, received_at
		)
		SELECT id, 'history-' || id, 'history-hash-' || id, '05-05-12-01-01::HISTORY', '05', '05',
			'12', '01', '01', 'FIRE', 'ONLINE', 'DIRECT_UPLINK', 13.1, 123.7,
			'HISTORY-DEVICE', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'
		FROM ids;
	`, incidents)
	if err != nil {
		tb.Fatal(err)
	}
	// Insert reverse chronological groups and equal timestamps so reads must
	// preserve both timestamp ordering and the ID tie breaker.
	_, err = db.Exec(`
		WITH RECURSIVE events(n) AS (
			SELECT 10 UNION ALL SELECT n - 1 FROM events WHERE n > 1
		)
		INSERT INTO incident_status_history (incident_id, status, notes, created_at)
		SELECT i.id, 'ACKNOWLEDGED', 'History fixture',
			'2026-01-01T00:00:0' || CAST((events.n - 1) / 2 AS INTEGER) || 'Z'
		FROM incidents i CROSS JOIN events
	`)
	if err != nil {
		tb.Fatal(err)
	}
	return db
}

func TestIncidentHistoryLookupUsesOrderedIndex(t *testing.T) {
	db := historyPerformanceDB(t, 3)
	ctx := context.Background()
	for _, query := range []string{
		`SELECT id, incident_id, status, changed_by, latitude, longitude, COALESCE(notes, ''), created_at
		 FROM incident_status_history WHERE incident_id = 1 ORDER BY created_at, id`,
		`SELECT id, incident_id, status, changed_by, latitude, longitude, COALESCE(notes, ''), created_at
		 FROM incident_status_history WHERE incident_id IN (1, 2) ORDER BY incident_id, created_at, id`,
	} {
		rows, err := db.QueryContext(ctx, "EXPLAIN QUERY PLAN "+query)
		if err != nil {
			t.Fatal(err)
		}
		var indexed bool
		for rows.Next() {
			var id, parent, unused int
			var detail string
			if err := rows.Scan(&id, &parent, &unused, &detail); err != nil {
				t.Fatal(err)
			}
			if strings.Contains(detail, "idx_incident_status_history_incident_created") {
				indexed = true
			}
			if strings.Contains(detail, "USE TEMP B-TREE") {
				t.Errorf("history lookup requires an unnecessary sort: %s", detail)
			}
		}
		if err := rows.Err(); err != nil {
			t.Fatal(err)
		}
		rows.Close()
		if !indexed {
			t.Error("history lookup did not use the incident/time index")
		}
	}

	repo := NewTelemetryRepo(db)
	bulk, err := repo.ListIncidentStatusHistoryBulk(ctx, []int64{2, 1, 999})
	if err != nil {
		t.Fatal(err)
	}
	for _, incidentID := range []int64{1, 2} {
		history, err := repo.ListIncidentStatusHistory(ctx, incidentID)
		if err != nil {
			t.Fatal(err)
		}
		if len(history) != 10 || len(bulk[incidentID]) != 10 {
			t.Fatalf("expected ten events for incident %d", incidentID)
		}
		for i, event := range history {
			if event.ID != bulk[incidentID][i].ID || event.Status != domain.StatusAcknowledged {
				t.Fatalf("single and bulk histories differ for incident %d", incidentID)
			}
			if i > 0 {
				previous := history[i-1]
				if event.CreatedAt.Before(previous.CreatedAt) ||
					(event.CreatedAt.Equal(previous.CreatedAt) && event.ID < previous.ID) {
					t.Fatalf("history is out of order for incident %d", incidentID)
				}
			}
		}
	}
	if _, ok := bulk[999]; ok {
		t.Error("unexpected history for a missing incident")
	}
}

func BenchmarkIncidentHistoryLookup(b *testing.B) {
	for _, indexed := range []bool{false, true} {
		name := "without_index"
		if indexed {
			name = "with_index"
		}
		b.Run(name, func(b *testing.B) {
			db := historyPerformanceDB(b, 10000)
			if !indexed {
				if _, err := db.Exec("DROP INDEX IF EXISTS idx_incident_status_history_incident_created"); err != nil {
					b.Fatal(err)
				}
			}
			repo := NewTelemetryRepo(db)
			ctx := context.Background()
			b.Run("single", func(b *testing.B) {
				b.ReportAllocs()
				for i := 0; i < b.N; i++ {
					history, err := repo.ListIncidentStatusHistory(ctx, 5000)
					if err != nil || len(history) != 10 {
						b.Fatalf("history lookup: count=%d err=%v", len(history), err)
					}
				}
			})
			b.Run("bulk", func(b *testing.B) {
				ids := []int64{10, 500, 1000, 5000, 9999}
				b.ReportAllocs()
				b.ResetTimer()
				for i := 0; i < b.N; i++ {
					history, err := repo.ListIncidentStatusHistoryBulk(ctx, ids)
					if err != nil || len(history) != len(ids) {
						b.Fatalf("bulk history lookup: count=%d err=%v", len(history), err)
					}
				}
			})
		})
	}
}
