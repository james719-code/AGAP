package handler_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/handler"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type mockTelemetryService struct {
	IngestFn func(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error)
}

func (m *mockTelemetryService) IngestPacket(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error) {
	if m.IngestFn != nil {
		return m.IngestFn(ctx, packet)
	}
	return &domain.Incident{ID: 1, PacketUUID: packet.PacketUUID}, false, nil
}

func (m *mockTelemetryService) GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error) {
	return nil, nil
}
func (m *mockTelemetryService) GetIncidentByIncidentUUID(ctx context.Context, incidentUUID string) (*domain.Incident, error) {
	return nil, nil
}
func (m *mockTelemetryService) GetIncidentByPacketUUID(ctx context.Context, packetUUID string) (*domain.Incident, error) {
	return nil, nil
}
func (m *mockTelemetryService) ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, int, error) {
	return nil, 0, nil
}
func (m *mockTelemetryService) CanCitizenAccessIncident(ctx context.Context, incidentID, userID int64) (bool, error) {
	return true, nil
}
func (m *mockTelemetryService) UpdateStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus, changedBy *int64, notes string) error {
	return nil
}

func (m *mockTelemetryService) ListStatusHistory(ctx context.Context, incidentID int64) ([]*domain.IncidentStatusHistory, error) {
	return nil, nil
}

func (m *mockTelemetryService) ListStatusHistoryBulk(ctx context.Context, incidentIDs []int64) (map[int64][]*domain.IncidentStatusHistory, error) {
	return nil, nil
}

func (m *mockTelemetryService) UpdateIncidentLocation(ctx context.Context, incidentID int64, lat, lng float64) error {
	return nil
}

func (m *mockTelemetryService) RecordMeshDeliveryEvents(ctx context.Context, events []*domain.MeshDeliveryEvent) (int, error) {
	return len(events), nil
}

func TestTelemetryHandler_Uplink(t *testing.T) {
	mockSvc := &mockTelemetryService{}
	h := handler.NewTelemetryHandler(mockSvc)

	packet := map[string]interface{}{
		"incident_uuid":    "inc-test-01",
		"packet_uuid":      "pkt-test-01",
		"target_id":        map[string]string{"raw": "05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111"},
		"incident_type":    "MEDICAL",
		"channel":          "ONLINE",
		"delivery_path":    "DIRECT_UPLINK",
		"latitude":         13.1391,
		"longitude":        123.7438,
		"sender_device_id": "dev-01",
		"timestamp":        time.Now().Format(time.RFC3339),
	}

	body, _ := json.Marshal(packet)
	req := httptest.NewRequest("POST", "/api/v1/telemetry/uplink", bytes.NewBuffer(body))
	rec := httptest.NewRecorder()

	h.Uplink(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201 Created, got %d: %s", rec.Code, rec.Body.String())
	}

	var res handler.IngestResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}

	if res.Status != "accepted" {
		t.Errorf("expected status 'accepted', got %s", res.Status)
	}
}

func TestTelemetryHandler_RecordMeshEventsBatch(t *testing.T) {
	mockSvc := &mockTelemetryService{}
	h := handler.NewTelemetryHandler(mockSvc)

	payload := map[string]interface{}{
		"events": []map[string]interface{}{
			{
				"packet_uuid":       "pkt-mesh-001",
				"packet_type":       "SOS",
				"event_type":        "RELAYED",
				"device_identifier": "node-001",
				"latitude":          13.1391,
				"longitude":         123.7438,
				"recorded_at":       time.Now().UTC().Format(time.RFC3339),
			},
		},
	}

	body, _ := json.Marshal(payload)
	req := httptest.NewRequest("POST", "/api/v1/mesh/events/batch", bytes.NewBuffer(body))
	rec := httptest.NewRecorder()

	h.RecordMeshEventsBatch(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Fatalf("expected 202 Accepted, got %d: %s", rec.Code, rec.Body.String())
	}
}