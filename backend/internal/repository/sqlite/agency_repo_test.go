package sqlite_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func TestAgencyRepo_CRUD(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_agency.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	repo := sqlite.NewAgencyRepo(db)
	ctx := context.Background()

	// 1. Create Agency
	agency := &domain.Agency{
		Code:          "DRRMO-LEGAZPI",
		Name:          "Legazpi City Disaster Risk Reduction Management Office",
		Type:          domain.AgencyDRRMO,
		RegionCode:    "05",
		ProvinceCode:  "05",
		MuniCode:      "12",
		BarangayCode:  "01",
		ContactNumber: "09123456789",
		Latitude:      13.1391,
		Longitude:     123.7438,
	}

	created, err := repo.CreateAgency(ctx, agency)
	if err != nil {
		t.Fatalf("failed to create agency: %v", err)
	}
	if created.ID == 0 {
		t.Fatalf("expected non-zero ID for created agency")
	}

	// 2. Fetch Agency
	fetched, err := repo.GetAgencyByID(ctx, created.ID)
	if err != nil {
		t.Fatalf("failed to fetch agency: %v", err)
	}
	if fetched.Code != "DRRMO-LEGAZPI" {
		t.Errorf("expected code DRRMO-LEGAZPI, got %s", fetched.Code)
	}

	// 3. Create Responder Unit
	responder := &domain.ResponderUnit{
		AgencyID:     created.ID,
		TargetID:     "05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111",
		UnitCallsign: "DRRMO-ALPHA-1",
		DeviceID:     "responder-dev-01",
		Status:       "IDLE",
		CurrentLat:   13.1395,
		CurrentLon:   123.7440,
	}

	resCreated, err := repo.CreateResponder(ctx, responder)
	if err != nil {
		t.Fatalf("failed to create responder unit: %v", err)
	}

	// 4. Update Responder Location
	err = repo.UpdateResponderLocation(ctx, resCreated.ID, 13.1410, 123.7460, "DISPATCHED")
	if err != nil {
		t.Fatalf("failed to update responder location: %v", err)
	}

	resFetched, err := repo.GetResponderByID(ctx, resCreated.ID)
	if err != nil {
		t.Fatalf("failed to fetch responder unit: %v", err)
	}
	if resFetched.Status != "DISPATCHED" {
		t.Errorf("expected status DISPATCHED, got %s", resFetched.Status)
	}
}
