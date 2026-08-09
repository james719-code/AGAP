package service

import (
	"context"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type EmergencyTypeService interface {
	ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error)
	CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
	UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
	DeleteEmergencyType(ctx context.Context, id int64) error
}

type emergencyTypeService struct {
	repo repository.EmergencyTypeRepository
}

func NewEmergencyTypeService(repo repository.EmergencyTypeRepository) EmergencyTypeService {
	return &emergencyTypeService{repo: repo}
}

func (s *emergencyTypeService) ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error) {
	return s.repo.ListEmergencyTypes(ctx, includeInactive)
}

func (s *emergencyTypeService) CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	normalizeEmergencyType(emergencyType)
	if err := validateEmergencyType(emergencyType); err != nil {
		return nil, err
	}
	return s.repo.CreateEmergencyType(ctx, emergencyType)
}

func (s *emergencyTypeService) UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error) {
	normalizeEmergencyType(emergencyType)
	if emergencyType.ID <= 0 {
		return nil, domain.ErrNotFound
	}
	if err := validateEmergencyType(emergencyType); err != nil {
		return nil, err
	}
	return s.repo.UpdateEmergencyType(ctx, emergencyType)
}

func (s *emergencyTypeService) DeleteEmergencyType(ctx context.Context, id int64) error {
	return s.repo.DeleteEmergencyType(ctx, id)
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
