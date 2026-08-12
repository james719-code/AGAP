package sqlite_test

import (
	"context"
	"errors"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
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

func TestGetResponderByUserID_RemovedMembershipDoesNotFallBackToLegacyOwner(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_responder_membership_lookup.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	now := "2026-01-01T00:00:00Z"
	userRes, err := db.ExecContext(ctx, `
		INSERT INTO users (username, password_hash, role, account_status, is_verified, created_at, updated_at)
		VALUES ('agency-membership-responder', 'hash', 'RESPONDER', 'APPROVED', 1, ?, ?)
	`, now, now)
	if err != nil {
		t.Fatalf("failed to create responder user: %v", err)
	}
	userID, _ := userRes.LastInsertId()

	var agencyID int64
	if err := db.QueryRowContext(ctx, `SELECT id FROM agencies ORDER BY id LIMIT 1`).Scan(&agencyID); err != nil {
		t.Fatalf("expected a seeded agency: %v", err)
	}

	unitRes, err := db.ExecContext(ctx, `
		INSERT INTO rescue_units (
			agency_id, user_id, target_id, unit_code, unit_type, responder_name,
			responder_phone, device_id, status, current_latitude, current_longitude,
			last_location_at, created_at, updated_at
		) VALUES (?, ?, '05-05-12-01-04', 'AGENCY-MEMBERSHIP-UNIT', 'RESCUE_VEHICLE',
			'Membership Responder', '', 'device-agency-membership', 'IDLE', 13.13, 123.74, ?, ?, ?)
	`, agencyID, userID, now, now, now)
	if err != nil {
		t.Fatalf("failed to create rescue unit: %v", err)
	}
	unitID, _ := unitRes.LastInsertId()

	repo := sqlite.NewAgencyRepo(db)
	unit, err := repo.GetResponderByUserID(ctx, userID)
	if err != nil {
		t.Fatalf("legacy owner should resolve while unit has no membership history: %v", err)
	}
	if unit.ID != unitID {
		t.Fatalf("expected unit %d, got %d", unitID, unit.ID)
	}

	memberRes, err := db.ExecContext(ctx, `
		INSERT INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at)
		VALUES (?, ?, 1, ?)
	`, unitID, userID, now)
	if err != nil {
		t.Fatalf("failed to create membership: %v", err)
	}
	memberID, _ := memberRes.LastInsertId()

	if _, err := repo.GetResponderByUserID(ctx, userID); err != nil {
		t.Fatalf("active membership should resolve responder unit: %v", err)
	}

	if _, err := db.ExecContext(ctx, `UPDATE rescue_unit_members SET removed_at = ? WHERE id = ?`, "2026-01-01T00:01:00Z", memberID); err != nil {
		t.Fatalf("failed to remove membership: %v", err)
	}

	_, err = repo.GetResponderByUserID(ctx, userID)
	if !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("removed member must not regain unit through legacy user_id fallback; got %v", err)
	}
}
