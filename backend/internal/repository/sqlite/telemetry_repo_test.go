package sqlite_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
)

func setupTestDB(t *testing.T) repository.TelemetryRepository {
	t.Helper()
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_agap.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}

	return sqlite.NewTelemetryRepo(db)
}

func TestTelemetryRepo_SaveAndList(t *testing.T) {
	repo := setupTestDB(t)
	defer repo.Close()

	ctx := context.Background()
	targetID, _ := domain.ParseTargetID("05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")

	packet := &domain.TelemetryPacket{
		PacketID:       "pkt-uuid-001",
		PacketHash:     "hash-sha256-001",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeMedical,
		Channel:        domain.ChannelOnline,
		DeliveryPath:   domain.DeliveryDirectUplink,
		Latitude:       13.1391,
		Longitude:      123.7438,
		Altitude:       10.5,
		GPSAccuracy:    5.0,
		HopCount:       0,
		TTL:            10,
		SenderDeviceID: "device-victim-01",
		Timestamp:      time.Now().UTC(),
		ReceivedAt:     time.Now().UTC(),
	}

	inc, isDup, err := repo.SaveIncident(ctx, packet)
	if err != nil {
		t.Fatalf("failed to save incident: %v", err)
	}
	if isDup {
		t.Fatalf("expected new incident, got duplicate flag")
	}

	if inc.ID == 0 {
		t.Fatalf("expected valid auto-increment ID")
	}

	// Test listing
	incidents, _, err := repo.ListIncidents(ctx, repository.IncidentFilter{
		IncidentType: string(domain.IncidentTypeMedical),
	})

	if err != nil {
		t.Fatalf("failed to list incidents: %v", err)
	}
	if len(incidents) == 0 {
		t.Fatalf("expected incidents, got 0")
	}
	var found bool
	for _, inc := range incidents {
		if inc.PacketID == "pkt-uuid-001" {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected packet ID pkt-uuid-001 in incident list")
	}


	// Test duplicate insertion
	_, isDup2, err := repo.SaveIncident(ctx, packet)
	if !isDup2 {
		t.Fatalf("expected duplicate flag on second insert")
	}
}

func TestTelemetryRepo_StatusUpdate(t *testing.T) {
	repo := setupTestDB(t)
	defer repo.Close()

	ctx := context.Background()
	targetID, _ := domain.ParseTargetID("05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")

	packet := &domain.TelemetryPacket{
		PacketID:       "pkt-uuid-002",
		PacketHash:     "hash-sha256-002",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeCrime,
		Channel:        domain.ChannelBLEMesh,
		DeliveryPath:   domain.DeliveryCloudShortcut,
		Latitude:       13.1400,
		Longitude:      123.7400,
		SenderDeviceID: "relay-phone-02",
		Timestamp:      time.Now().UTC(),
	}

	inc, _, err := repo.SaveIncident(ctx, packet)
	if err != nil {
		t.Fatalf("failed to save incident: %v", err)
	}

	err = repo.UpdateIncidentStatus(ctx, inc.ID, domain.StatusDispatched)
	if err != nil {
		t.Fatalf("failed to update incident status: %v", err)
	}

	fetched, err := repo.GetIncidentByPacketID(ctx, "pkt-uuid-002")
	if err != nil {
		t.Fatalf("failed to fetch incident: %v", err)
	}
	if fetched.Status != domain.StatusDispatched {
		t.Errorf("expected status DISPATCHED, got %s", fetched.Status)
	}
}
