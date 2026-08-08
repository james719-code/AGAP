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
	UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus, changedBy *int64) error
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

	agency, err := s.agencyRepo.GetAgencyByID(ctx, unit.AgencyID)
	if err != nil {
		return nil, fmt.Errorf("responder agency not found: %w", err)
	}
	if !isAgencyTypeAllowedForIncident(agency.Type, inc.IncidentType) {
		return nil, domain.ErrInvalidDispatchAgency
	}

	dispatch := &domain.IncidentDispatch{
		IncidentID:   incidentID,
		UnitID:       unitID,
		DispatchedBy: dispatchedBy,
		Status:       domain.DispatchStatusAssigned,
	}

	created, err := s.dispatchRepo.CreateDispatch(ctx, dispatch)
	if err != nil {
		return nil, err
	}

	// Automatically transition incident status to DISPATCHED when a unit is assigned.
	if inc.Status == domain.StatusReceived || inc.Status == domain.StatusAcknowledged {
		_ = s.telemetryRepo.UpdateIncidentStatus(ctx, incidentID, domain.StatusDispatched)
		_ = s.telemetryRepo.AppendIncidentStatusHistory(ctx, &domain.IncidentStatusHistory{
			IncidentID: incidentID,
			Status:     domain.StatusDispatched,
			ChangedBy:  dispatchedBy,
			Notes:      fmt.Sprintf("Unit %s dispatched", unit.UnitCallsign),
		})
	}

	// Update responder unit status to DISPATCHED
	_ = s.agencyRepo.UpdateResponderLocation(ctx, unitID, unit.CurrentLat, unit.CurrentLon, "DISPATCHED")

	// Broadcast WS event
	payload := map[string]interface{}{
		"event":       "INCIDENT_DISPATCHED",
		"incident_id": incidentID,
		"dispatch_id": created.ID,
		"unit_id":     unitID,
		"callsign":    unit.UnitCallsign,
		"status":      string(domain.StatusDispatched),
	}
	if inc.CitizenID != nil {
		s.hub.BroadcastToAdminsAndUser(websocket.EventIncidentStatus, payload, *inc.CitizenID)
	} else {
		s.hub.BroadcastToAdminsAndDevice(websocket.EventIncidentStatus, payload, inc.SenderDeviceID)
	}

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

func (s *dispatchService) UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus, changedBy *int64) error {
	dispatch, err := s.dispatchRepo.GetDispatchByID(ctx, id)
	if err != nil {
		return err
	}

	if !isValidDispatchTransition(dispatch.Status, status) {
		return domain.ErrInvalidDispatchTransition
	}

	err = s.dispatchRepo.UpdateDispatchStatus(ctx, id, status)
	if err != nil {
		return err
	}

	incidentStatus := incidentStatusForDispatchStatus(status)
	if incidentStatus != "" {
		if err := s.telemetryRepo.UpdateIncidentStatus(ctx, dispatch.IncidentID, incidentStatus); err == nil {
			_ = s.telemetryRepo.AppendIncidentStatusHistory(ctx, &domain.IncidentStatusHistory{
				IncidentID: dispatch.IncidentID,
				Status:     incidentStatus,
				ChangedBy:  changedBy,
				Notes:      fmt.Sprintf("Dispatch %d moved to %s", id, status),
			})
		}
	}

	var targetDeviceID string
	var targetCitizenID *int64
	if inc, err := s.telemetryRepo.GetIncidentByID(ctx, dispatch.IncidentID); err == nil && inc != nil {
		targetDeviceID = inc.SenderDeviceID
		targetCitizenID = inc.CitizenID
	}

	payload := map[string]interface{}{
		"event":       "DISPATCH_STATUS_UPDATED",
		"dispatch_id": id,
		"incident_id": dispatch.IncidentID,
		"unit_id":     dispatch.UnitID,
		"new_status":  string(status),
	}
	if incidentStatus != "" {
		payload["status"] = string(incidentStatus)
	}
	if targetCitizenID != nil {
		s.hub.BroadcastToAdminsAndUser(websocket.EventIncidentStatus, payload, *targetCitizenID)
	} else {
		s.hub.BroadcastToAdminsAndDevice(websocket.EventIncidentStatus, payload, targetDeviceID)
	}

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

func isValidDispatchTransition(from, to domain.DispatchStatus) bool {
	if from == to {
		return true
	}
	terminal := map[domain.DispatchStatus]bool{
		domain.DispatchStatusCompleted: true,
		domain.DispatchStatusCancelled: true,
	}
	if terminal[from] {
		return false
	}
	allowed := map[domain.DispatchStatus][]domain.DispatchStatus{
		domain.DispatchStatusAssigned: {domain.DispatchStatusAccepted, domain.DispatchStatusCancelled},
		domain.DispatchStatusAccepted: {domain.DispatchStatusEnRoute, domain.DispatchStatusCancelled},
		domain.DispatchStatusEnRoute:  {domain.DispatchStatusOnScene, domain.DispatchStatusCompleted, domain.DispatchStatusCancelled},
		domain.DispatchStatusOnScene:  {domain.DispatchStatusCompleted},
	}
	for _, next := range allowed[from] {
		if next == to {
			return true
		}
	}
	return false
}

func incidentStatusForDispatchStatus(status domain.DispatchStatus) domain.IncidentStatus {
	switch status {
	case domain.DispatchStatusEnRoute:
		return domain.StatusEnRoute
	case domain.DispatchStatusOnScene:
		return domain.StatusOnScene
	case domain.DispatchStatusCompleted:
		return domain.StatusResolved
	default:
		return ""
	}
}

func isAgencyTypeAllowedForIncident(agencyType domain.AgencyType, incidentType domain.IncidentType) bool {
	expected := map[domain.IncidentType]domain.AgencyType{
		domain.IncidentTypeMedical:  domain.AgencyMedical,
		domain.IncidentTypeAccident: domain.AgencyRescue,
		domain.IncidentTypeFire:     domain.AgencyFire,
		domain.IncidentTypeDisaster: domain.AgencyDRRMO,
		domain.IncidentTypeCrime:    domain.AgencyPolice,
	}

	target, ok := expected[incidentType]
	if !ok {
		return false
	}
	return agencyType == target
}
