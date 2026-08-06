package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type AgencyRepository interface {
	// Agency CRUD
	CreateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error)
	GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error)
	GetAgencyByCode(ctx context.Context, code string) (*domain.Agency, error)
	ListAgencies(ctx context.Context) ([]*domain.Agency, error)
	UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error)
	DeleteAgency(ctx context.Context, id int64) error

	// ResponderUnit CRUD
	CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error)
	GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error)
	GetResponderByUserID(ctx context.Context, userID int64) (*domain.ResponderUnit, error)
	ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error)
	UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error)
	UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error
	DeleteResponder(ctx context.Context, id int64) error
}

