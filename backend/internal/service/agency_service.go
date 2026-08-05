package service

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

type AgencyService interface {
	CreateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error)
	GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error)
	ListAgencies(ctx context.Context) ([]*domain.Agency, error)
	UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error)
	DeleteAgency(ctx context.Context, id int64) error

	CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error)
	GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error)
	ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error)
	UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error)
	UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error
	DeleteResponder(ctx context.Context, id int64) error
}

type agencyService struct {
	repo repository.AgencyRepository
	hub  *websocket.Hub
}

func NewAgencyService(repo repository.AgencyRepository, hub *websocket.Hub) AgencyService {
	return &agencyService{
		repo: repo,
		hub:  hub,
	}
}

func (s *agencyService) CreateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	return s.repo.CreateAgency(ctx, agency)
}

func (s *agencyService) GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error) {
	return s.repo.GetAgencyByID(ctx, id)
}

func (s *agencyService) ListAgencies(ctx context.Context) ([]*domain.Agency, error) {
	return s.repo.ListAgencies(ctx)
}

func (s *agencyService) UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	return s.repo.UpdateAgency(ctx, agency)
}

func (s *agencyService) DeleteAgency(ctx context.Context, id int64) error {
	return s.repo.DeleteAgency(ctx, id)
}

func (s *agencyService) CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	return s.repo.CreateResponder(ctx, responder)
}

func (s *agencyService) GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error) {
	return s.repo.GetResponderByID(ctx, id)
}

func (s *agencyService) ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error) {
	return s.repo.ListResponders(ctx, agencyID)
}

func (s *agencyService) UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	return s.repo.UpdateResponder(ctx, responder)
}

func (s *agencyService) UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error {
	err := s.repo.UpdateResponderLocation(ctx, id, lat, lon, status)
	if err != nil {
		return err
	}
	s.hub.Broadcast(websocket.EventResponderLocation, map[string]interface{}{
		"responder_id": id,
		"latitude":     lat,
		"longitude":    lon,
		"status":       status,
	})
	return nil
}

func (s *agencyService) DeleteResponder(ctx context.Context, id int64) error {
	return s.repo.DeleteResponder(ctx, id)
}
