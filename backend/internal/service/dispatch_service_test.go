package service_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
	"github.com/travelingsalesman/agap/backend/internal/service"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

func TestDispatchService_LifecycleDrivesIncidentStatus(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_dispatch_service.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	telemetryRepo := sqlite.NewTelemetryRepo(db)
	agencyRepo := sqlite.NewAgencyRepo(db)
	dispatchRepo := sqlite.NewDispatchRepo(db)
	dispatchSvc := service.NewDispatchService(dispatchRepo, telemetryRepo, agencyRepo, nil, nil, websocket.NewHub())

	agency, err := agencyRepo.CreateAgency(ctx, &domain.Agency{
		Code:          "MEDICAL-DISPATCH-TEST",
		Name:          "Medical Dispatch Test",
		Type:          domain.AgencyMedical,
		RegionCode:    "05",
		ProvinceCode:  "05",
		MuniCode:      "12",
		BarangayCode:  "01",
		ContactNumber: "09123456789",
		Latitude:      13.1391,
		Longitude:     123.7438,
	})
	if err != nil {
		t.Fatalf("failed to create agency: %v", err)
	}

	unit, err := agencyRepo.CreateResponder(ctx, &domain.ResponderUnit{
		AgencyID:     agency.ID,
		TargetID:     "05-05-12-01-04::unit-dispatch-test",
		UnitCallsign: "MEDICAL-TEST-1",
		DeviceID:     "dispatch-test-device",
		Status:       "IDLE",
		CurrentLat:   13.1391,
		CurrentLon:   123.7438,
	})
	if err != nil {
		t.Fatalf("failed to create unit: %v", err)
	}

	targetID, _ := domain.ParseTargetID("05-05-12-01-04::incident-dispatch-test")
	incident, _, err := telemetryRepo.SaveIncident(ctx, &domain.TelemetryPacket{
		PacketUUID:     "dispatch-pkt-001",
		PacketHash:     "dispatch-hash-001",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeMedical,
		Channel:        domain.ChannelOnline,
		DeliveryPath:   domain.DeliveryDirectUplink,
		Latitude:       13.1391,
		Longitude:      123.7438,
		SenderDeviceID: "citizen-device-dispatch",
		Timestamp:      time.Now().UTC(),
	})
	if err != nil {
		t.Fatalf("failed to create incident: %v", err)
	}

	dispatch, err := dispatchSvc.CreateDispatch(ctx, incident.ID, unit.ID, nil)
	if err != nil {
		t.Fatalf("failed to create dispatch: %v", err)
	}
	if dispatch.Status != domain.DispatchStatusAssigned {
		t.Fatalf("expected dispatch ASSIGNED, got %s", dispatch.Status)
	}

	incident, err = telemetryRepo.GetIncidentByID(ctx, incident.ID)
	if err != nil {
		t.Fatalf("failed to refetch incident: %v", err)
	}
	if incident.Status != domain.StatusDispatched {
		t.Fatalf("expected incident DISPATCHED after unit assignment, got %s", incident.Status)
	}

	for _, status := range []domain.DispatchStatus{
		domain.DispatchStatusAccepted,
		domain.DispatchStatusEnRoute,
		domain.DispatchStatusOnScene,
		domain.DispatchStatusCompleted,
	} {
		if err := dispatchSvc.UpdateDispatchStatus(ctx, dispatch.ID, status, nil); err != nil {
			t.Fatalf("expected dispatch transition to %s to pass, got %v", status, err)
		}
	}

	incident, err = telemetryRepo.GetIncidentByID(ctx, incident.ID)
	if err != nil {
		t.Fatalf("failed to refetch completed incident: %v", err)
	}
	if incident.Status != domain.StatusResolved {
		t.Fatalf("expected incident RESOLVED after completed dispatch, got %s", incident.Status)
	}

	if err := dispatchSvc.UpdateDispatchStatus(ctx, dispatch.ID, domain.DispatchStatusEnRoute, nil); err != domain.ErrInvalidDispatchTransition {
		t.Fatalf("expected completed dispatch regression to be rejected, got %v", err)
	}
}

func TestDispatchService_RejectsAgencyTypeMismatch(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_dispatch_agency_type.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	telemetryRepo := sqlite.NewTelemetryRepo(db)
	agencyRepo := sqlite.NewAgencyRepo(db)
	dispatchRepo := sqlite.NewDispatchRepo(db)
	dispatchSvc := service.NewDispatchService(dispatchRepo, telemetryRepo, agencyRepo, nil, nil, websocket.NewHub())

	fireAgency, err := agencyRepo.CreateAgency(ctx, &domain.Agency{
		Code:          "FIRE-DISPATCH-TEST",
		Name:          "Fire Dispatch Test",
		Type:          domain.AgencyFire,
		RegionCode:    "05",
		ProvinceCode:  "05",
		MuniCode:      "12",
		BarangayCode:  "01",
		ContactNumber: "09123456789",
		Latitude:      13.1391,
		Longitude:     123.7438,
	})
	if err != nil {
		t.Fatalf("failed to create agency: %v", err)
	}

	unit, err := agencyRepo.CreateResponder(ctx, &domain.ResponderUnit{
		AgencyID:     fireAgency.ID,
		TargetID:     "05-05-12-01-04::fire-unit-dispatch-test",
		UnitCallsign: "FIRE-TEST-1",
		DeviceID:     "fire-dispatch-test-device",
		Status:       "IDLE",
		CurrentLat:   13.1391,
		CurrentLon:   123.7438,
	})
	if err != nil {
		t.Fatalf("failed to create unit: %v", err)
	}

	targetID, _ := domain.ParseTargetID("05-05-12-01-04::incident-medical-test")
	incident, _, err := telemetryRepo.SaveIncident(ctx, &domain.TelemetryPacket{
		PacketUUID:     "dispatch-pkt-medical-001",
		PacketHash:     "dispatch-hash-medical-001",
		TargetID:       targetID,
		IncidentType:   domain.IncidentTypeMedical,
		Channel:        domain.ChannelOnline,
		DeliveryPath:   domain.DeliveryDirectUplink,
		Latitude:       13.1391,
		Longitude:      123.7438,
		SenderDeviceID: "citizen-device-medical-dispatch",
		Timestamp:      time.Now().UTC(),
	})
	if err != nil {
		t.Fatalf("failed to create incident: %v", err)
	}

	if _, err := dispatchSvc.CreateDispatch(ctx, incident.ID, unit.ID, nil); err != domain.ErrInvalidDispatchAgency {
		t.Fatalf("expected agency type mismatch to be rejected, got %v", err)
	}
}
