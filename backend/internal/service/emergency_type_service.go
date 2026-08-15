package service

import (
	"context"
	"strings"
	"sync"

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
	repo     repository.EmergencyTypeRepository
	hub      *websocket.Hub
	mu       sync.RWMutex
	cacheAll []*domain.EmergencyType
	cacheAct []*domain.EmergencyType
}

func NewEmergencyTypeService(repo repository.EmergencyTypeRepository, hub *websocket.Hub) EmergencyTypeService {
	return &emergencyTypeService{repo: repo, hub: hub}
}

func (s *emergencyTypeService) invalidateCache() {
	s.mu.Lock()
	s.cacheAll = nil
	s.cacheAct = nil
	s.mu.Unlock()
}

func (s *emergencyTypeService) ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error) {
	s.mu.RLock()
	if includeInactive && s.cacheAll != nil {
		cached := s.cacheAll
		s.mu.RUnlock()
		return cached, nil
	} else if !includeInactive && s.cacheAct != nil {
		cached := s.cacheAct
		s.mu.RUnlock()
		return cached, nil
	}
	s.mu.RUnlock()

	types, err := s.repo.ListEmergencyTypes(ctx, includeInactive)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	if includeInactive {
		s.cacheAll = types
	} else {
		s.cacheAct = types
	}
	s.mu.Unlock()

	return types, nil
}

func (s *emergencyTypeService) CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	normalizeEmergencyType(emergencyType)
	if err := validateEmergencyType(emergencyType); err != nil {
		return nil, err
	}
	created, err := s.repo.CreateEmergencyType(ctx, emergencyType)
	if err == nil {
		s.invalidateCache()
		if s.hub != nil {
			s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "emergency_types", "action": "upsert", "id": created.ID})
		}
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
	if err == nil {
		s.invalidateCache()
		if s.hub != nil {
			s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "emergency_types", "action": "upsert", "id": updated.ID})
		}
	}
	return updated, err
}

func (s *emergencyTypeService) DeleteEmergencyType(ctx context.Context, id int64) error {
	if err := s.repo.DeleteEmergencyType(ctx, id); err != nil {
		return err
	}
	s.invalidateCache()
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
