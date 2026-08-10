package sqlite_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func TestRouteIncidentAgencies(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_agency_routing.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	agencyRepo := sqlite.NewAgencyRepo(db)
	ctx := context.Background()

	_, err = db.ExecContext(ctx, `INSERT INTO incidents (
		id, incident_uuid, packet_hash, target_id_raw, region_code, province_code,
		municipality_code, barangay_code, zone_code, incident_type, channel, delivery_path,
		latitude, longitude, sender_device_id, event_timestamp, received_at
	) VALUES 
	(1, 'uuid-1', 'hash-1', 'target-1', 'REG', 'PROV', 'MUNI', 'PH-ALB-LEG-001', 'ZONE', 'FIRE', 'ONLINE', 'PATH', 0.0, 0.0, 'device-1', 'now', 'now'),
	(2, 'uuid-2', 'hash-2', 'target-2', 'REG', 'PROV', 'MUNI', 'PH-ALB-LEG-002', 'ZONE', 'MEDICAL', 'ONLINE', 'PATH', 0.0, 0.0, 'device-2', 'now', 'now')`)
	if err != nil {
		t.Fatalf("failed to insert test incidents: %v", err)
	}

	// Route a FIRE emergency in PH-ALB-LEG-001
	err = agencyRepo.RouteIncidentAgencies(ctx, 1, "FIRE", "PH-ALB-LEG-001")
	if err != nil {
		t.Fatalf("unexpected error routing incident: %v", err)
	}

	// Route a MEDICAL emergency in PH-ALB-LEG-002
	err = agencyRepo.RouteIncidentAgencies(ctx, 2, "MEDICAL", "PH-ALB-LEG-002")
	if err != nil {
		t.Fatalf("unexpected error routing medical incident: %v", err)
	}
}
