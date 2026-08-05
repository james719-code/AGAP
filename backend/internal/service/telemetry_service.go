package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/platform/cache"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type TelemetryService interface {
	IngestPacket(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error)
	GetIncidentByPacketID(ctx context.Context, packetID string) (*domain.Incident, error)
	ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, error)
	UpdateStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error
}

type telemetryService struct {
	repo       repository.TelemetryRepository
	dedupCache *cache.TTLCache
	hub        *websocket.Hub
}

func NewTelemetryService(repo repository.TelemetryRepository, dedupCache *cache.TTLCache, hub *websocket.Hub) TelemetryService {
	return &telemetryService{
		repo:       repo,
		dedupCache: dedupCache,
		hub:        hub,
	}
}

func (s *telemetryService) IngestPacket(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error) {
	if packet.ReceivedAt.IsZero() {
		packet.ReceivedAt = time.Now().UTC()
	}

	// Compute packet hash if not provided
	if packet.PacketHash == "" {
		h := sha256.New()
		h.Write([]byte(fmt.Sprintf("%s|%s|%.6f|%.6f|%s",
			packet.PacketID, packet.TargetID.Raw, packet.Latitude, packet.Longitude, packet.Timestamp.Format(time.RFC3339Nano),
		)))
		packet.PacketHash = hex.EncodeToString(h.Sum(nil))
	}

	// Step 1: Check In-Memory Dedup Cache
	if s.dedupCache.Exists(packet.PacketHash) {
		// Log hop event for duplicate packet without creating a new incident row
		_ = s.repo.AppendHopEvent(ctx, packet.PacketID, packet.HopCount, packet.SenderDeviceID)
		
		existing, err := s.repo.GetIncidentByPacketID(ctx, packet.PacketID)
		if err == nil {
			s.hub.Broadcast(websocket.EventIncidentDedupped, map[string]interface{}{
				"packet_id": packet.PacketID,
				"hop_count": packet.HopCount,
				"sender_id": packet.SenderDeviceID,
			})
			return existing, true, nil
		}
		return nil, true, domain.ErrDuplicatePacket
	}

	// Step 2: Store in TTLCache
	s.dedupCache.Set(packet.PacketHash)

	// Step 3: Persist Incident in DB
	incident, isDuplicate, err := s.repo.SaveIncident(ctx, packet)
	if err != nil {
		return nil, false, err
	}

	if isDuplicate {
		_ = s.repo.AppendHopEvent(ctx, packet.PacketID, packet.HopCount, packet.SenderDeviceID)
		s.hub.Broadcast(websocket.EventIncidentDedupped, map[string]interface{}{
			"packet_id": packet.PacketID,
			"hop_count": packet.HopCount,
			"sender_id": packet.SenderDeviceID,
		})
		return incident, true, nil
	}

	// Step 4: Record initial hop event
	_ = s.repo.AppendHopEvent(ctx, packet.PacketID, packet.HopCount, packet.SenderDeviceID)

	// Step 5: Broadcast real-time incident event via WebSocket
	s.hub.Broadcast(websocket.EventNewIncident, incident)

	return incident, false, nil
}

func (s *telemetryService) GetIncidentByPacketID(ctx context.Context, packetID string) (*domain.Incident, error) {
	return s.repo.GetIncidentByPacketID(ctx, packetID)
}

func (s *telemetryService) ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, error) {
	return s.repo.ListIncidents(ctx, filter)
}

func (s *telemetryService) UpdateStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus) error {
	err := s.repo.UpdateIncidentStatus(ctx, incidentID, status)
	if err != nil {
		return err
	}
	s.hub.Broadcast(websocket.EventIncidentStatus, map[string]interface{}{
		"incident_id": incidentID,
		"status":      status,
	})
	return nil
}
