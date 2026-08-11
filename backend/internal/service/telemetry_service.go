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
	GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error)
	GetIncidentByIncidentUUID(ctx context.Context, incidentUUID string) (*domain.Incident, error)
	GetIncidentByPacketUUID(ctx context.Context, packetUUID string) (*domain.Incident, error)
	ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, int, error)
	CanCitizenAccessIncident(ctx context.Context, incidentID, userID int64) (bool, error)
	UpdateStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus, changedBy *int64, notes string) error
	ListStatusHistory(ctx context.Context, incidentID int64) ([]*domain.IncidentStatusHistory, error)
	ListStatusHistoryBulk(ctx context.Context, incidentIDs []int64) (map[int64][]*domain.IncidentStatusHistory, error)
	RecordMeshDeliveryEvents(ctx context.Context, events []*domain.MeshDeliveryEvent) (int, error)
}

type telemetryService struct {
	repo       repository.TelemetryRepository
	notifRepo  repository.NotificationRepository
	auditRepo  repository.AuditRepository
	dedupCache *cache.TTLCache
	hub        *websocket.Hub
}

func NewTelemetryService(repo repository.TelemetryRepository, notifRepo repository.NotificationRepository, auditRepo repository.AuditRepository, dedupCache *cache.TTLCache, hub *websocket.Hub) TelemetryService {
	return &telemetryService{
		repo:       repo,
		notifRepo:  notifRepo,
		auditRepo:  auditRepo,
		dedupCache: dedupCache,
		hub:        hub,
	}
}

func (s *telemetryService) IngestPacket(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error) {
	return s.ingestPacket(ctx, packet, true)
}

// IngestPacketDeferred persists an incident without publishing NEW_INCIDENT.
// Composite workflows use it so clients cannot observe a half-persisted aggregate.
func (s *telemetryService) IngestPacketDeferred(ctx context.Context, packet *domain.TelemetryPacket) (*domain.Incident, bool, error) {
	return s.ingestPacket(ctx, packet, false)
}

func (s *telemetryService) ingestPacket(ctx context.Context, packet *domain.TelemetryPacket, publish bool) (*domain.Incident, bool, error) {
	if packet.ReceivedAt.IsZero() {
		packet.ReceivedAt = time.Now().UTC()
	}

	// Compute packet hash if not provided
	if packet.PacketHash == "" {
		h := sha256.New()
		h.Write([]byte(fmt.Sprintf("%s|%s|%.6f|%.6f|%s",
			packet.PacketUUID, packet.TargetID.Raw, packet.Latitude, packet.Longitude, packet.Timestamp.Format(time.RFC3339Nano),
		)))
		packet.PacketHash = hex.EncodeToString(h.Sum(nil))
	}

	// Step 1: Check In-Memory Dedup Cache
	if s.dedupCache.Exists(packet.PacketHash) {
		existing, err := s.repo.GetIncidentByPacketUUID(ctx, packet.PacketUUID)
		if err == nil {
			if eventErr := s.repo.RecordMeshDeliveryEvent(ctx, meshDeliveryEventForPacket(packet, &existing.ID, domain.MeshEventDuplicateDropped)); eventErr == nil && s.hub != nil {
				s.hub.BroadcastToAdmins(websocket.EventTelemetryUpdated, map[string]interface{}{"reason": "duplicate_dropped"})
			}
			s.hub.Broadcast(websocket.EventIncidentDedupped, map[string]interface{}{
				"packet_uuid": packet.PacketUUID,
				"hop_count":   packet.HopCount,
				"sender_id":   packet.SenderDeviceID,
			})
			return existing, true, nil
		}
		return nil, true, domain.ErrDuplicatePacket
	}

	// Step 2: Persist Incident in DB. Do not poison the in-memory dedup cache
	// before persistence succeeds; otherwise a transient DB failure can suppress
	// a legitimate retry for the entire TTL window.
	incident, isDuplicate, err := s.repo.SaveIncident(ctx, packet)
	if err != nil {
		return nil, false, err
	}
	s.dedupCache.Set(packet.PacketHash)

	if isDuplicate {
		if incident != nil {
			if eventErr := s.repo.RecordMeshDeliveryEvent(ctx, meshDeliveryEventForPacket(packet, &incident.ID, domain.MeshEventDuplicateDropped)); eventErr == nil && s.hub != nil {
				s.hub.BroadcastToAdmins(websocket.EventTelemetryUpdated, map[string]interface{}{"reason": "duplicate_dropped"})
			}
		}
		s.hub.BroadcastToAdmins(websocket.EventIncidentDedupped, map[string]interface{}{
			"packet_uuid": packet.PacketUUID,
			"hop_count":   packet.HopCount,
			"sender_id":   packet.SenderDeviceID,
		})
		return incident, true, nil
	}

	// Step 4: Record mesh delivery event for accepted uplink.
	_ = s.repo.RecordMeshDeliveryEvent(ctx, meshDeliveryEventForPacket(packet, &incident.ID, domain.MeshEventUplinked))
	s.hub.BroadcastToAdmins(websocket.EventTelemetryUpdated, map[string]interface{}{"reason": "packet_ingested"})

	// Step 5: Broadcast real-time distress incident event exclusively to Admins & Responders
	if publish {
		s.hub.BroadcastToAdmins(websocket.EventNewIncident, incident)
		if s.notifRepo != nil {
			_ = s.notifRepo.CreateNotificationsForRole(ctx, domain.RoleAdmin, &domain.Notification{
				IncidentID: &incident.ID,
				Type:       string(websocket.EventNewIncident),
				Payload:    fmt.Sprintf("New %s incident #%d received", incident.IncidentType, incident.ID),
			})
		}
	}

	return incident, false, nil
}

func meshDeliveryEventForPacket(packet *domain.TelemetryPacket, incidentID *int64, eventType domain.MeshDeliveryEventType) *domain.MeshDeliveryEvent {
	latitude := packet.Latitude
	longitude := packet.Longitude
	return &domain.MeshDeliveryEvent{
		PacketUUID:       packet.PacketUUID,
		PacketType:       domain.MeshPacketTypeSOS,
		IncidentID:       incidentID,
		OriginDeviceID:   packet.SenderDeviceID,
		PayloadHash:      packet.PacketHash,
		EventType:        eventType,
		DeviceIdentifier: packet.SenderDeviceID,
		Latitude:         &latitude,
		Longitude:        &longitude,
		RecordedAt:       packet.ReceivedAt,
		SyncedAt:         &packet.ReceivedAt,
	}
}

func (s *telemetryService) GetIncidentByID(ctx context.Context, id int64) (*domain.Incident, error) {
	return s.repo.GetIncidentByID(ctx, id)
}

func (s *telemetryService) GetIncidentByIncidentUUID(ctx context.Context, incidentUUID string) (*domain.Incident, error) {
	return s.repo.GetIncidentByIncidentUUID(ctx, incidentUUID)
}

func (s *telemetryService) GetIncidentByPacketUUID(ctx context.Context, packetUUID string) (*domain.Incident, error) {
	return s.repo.GetIncidentByPacketUUID(ctx, packetUUID)
}

func (s *telemetryService) ListIncidents(ctx context.Context, filter repository.IncidentFilter) ([]*domain.Incident, int, error) {
	return s.repo.ListIncidents(ctx, filter)
}

func (s *telemetryService) CanCitizenAccessIncident(ctx context.Context, incidentID, userID int64) (bool, error) {
	return s.repo.CanCitizenAccessIncident(ctx, incidentID, userID)
}

func (s *telemetryService) ListStatusHistory(ctx context.Context, incidentID int64) ([]*domain.IncidentStatusHistory, error) {
	return s.repo.ListIncidentStatusHistory(ctx, incidentID)
}

func (s *telemetryService) ListStatusHistoryBulk(ctx context.Context, incidentIDs []int64) (map[int64][]*domain.IncidentStatusHistory, error) {
	return s.repo.ListIncidentStatusHistoryBulk(ctx, incidentIDs)
}

func (s *telemetryService) RecordMeshDeliveryEvents(ctx context.Context, events []*domain.MeshDeliveryEvent) (int, error) {
	count := 0
	now := time.Now().UTC()
	for _, event := range events {
		if event == nil {
			continue
		}
		if event.PacketUUID == "" || event.EventType == "" {
			return count, domain.ErrInvalidMeshDeliveryEvent
		}
		if event.PacketType == "" {
			event.PacketType = domain.MeshPacketTypeSOS
		}
		if event.RecordedAt.IsZero() {
			event.RecordedAt = now
		}
		if event.SyncedAt == nil {
			event.SyncedAt = &now
		}
		if err := s.repo.RecordMeshDeliveryEvent(ctx, event); err != nil {
			return count, err
		}
		count++
	}
	if count > 0 && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventTelemetryUpdated, map[string]interface{}{"reason": "mesh_events_recorded", "count": count})
	}
	return count, nil
}

func (s *telemetryService) UpdateStatus(ctx context.Context, incidentID int64, status domain.IncidentStatus, changedBy *int64, notes string) error {
	inc, err := s.repo.GetIncidentByID(ctx, incidentID)
	if err != nil {
		return err
	}

	if !isValidIncidentTransition(inc.Status, status) {
		return domain.ErrInvalidStatusTransition
	}

	err = s.repo.UpdateIncidentStatus(ctx, incidentID, status)
	if err != nil {
		return err
	}
	_ = s.repo.AppendIncidentStatusHistory(ctx, &domain.IncidentStatusHistory{
		IncidentID: incidentID,
		Status:     status,
		ChangedBy:  changedBy,
		Notes:      notes,
	})

	// Broadcast status update to operational users and the citizen account.
	payload := map[string]interface{}{
		"incident_id": incidentID,
		"status":      status,
	}
	if inc.CitizenID != nil {
		s.hub.BroadcastToAdminsAndUser(websocket.EventIncidentStatus, payload, *inc.CitizenID)
	} else {
		s.hub.BroadcastToAdminsAndDevice(websocket.EventIncidentStatus, payload, inc.SenderDeviceID)
	}

	// Create persistent notification for citizen if associated
	if inc.CitizenID != nil && s.notifRepo != nil {
		_, _ = s.notifRepo.CreateNotification(ctx, &domain.Notification{
			UserID:     *inc.CitizenID,
			IncidentID: &incidentID,
			Type:       "INCIDENT_STATUS_UPDATE",
			Payload:    fmt.Sprintf("Your incident #%d status updated to %s", incidentID, status),
		})
	}

	// Audit Log
	if s.auditRepo != nil {
		_ = s.auditRepo.LogAction(ctx, &domain.AuditLog{
			Action:     "UPDATE_INCIDENT_STATUS",
			EntityType: "INCIDENT",
			EntityID:   incidentID,
			OldValue:   string(inc.Status),
			NewValue:   string(status),
		})
	}

	return nil
}

func isValidIncidentTransition(from, to domain.IncidentStatus) bool {
	if from == to {
		return true
	}

	terminal := map[domain.IncidentStatus]bool{
		domain.StatusResolved:   true,
		domain.StatusCancelled:  true,
		domain.StatusFalseAlarm: true,
	}
	if terminal[from] {
		return false
	}

	allowed := map[domain.IncidentStatus][]domain.IncidentStatus{
		domain.StatusCreated:      {domain.StatusQueued, domain.StatusReceived, domain.StatusCancelled},
		domain.StatusQueued:       {domain.StatusReceived, domain.StatusCancelled},
		domain.StatusReceived:     {domain.StatusAcknowledged, domain.StatusDispatched, domain.StatusCancelled, domain.StatusFalseAlarm},
		domain.StatusAcknowledged: {domain.StatusDispatched, domain.StatusCancelled, domain.StatusFalseAlarm},
		domain.StatusDispatched:   {domain.StatusEnRoute, domain.StatusOnScene, domain.StatusResolved, domain.StatusCancelled},
		domain.StatusEnRoute:      {domain.StatusOnScene, domain.StatusResolved, domain.StatusCancelled},
		domain.StatusOnScene:      {domain.StatusResolved, domain.StatusFalseAlarm},
	}

	for _, next := range allowed[from] {
		if next == to {
			return true
		}
	}
	return false
}
