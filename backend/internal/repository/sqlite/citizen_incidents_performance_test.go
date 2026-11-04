package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/repository"
)

func citizenIncidentsDB(tb testing.TB, incidents int) (*sql.DB, int64) {
	tb.Helper()
	db, err := Open(filepath.Join(tb.TempDir(), "citizen-incidents.db"))
	if err != nil {
		tb.Fatal(err)
	}
	tb.Cleanup(func() { db.Close() })
	result, err := db.Exec(`INSERT INTO users (username, password_hash, role) VALUES ('history-owner', 'unused', 'CITIZEN')`)
	if err != nil {
		tb.Fatal(err)
	}
	userID, err := result.LastInsertId()
	if err != nil {
		tb.Fatal(err)
	}
	_, err = db.Exec(`
		WITH RECURSIVE ids(id) AS (
			SELECT 1 UNION ALL SELECT id + 1 FROM ids WHERE id < ?
		)
		INSERT INTO incidents (
			id, incident_uuid, packet_hash, citizen_id, target_id_raw, region_code, province_code,
			municipality_code, barangay_code, zone_code, incident_type, channel,
			delivery_path, latitude, longitude, sender_device_id, event_timestamp, received_at
		)
		SELECT id, 'citizen-' || id, 'citizen-hash-' || id,
			CASE WHEN id % 100 = 0 THEN ? ELSE NULL END,
			'05-05-12-01-01::HISTORY', '05', '05', '12', '01', '01', 'FIRE', 'ONLINE',
			'DIRECT_UPLINK', 13.1, 123.7, 'HISTORY-DEVICE', '2026-01-01T00:00:00Z',
			strftime('%Y-%m-%dT%H:%M:%SZ', '2026-01-01', '+' || id || ' seconds')
		FROM ids;
	`, incidents, userID)
	if err != nil {
		tb.Fatal(err)
	}
	_, err = db.Exec(`
		INSERT INTO incident_reports (
			report_uuid, transport_token, incident_id, reporter_user_id, submission_type,
			subject_relation, emergency_type, people_affected, immediate_danger,
			location_source, latitude, longitude, observed_at, origin_channel, detail_state
		)
		SELECT 'report-' || id, printf('%012x', id), id,
			CASE WHEN id % 100 IN (0, 1) THEN ? ELSE NULL END,
			'INCIDENT_REPORT', 'SELF', 'FIRE', 1, 0, 'GPS', 13.1, 123.7,
			'2026-01-01T00:00:00Z', 'ONLINE', 'FULL'
		FROM incidents;
	`, userID)
	if err != nil {
		tb.Fatal(err)
	}
	return db, userID
}

func TestCitizenIncidentListingPreservesOwnershipAndPagination(t *testing.T) {
	db, userID := citizenIncidentsDB(t, 201)
	repo := NewTelemetryRepo(db)
	ctx := context.Background()
	// Keep a direct-only incident as well as report-only and shared ownership.
	if _, err := db.Exec(`UPDATE incident_reports SET reporter_user_id=NULL WHERE incident_id=100`); err != nil {
		t.Fatal(err)
	}
	incidents, total, err := repo.ListIncidents(ctx, repository.IncidentFilter{CitizenID: &userID, Limit: 2, Offset: 1})
	if err != nil {
		t.Fatal(err)
	}
	// Ownership includes IDs 1, 100, 101, 200, and 201. ID 200 belongs both
	// directly and through a report and must not appear twice.
	if total != 5 || len(incidents) != 2 || incidents[0].ID != 200 || incidents[1].ID != 101 {
		t.Fatalf("unexpected paginated ownership result: total=%d incidents=%+v", total, incidents)
	}
	if _, err := db.Exec(`UPDATE incidents SET status='RESOLVED' WHERE id=200`); err != nil {
		t.Fatal(err)
	}
	incidents, total, err = repo.ListIncidents(ctx, repository.IncidentFilter{CitizenID: &userID, Status: "RESOLVED"})
	if err != nil || total != 1 || len(incidents) != 1 || incidents[0].ID != 200 {
		t.Fatalf("ownership combined with status filter: total=%d incidents=%+v err=%v", total, incidents, err)
	}
	missingUserID := userID + 1000
	incidents, total, err = repo.ListIncidents(ctx, repository.IncidentFilter{CitizenID: &missingUserID})
	if err != nil || total != 0 || len(incidents) != 0 {
		t.Fatalf("unrelated user received incidents: total=%d incidents=%+v err=%v", total, incidents, err)
	}
}

func BenchmarkCitizenIncidentListing(b *testing.B) {
	for _, count := range []int{1000, 10000} {
		b.Run(fmt.Sprintf("incidents_%d", count), func(b *testing.B) {
			db, userID := citizenIncidentsDB(b, count)
			repo := NewTelemetryRepo(db)
			filter := repository.IncidentFilter{CitizenID: &userID, Limit: 20}
			ctx := context.Background()
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				incidents, total, err := repo.ListIncidents(ctx, filter)
				if err != nil || total != count/50 || len(incidents) != 20 {
					b.Fatalf("citizen listing: total=%d count=%d err=%v", total, len(incidents), err)
				}
			}
		})
	}
}
