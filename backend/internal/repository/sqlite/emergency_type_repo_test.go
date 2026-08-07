package sqlite_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func TestEmergencyTypeRepo_CRUDAndFiltering(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_emergency_types.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	repo := sqlite.NewEmergencyTypeRepo(db)

	seeded, err := repo.ListEmergencyTypes(ctx, false)
	if err != nil {
		t.Fatalf("failed to list seeded emergency types: %v", err)
	}
	if len(seeded) < 5 {
		t.Fatalf("expected seeded emergency types, got %d", len(seeded))
	}

	created, err := repo.CreateEmergencyType(ctx, &domain.EmergencyType{
		Code:             "LANDSLIDE",
		Name:             "Landslide",
		Description:      "Landslide or slope failure response",
		DefaultPriority:  1,
		TargetAgencyType: "DRRMO",
		IsActive:         true,
	})
	if err != nil {
		t.Fatalf("failed to create emergency type: %v", err)
	}
	if created.ID == 0 || created.Code != "LANDSLIDE" {
		t.Fatalf("unexpected created emergency type: %+v", created)
	}

	created.IsActive = false
	created.DefaultPriority = 2
	updated, err := repo.UpdateEmergencyType(ctx, created)
	if err != nil {
		t.Fatalf("failed to update emergency type: %v", err)
	}
	if updated.IsActive || updated.DefaultPriority != 2 {
		t.Fatalf("unexpected updated emergency type: %+v", updated)
	}

	activeOnly, err := repo.ListEmergencyTypes(ctx, false)
	if err != nil {
		t.Fatalf("failed to list active emergency types: %v", err)
	}
	for _, item := range activeOnly {
		if item.ID == updated.ID {
			t.Fatalf("inactive emergency type should not be returned by default")
		}
	}

	allTypes, err := repo.ListEmergencyTypes(ctx, true)
	if err != nil {
		t.Fatalf("failed to list all emergency types: %v", err)
	}
	foundInactive := false
	for _, item := range allTypes {
		if item.ID == updated.ID {
			foundInactive = true
			break
		}
	}
	if !foundInactive {
		t.Fatalf("expected includeInactive list to contain updated inactive emergency type")
	}
}
