package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type DispatchRepository interface {
	CreateDispatch(ctx context.Context, dispatch *domain.IncidentDispatch) (*domain.IncidentDispatch, error)
	GetDispatchByID(ctx context.Context, id int64) (*domain.IncidentDispatch, error)
	ListDispatchesByIncident(ctx context.Context, incidentID int64) ([]*domain.IncidentDispatch, error)
	ListDispatchesByIncidentBulk(ctx context.Context, incidentIDs []int64) (map[int64][]*domain.IncidentDispatch, error)
	ListDispatchesByResponderUnit(ctx context.Context, unitID int64) ([]*domain.IncidentDispatch, error)
	GetActiveDispatchByResponderUnit(ctx context.Context, unitID int64) (*domain.IncidentDispatch, error)
	UpdateDispatchStatus(ctx context.Context, id int64, status domain.DispatchStatus) error
}
