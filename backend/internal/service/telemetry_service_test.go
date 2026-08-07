package service_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/platform/cache"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

func TestTelemetryService_StatusTransitionValidation(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_telemetry_service.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	repo := sqlite.NewTelemetryRepo(db)
	svc := service.NewTelemetryService(repo, nil, nil, cache.NewTTLCache(time.Minute), websocket.NewHub())
	ctx := context.Background()
	targetID, _ := domain.ParseTargetID("05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")

	incident, _, err := repo.SaveIncident(ctx, &domain.TelemetryPacket{
		IncidentUUID:   "transition-inc-001",
		PacketUUID:     "transition-pkt-001",
		PacketHash:     "transition-hash-001",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeMedical,
		Channel:        domain.ChannelOnline,
		DeliveryPath:   domain.DeliveryDirectUplink,
		Latitude:       13.1391,
		Longitude:      123.7438,
		SenderDeviceID: "device-transition-01",
		Timestamp:      time.Now().UTC(),
	})
	if err != nil {
		t.Fatalf("failed to seed incident: %v", err)
	}

	if err := svc.UpdateStatus(ctx, incident.ID, domain.StatusDispatched, nil, "dispatched by test"); err != nil {
		t.Fatalf("expected RECEIVED -> DISPATCHED to pass, got %v", err)
	}
	if err := svc.UpdateStatus(ctx, incident.ID, domain.StatusResolved, nil, "resolved by test"); err != nil {
		t.Fatalf("expected DISPATCHED -> RESOLVED to pass, got %v", err)
	}
	if err := svc.UpdateStatus(ctx, incident.ID, domain.StatusEnRoute, nil, "invalid regression"); err != domain.ErrInvalidStatusTransition {
		t.Fatalf("expected RESOLVED -> EN_ROUTE to be rejected, got %v", err)
	}

	history, err := svc.ListStatusHistory(ctx, incident.ID)
	if err != nil {
		t.Fatalf("failed to list status history: %v", err)
	}
	if len(history) != 3 {
		t.Fatalf("expected 3 status history events, got %d", len(history))
	}
}

func TestTelemetryService_RecordsMeshDeliveryEvents(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_mesh_delivery.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	repo := sqlite.NewTelemetryRepo(db)
	svc := service.NewTelemetryService(repo, nil, nil, cache.NewTTLCache(time.Minute), websocket.NewHub())
	ctx := context.Background()
	targetID, _ := domain.ParseTargetID("05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")

	packet := &domain.TelemetryPacket{
		IncidentUUID:   "mesh-inc-001",
		PacketUUID:     "mesh-pkt-001",
		PacketHash:     "mesh-hash-001",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeMedical,
		Channel:        domain.ChannelBLEMesh,
		DeliveryPath:   domain.DeliveryCloudShortcut,
		Latitude:       13.1391,
		Longitude:      123.7438,
		HopCount:       2,
		SenderDeviceID: "relay-device-01",
		Timestamp:      time.Now().UTC(),
	}

	incident, duplicate, err := svc.IngestPacket(ctx, packet)
	if err != nil {
		t.Fatalf("failed to ingest packet: %v", err)
	}
	if duplicate {
		t.Fatalf("expected first packet to be accepted")
	}

	_, duplicate, err = svc.IngestPacket(ctx, packet)
	if err != nil {
		t.Fatalf("failed to ingest duplicate packet: %v", err)
	}
	if !duplicate {
		t.Fatalf("expected second packet to be duplicate")
	}

	var packetCount int
	if err := db.QueryRowContext(ctx, `SELECT COUNT(*) FROM mesh_packets WHERE packet_uuid = ? AND incident_id = ?`, packet.PacketUUID, incident.ID).Scan(&packetCount); err != nil {
		t.Fatalf("failed to count mesh packets: %v", err)
	}
	if packetCount != 1 {
		t.Fatalf("expected one logical mesh packet, got %d", packetCount)
	}
	if incident.IncidentUUID != packet.IncidentUUID || incident.PacketUUID != packet.PacketUUID {
		t.Fatalf("expected incident_uuid %q and packet_uuid %q, got incident_uuid=%q packet_uuid=%q", packet.IncidentUUID, packet.PacketUUID, incident.IncidentUUID, incident.PacketUUID)
	}

	var uplinkedCount, duplicateDroppedCount int
	if err := db.QueryRowContext(ctx, `
		SELECT
			SUM(CASE WHEN event_type = 'UPLINKED' THEN 1 ELSE 0 END),
			SUM(CASE WHEN event_type = 'DUPLICATE_DROPPED' THEN 1 ELSE 0 END)
		FROM mesh_delivery_events
		WHERE packet_id = (SELECT id FROM mesh_packets WHERE packet_uuid = ?)
	`, packet.PacketUUID).Scan(&uplinkedCount, &duplicateDroppedCount); err != nil {
		t.Fatalf("failed to count mesh delivery events: %v", err)
	}
	if uplinkedCount != 1 || duplicateDroppedCount != 1 {
		t.Fatalf("expected one uplinked and one duplicate event, got uplinked=%d duplicate=%d", uplinkedCount, duplicateDroppedCount)
	}
}
