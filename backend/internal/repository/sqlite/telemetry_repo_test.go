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
		IncidentUUID:   "inc-uuid-001",
		PacketUUID:     "pkt-uuid-001",
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
	if inc.IncidentUUID != packet.IncidentUUID {
		t.Fatalf("expected incident_uuid %q, got %q", packet.IncidentUUID, inc.IncidentUUID)
	}
	if inc.PacketUUID != packet.PacketUUID {
		t.Fatalf("expected packet_uuid %q, got %q", packet.PacketUUID, inc.PacketUUID)
	}
	if inc.Status != domain.StatusReceived {
		t.Fatalf("expected initial status RECEIVED, got %s", inc.Status)
	}
	if inc.EmergencyTypeID == nil || *inc.EmergencyTypeID != int64(domain.IncidentTypeMedical.DefaultEmergencyTypeID()) {
		t.Fatalf("expected MEDICAL emergency_type_id, got %v", inc.EmergencyTypeID)
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
		if inc.PacketUUID == "pkt-uuid-001" {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected packet UUID pkt-uuid-001 in incident list")
	}

	fetchedByUUID, err := repo.GetIncidentByIncidentUUID(ctx, packet.IncidentUUID)
	if err != nil {
		t.Fatalf("failed to fetch incident by incident_uuid: %v", err)
	}
	if fetchedByUUID.ID != inc.ID {
		t.Fatalf("expected incident_uuid lookup to return incident %d, got %d", inc.ID, fetchedByUUID.ID)
	}

	// Test duplicate insertion
	_, isDup2, err := repo.SaveIncident(ctx, packet)
	if !isDup2 {
		t.Fatalf("expected duplicate flag on second insert")
	}
}

func TestTelemetryRepo_AccidentTypesRetiredFromSchema(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_agap_schema.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	for _, table := range []string{"incidents", "predefined_messages"} {
		rows, err := db.QueryContext(ctx, "PRAGMA table_info("+table+")")
		if err != nil {
			t.Fatalf("failed to inspect %s: %v", table, err)
		}
		for rows.Next() {
			var cid int
			var name, columnType string
			var notNull int
			var defaultValue interface{}
			var pk int
			if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
				rows.Close()
				t.Fatalf("failed to scan %s column info: %v", table, err)
			}
			if name == "accident_type_id" {
				rows.Close()
				t.Fatalf("expected %s.accident_type_id to be retired", table)
			}
		}
		if err := rows.Close(); err != nil {
			t.Fatalf("failed to close %s schema rows: %v", table, err)
		}
	}

	for _, legacyTable := range []string{"accident_types", "hop_events"} {
		var legacyCount int
		if err := db.QueryRowContext(ctx, `SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?`, legacyTable).Scan(&legacyCount); err != nil {
			t.Fatalf("failed to inspect sqlite_master for %s: %v", legacyTable, err)
		}
		if legacyCount != 0 {
			t.Fatalf("expected %s table to be retired, found %d", legacyTable, legacyCount)
		}
	}
}

func TestTelemetryRepo_IncidentPacketIDRetiredFromSchema(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_agap_packet_identity_schema.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}
	defer db.Close()

	rows, err := db.QueryContext(context.Background(), "PRAGMA table_info(incidents)")
	if err != nil {
		t.Fatalf("failed to inspect incidents schema: %v", err)
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name, columnType string
		var notNull int
		var defaultValue interface{}
		var pk int
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
			t.Fatalf("failed to scan incidents column info: %v", err)
		}
		if name == "packet_id" {
			t.Fatalf("expected incidents.packet_id to be retired")
		}
	}
}

func TestTelemetryRepo_StatusUpdate(t *testing.T) {
	repo := setupTestDB(t)
	defer repo.Close()

	ctx := context.Background()
	targetID, _ := domain.ParseTargetID("05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")

	packet := &domain.TelemetryPacket{
		IncidentUUID:   "inc-uuid-002",
		PacketUUID:     "pkt-uuid-002",
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
	if err := repo.AppendIncidentStatusHistory(ctx, &domain.IncidentStatusHistory{IncidentID: inc.ID, Status: domain.StatusDispatched}); err != nil {
		t.Fatalf("failed to append status history: %v", err)
	}

	if err := repo.RecordMeshDeliveryEvent(ctx, &domain.MeshDeliveryEvent{
		PacketUUID: packet.PacketUUID,
		PacketType: domain.MeshPacketTypeSOS,
		IncidentID: &inc.ID,
		EventType:  domain.MeshEventUplinked,
		RecordedAt: time.Now().UTC(),
	}); err != nil {
		t.Fatalf("failed to seed mesh packet: %v", err)
	}

	fetched, err := repo.GetIncidentByPacketUUID(ctx, "pkt-uuid-002")
	if err != nil {
		t.Fatalf("failed to fetch incident: %v", err)
	}
	if fetched.Status != domain.StatusDispatched {
		t.Errorf("expected status DISPATCHED, got %s", fetched.Status)
	}
	if fetched.IncidentUUID != packet.IncidentUUID {
		t.Fatalf("expected fetched incident_uuid %q, got %q", packet.IncidentUUID, fetched.IncidentUUID)
	}
	if fetched.EmergencyTypeID == nil || *fetched.EmergencyTypeID != int64(domain.IncidentTypeCrime.DefaultEmergencyTypeID()) {
		t.Fatalf("expected CRIME emergency_type_id, got %v", fetched.EmergencyTypeID)
	}

	history, err := repo.ListIncidentStatusHistory(ctx, inc.ID)
	if err != nil {
		t.Fatalf("failed to list status history: %v", err)
	}
	if len(history) < 2 {
		t.Fatalf("expected at least initial and dispatched history events, got %d", len(history))
	}
}
