package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type EmergencyTypeRepository interface {
	ListEmergencyTypes(ctx context.Context, includeInactive bool) ([]*domain.EmergencyType, error)
	CreateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
	UpdateEmergencyType(ctx context.Context, emergencyType *domain.EmergencyType) (*domain.EmergencyType, error)
}
