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
	var storedUnitCode string
	var storedLat float64
	if err := db.QueryRowContext(ctx, `SELECT unit_code, current_latitude FROM rescue_units WHERE id = ?`, resCreated.ID).Scan(&storedUnitCode, &storedLat); err != nil {
		t.Fatalf("expected responder unit to be stored in rescue_units: %v", err)
	}
	if storedUnitCode != responder.UnitCallsign {
		t.Fatalf("expected rescue_units.unit_code %q, got %q", responder.UnitCallsign, storedUnitCode)
	}
	if storedLat != responder.CurrentLat {
		t.Fatalf("expected rescue_units.current_latitude %f, got %f", responder.CurrentLat, storedLat)
	}

	userResult, err := db.ExecContext(ctx, `
		INSERT INTO users (username, password_hash, role, device_id, name, phone_number, account_status, is_verified, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', 1, strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now'))
	`, "rescue-member-1", "hash", string(domain.RoleResponder), "member-device-1", "Rescue Member", "+639171111111")
	if err != nil {
		t.Fatalf("failed to create responder user for membership: %v", err)
	}
	memberUserID, _ := userResult.LastInsertId()

	member, err := repo.AddRescueUnitMember(ctx, &domain.RescueUnitMember{
		RescueUnitID: resCreated.ID,
		ResponderID:  memberUserID,
		IsLeader:     true,
	})
	if err != nil {
		t.Fatalf("failed to add rescue unit member: %v", err)
	}
	if member.ID == 0 || !member.IsLeader {
		t.Fatalf("unexpected rescue unit member: %+v", member)
	}

	members, err := repo.ListRescueUnitMembers(ctx, resCreated.ID)
	if err != nil {
		t.Fatalf("failed to list rescue unit members: %v", err)
	}
	if len(members) != 1 || members[0].ResponderID != memberUserID {
		t.Fatalf("expected one rescue unit member for user %d, got %+v", memberUserID, members)
	}

	if err := repo.RemoveRescueUnitMember(ctx, resCreated.ID, memberUserID); err != nil {
		t.Fatalf("failed to remove rescue unit member: %v", err)
	}
	members, err = repo.ListRescueUnitMembers(ctx, resCreated.ID)
	if err != nil {
		t.Fatalf("failed to list rescue unit members after removal: %v", err)
	}
	if members[0].RemovedAt == nil {
		t.Fatalf("expected removed_at to be set after soft removal")
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
