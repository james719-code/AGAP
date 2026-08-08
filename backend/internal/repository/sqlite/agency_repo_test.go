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
