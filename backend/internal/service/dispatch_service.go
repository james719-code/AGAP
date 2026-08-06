package service

import (
	"context"
	"fmt"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type DispatchService interface {
	CreateDispatch(ctx context.Context, incidentID int64, unitID int64, dispatchedBy *int64) (*domain.IncidentDispatch, error)
	ListDispatchesByIncident(ctx context.Context, incidentID int64) ([]*domain.IncidentDispatch, error)
	ListDispatchesByResponderUnit(ctx context.Context, unitID int64) ([]*domain.IncidentDispatch, error)
	UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus) error
}

type dispatchService struct {
	dispatchRepo  repository.DispatchRepository
	telemetryRepo repository.TelemetryRepository
	agencyRepo    repository.AgencyRepository
	notifRepo     repository.NotificationRepository
	auditRepo     repository.AuditRepository
	hub           *websocket.Hub
}

func NewDispatchService(
	dispatchRepo repository.DispatchRepository,
	telemetryRepo repository.TelemetryRepository,
	agencyRepo repository.AgencyRepository,
	notifRepo repository.NotificationRepository,
	auditRepo repository.AuditRepository,
	hub *websocket.Hub,
) DispatchService {
	return &dispatchService{
		dispatchRepo:  dispatchRepo,
		telemetryRepo: telemetryRepo,
		agencyRepo:    agencyRepo,
		notifRepo:     notifRepo,
		auditRepo:     auditRepo,
		hub:           hub,
	}
}

func (s *dispatchService) CreateDispatch(ctx context.Context, incidentID int64, unitID int64, dispatchedBy *int64) (*domain.IncidentDispatch, error) {
	inc, err := s.telemetryRepo.GetIncidentByID(ctx, incidentID)
	if err != nil {
		return nil, fmt.Errorf("incident not found: %w", err)
	}

	unit, err := s.agencyRepo.GetResponderByID(ctx, unitID)
	if err != nil {
		return nil, fmt.Errorf("responder unit not found: %w", err)
	}

	dispatch := &domain.IncidentDispatch{
		IncidentID:   incidentID,
		UnitID:       unitID,
		DispatchedBy: dispatchedBy,
		Status:       domain.DispatchStatusEnRoute,
	}

	created, err := s.dispatchRepo.CreateDispatch(ctx, dispatch)
	if err != nil {
		return nil, err
	}

	// Automatically transition incident status to DISPATCHED if ACTIVE
	if inc.Status == domain.StatusActive {
		_ = s.telemetryRepo.UpdateIncidentStatus(ctx, incidentID, domain.StatusDispatched)
	}

	// Update responder unit status to DISPATCHED
	_ = s.agencyRepo.UpdateResponderLocation(ctx, unitID, unit.CurrentLat, unit.CurrentLon, "DISPATCHED")

	// Broadcast WS event
	s.hub.BroadcastToAdminsAndDevice(websocket.EventIncidentStatus, map[string]interface{}{
		"event":        "INCIDENT_DISPATCHED",
		"incident_id":  incidentID,
		"dispatch_id":  created.ID,
		"unit_id":      unitID,
		"callsign":     unit.UnitCallsign,
		"status":       string(domain.StatusDispatched),
	}, inc.SenderDeviceID)

	// Create Notification for citizen if present
	if inc.CitizenID != nil && s.notifRepo != nil {
		_, _ = s.notifRepo.CreateNotification(ctx, &domain.Notification{
			UserID:     *inc.CitizenID,
			IncidentID: &incidentID,
			Type:       "UNIT_DISPATCHED",
			Payload:    fmt.Sprintf("Responder unit %s has been dispatched to your incident #%d", unit.UnitCallsign, incidentID),
		})
	}

	// Create Notification for responder user if linked
	if unit.UserID != nil && s.notifRepo != nil {
		_, _ = s.notifRepo.CreateNotification(ctx, &domain.Notification{
			UserID:     *unit.UserID,
			IncidentID: &incidentID,
			Type:       "DISPATCH_ASSIGNMENT",
			Payload:    fmt.Sprintf("Your unit %s has been dispatched to incident #%d (%s)", unit.UnitCallsign, incidentID, inc.IncidentType),
		})
	}

	// Audit log
	if s.auditRepo != nil {
		actorID := int64(0)
		if dispatchedBy != nil {
			actorID = *dispatchedBy
		}
		_ = s.auditRepo.LogAction(ctx, &domain.AuditLog{
			ActorID:    &actorID,
			Action:     "CREATE_DISPATCH",
			EntityType: "DISPATCH",
			EntityID:   created.ID,
			NewValue:   fmt.Sprintf("Incident %d assigned to Unit %d (%s)", incidentID, unitID, unit.UnitCallsign),
		})
	}

	return created, nil
}

func (s *dispatchService) ListDispatchesByIncident(ctx context.Context, incidentID int64) ([]*domain.IncidentDispatch, error) {
	return s.dispatchRepo.ListDispatchesByIncident(ctx, incidentID)
}

func (s *dispatchService) ListDispatchesByResponderUnit(ctx context.Context, unitID int64) ([]*domain.IncidentDispatch, error) {
	return s.dispatchRepo.ListDispatchesByResponderUnit(ctx, unitID)
}

func (s *dispatchService) UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus) error {
	dispatch, err := s.dispatchRepo.GetDispatchByID(ctx, id)
	if err != nil {
		return err
	}

	err = s.dispatchRepo.UpdateDispatchStatus(ctx, id, status)
	if err != nil {
		return err
	}

	s.hub.BroadcastToAdmins(websocket.EventIncidentStatus, map[string]interface{}{
		"event":       "DISPATCH_STATUS_UPDATED",
		"dispatch_id": id,
		"incident_id": dispatch.IncidentID,
		"unit_id":     dispatch.UnitID,
		"new_status":  string(status),
	})

	if s.auditRepo != nil {
		_ = s.auditRepo.LogAction(ctx, &domain.AuditLog{
			Action:     "UPDATE_DISPATCH_STATUS",
			EntityType: "DISPATCH",
			EntityID:   id,
			OldValue:   string(dispatch.Status),
			NewValue:   string(status),
		})
	}

	return nil
}
