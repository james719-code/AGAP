package service

import (
	"context"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type BootstrapPayload struct {
	User               *domain.User                `json:"user"`
	Agencies           []*domain.Agency            `json:"agencies"`
	EmergencyTypes     []*domain.EmergencyType     `json:"emergency_types"`
	PredefinedMessages []*domain.PredefinedMessage `json:"predefined_messages"`
	AssignedDispatches []*domain.IncidentDispatch  `json:"assigned_dispatches,omitempty"`
	ActiveIncidents    []*domain.Incident          `json:"active_incidents"`
	CatalogVersion     string                      `json:"catalog_version"`
	GeographyVersion   string                      `json:"geography_version"`
	RoutingVersion     string                      `json:"routing_version"`
	MapVersion         string                      `json:"map_version"`
}

type SyncService interface {
	Bootstrap(ctx context.Context, userID int64) (*BootstrapPayload, error)
	Push(ctx context.Context, req SyncPushRequest) (*SyncPushResult, error)
	Changes(ctx context.Context, since time.Time) (*SyncChangesPayload, error)
}

type syncService struct {
	userRepo          repository.UserRepository
	agencyRepo        repository.AgencyRepository
	emergencyTypeRepo repository.EmergencyTypeRepository
	messageRepo       repository.MessageRepository
	telemetryRepo     repository.TelemetryRepository
	dispatchRepo      repository.DispatchRepository
	telemetrySvc      TelemetryService
}

func NewSyncService(
	userRepo repository.UserRepository,
	agencyRepo repository.AgencyRepository,
	emergencyTypeRepo repository.EmergencyTypeRepository,
	messageRepo repository.MessageRepository,
	telemetryRepo repository.TelemetryRepository,
	dispatchRepo repository.DispatchRepository,
	telemetrySvc TelemetryService,
) SyncService {
	return &syncService{
		userRepo:          userRepo,
		agencyRepo:        agencyRepo,
		emergencyTypeRepo: emergencyTypeRepo,
		messageRepo:       messageRepo,
		telemetryRepo:     telemetryRepo,
		dispatchRepo:      dispatchRepo,
		telemetrySvc:      telemetrySvc,
	}
}

func (s *syncService) Bootstrap(ctx context.Context, userID int64) (*BootstrapPayload, error) {
	user, err := s.userRepo.GetUserByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	agencies, err := s.agencyRepo.ListAgencies(ctx)
	if err != nil {
		return nil, err
	}

	emergencyTypes, err := s.emergencyTypeRepo.ListEmergencyTypes(ctx, false)
	if err != nil {
		return nil, err
	}

	predefinedMessages, err := s.messageRepo.ListPredefinedMessages(ctx, false)
	if err != nil {
		return nil, err
	}

	activeIncidents, _, err := s.telemetryRepo.ListIncidents(ctx, repository.IncidentFilter{Limit: 100})
	if err != nil {
		return nil, err
	}

	payload := &BootstrapPayload{
		User:               user,
		Agencies:           agencies,
		EmergencyTypes:     emergencyTypes,
		PredefinedMessages: predefinedMessages,
		ActiveIncidents:    filterBootstrapIncidents(activeIncidents),
		CatalogVersion:     "1.0.0",
		GeographyVersion:   "1.0.0",
		RoutingVersion:     "1.0.0",
		MapVersion:         "1.0.0",
	}

	if user.Role == domain.RoleResponder {
		responderUnit, err := s.agencyRepo.GetResponderByUserID(ctx, user.ID)
		if err == nil {
			dispatches, err := s.dispatchRepo.ListDispatchesByResponderUnit(ctx, responderUnit.ID)
			if err != nil {
				return nil, err
			}
			payload.AssignedDispatches = dispatches
		} else if err != domain.ErrNotFound {
			return nil, err
		}
	}

	return payload, nil
}

func filterBootstrapIncidents(incidents []*domain.Incident) []*domain.Incident {
	var active []*domain.Incident
	for _, incident := range incidents {
		switch incident.Status {
		case domain.StatusResolved, domain.StatusCancelled, domain.StatusFalseAlarm:
			continue
		default:
			active = append(active, incident)
		}
	}
	return active
}

type SyncPushRequest struct {
	TelemetryPackets []*domain.TelemetryPacket   `json:"telemetry_packets,omitempty"`
	MeshEvents       []*domain.MeshDeliveryEvent `json:"mesh_events,omitempty"`
}

type SyncPushResult struct {
	Status             string   `json:"status"`
	TelemetryAccepted  int      `json:"telemetry_accepted"`
	TelemetryDuplicate int      `json:"telemetry_duplicate"`
	MeshEventsRecorded int      `json:"mesh_events_recorded"`
	Errors             []string `json:"errors,omitempty"`
}

type SyncChangesPayload struct {
	Since              time.Time                   `json:"since"`
	ServerTime         time.Time                   `json:"server_time"`
	Incidents          []*domain.Incident          `json:"incidents"`
	Agencies           []*domain.Agency            `json:"agencies"`
	EmergencyTypes     []*domain.EmergencyType     `json:"emergency_types"`
	PredefinedMessages []*domain.PredefinedMessage `json:"predefined_messages"`
}

func (s *syncService) Push(ctx context.Context, req SyncPushRequest) (*SyncPushResult, error) {
	result := &SyncPushResult{Status: "accepted"}

	for _, packet := range req.TelemetryPackets {
		if packet == nil {
			continue
		}
		if packet.TargetID.Raw != "" && packet.TargetID.Region == "" {
			targetID, err := domain.ParseTargetID(packet.TargetID.Raw)
			if err != nil {
				result.Errors = append(result.Errors, err.Error())
				continue
			}
			packet.TargetID = targetID
		}
		_, duplicate, err := s.telemetrySvc.IngestPacket(ctx, packet)
		if err != nil {
			result.Errors = append(result.Errors, err.Error())
			continue
		}
		if duplicate {
			result.TelemetryDuplicate++
		} else {
			result.TelemetryAccepted++
		}
	}

	recorded, err := s.telemetrySvc.RecordMeshDeliveryEvents(ctx, req.MeshEvents)
	if err != nil {
		result.Errors = append(result.Errors, err.Error())
	} else {
		result.MeshEventsRecorded = recorded
	}

	if len(result.Errors) > 0 {
		result.Status = "partial"
	}
	return result, nil
}

func (s *syncService) Changes(ctx context.Context, since time.Time) (*SyncChangesPayload, error) {
	incidents, _, err := s.telemetryRepo.ListIncidents(ctx, repository.IncidentFilter{Since: &since, Limit: 200})
	if err != nil {
		return nil, err
	}
	agencies, err := s.agencyRepo.ListAgencies(ctx)
	if err != nil {
		return nil, err
	}
	emergencyTypes, err := s.emergencyTypeRepo.ListEmergencyTypes(ctx, false)
	if err != nil {
		return nil, err
	}
	messages, err := s.messageRepo.ListPredefinedMessages(ctx, false)
	if err != nil {
		return nil, err
	}

	return &SyncChangesPayload{
		Since:              since,
		ServerTime:         time.Now().UTC(),
		Incidents:          incidents,
		Agencies:           agencies,
		EmergencyTypes:     emergencyTypes,
		PredefinedMessages: messages,
	}, nil
}
