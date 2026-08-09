package service

import (
	"context"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type EmergencyTypeService interface {
	ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error)
	CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
	UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
	DeleteEmergencyType(ctx context.Context, id int64) error
}

type emergencyTypeService struct {
	repo repository.EmergencyTypeRepository
	hub  *websocket.Hub
}

func NewEmergencyTypeService(repo repository.EmergencyTypeRepository, hub *websocket.Hub) EmergencyTypeService {
	return &emergencyTypeService{repo: repo, hub: hub}
}

func (s *emergencyTypeService) ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error) {
	return s.repo.ListEmergencyTypes(ctx, includeInactive)
}

func (s *emergencyTypeService) CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	normalizeEmergencyType(emergencyType)
	if err := validateEmergencyType(emergencyType); err != nil {
		return nil, err
	}
	created, err := s.repo.CreateEmergencyType(ctx, emergencyType)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "emergency_types", "action": "upsert", "id": created.ID})
	}
	return created, err
}

func (s *emergencyTypeService) UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	normalizeEmergencyType(emergencyType)
	if emergencyType.ID <= 0 {
		return nil, domain.ErrNotFound
	}
	if err := validateEmergencyType(emergencyType); err != nil {
		return nil, err
	}
	updated, err := s.repo.UpdateEmergencyType(ctx, emergencyType)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "emergency_types", "action": "upsert", "id": updated.ID})
	}
	return updated, err
}

func (s *emergencyTypeService) DeleteEmergencyType(ctx context.Context, id int64) error {
	if err := s.repo.DeleteEmergencyType(ctx, id); err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "emergency_types", "action": "delete", "id": id})
	}
	return nil
}

func normalizeEmergencyType(emergencyType *domain.EmergencyType) {
	emergencyType.Code = strings.ToUpper(strings.TrimSpace(emergencyType.Code))
	emergencyType.Name = strings.TrimSpace(emergencyType.Name)
	emergencyType.Description = strings.TrimSpace(emergencyType.Description)
	emergencyType.TargetAgencyType = strings.ToUpper(strings.TrimSpace(emergencyType.TargetAgencyType))
	if emergencyType.DefaultPriority == 0 {
		emergencyType.DefaultPriority = 3
	}
}

func validateEmergencyType(emergencyType *domain.EmergencyType) error {
	if emergencyType.Code == "" || emergencyType.Name == "" {
		return domain.ErrInvalidEmergencyType
	}
	if emergencyType.DefaultPriority < 1 || emergencyType.DefaultPriority > 5 {
		return domain.ErrInvalidEmergencyType
	}
	if emergencyType.TargetAgencyType != "" && !domain.AgencyType(emergencyType.TargetAgencyType).IsValid() {
		return domain.ErrInvalidEmergencyType
	}
	return nil
}
