package service

import (
	"context"
	"time"

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
	GetResponderByUserID(ctx context.Context, userID int64) (*domain.ResponderUnit, error)
	ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error)
	UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error)
	UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error
	DeleteResponder(ctx context.Context, id int64) error

	AddRescueUnitMember(ctx context.Context, member *domain.RescueUnitMember) (*domain.RescueUnitMember, error)
	ListRescueUnitMembers(ctx context.Context, rescueUnitID int64) ([]*domain.RescueUnitMember, error)
	RemoveRescueUnitMember(ctx context.Context, rescueUnitID int64, responderID int64) error
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
	created, err := s.repo.CreateAgency(ctx, agency)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "agencies", "action": "upsert", "id": created.ID})
	}
	return created, err
}

func (s *agencyService) GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error) {
	return s.repo.GetAgencyByID(ctx, id)
}

func (s *agencyService) ListAgencies(ctx context.Context) ([]*domain.Agency, error) {
	return s.repo.ListAgencies(ctx)
}

func (s *agencyService) UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	updated, err := s.repo.UpdateAgency(ctx, agency)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "agencies", "action": "upsert", "id": updated.ID})
	}
	return updated, err
}

func (s *agencyService) DeleteAgency(ctx context.Context, id int64) error {
	if err := s.repo.DeleteAgency(ctx, id); err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventCatalogUpdated, map[string]interface{}{"catalog": "agencies", "action": "delete", "id": id})
	}
	return nil
}

func (s *agencyService) CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	created, err := s.repo.CreateResponder(ctx, responder)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventResponderUpsert, created)
	}
	return created, err
}

func (s *agencyService) GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error) {
	return s.repo.GetResponderByID(ctx, id)
}

func (s *agencyService) GetResponderByUserID(ctx context.Context, userID int64) (*domain.ResponderUnit, error) {
	return s.repo.GetResponderByUserID(ctx, userID)
}

func (s *agencyService) ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error) {
	return s.repo.ListResponders(ctx, agencyID)
}

func (s *agencyService) UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	updated, err := s.repo.UpdateResponder(ctx, responder)
	if err == nil && s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventResponderUpsert, updated)
	}
	return updated, err
}

func (s *agencyService) UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error {
	err := s.repo.UpdateResponderLocation(ctx, id, lat, lon, status)
	if err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventResponderLocation, map[string]interface{}{
			"responder_id": id,
			"latitude":     lat,
			"longitude":    lon,
			"status":       status,
			"last_seen_at": time.Now().UTC(),
		})
	}
	return nil
}

func (s *agencyService) DeleteResponder(ctx context.Context, id int64) error {
	if err := s.repo.DeleteResponder(ctx, id); err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.BroadcastToAdmins(websocket.EventResponderDeleted, map[string]interface{}{"responder_id": id})
	}
	return nil
}

func (s *agencyService) AddRescueUnitMember(ctx context.Context, member *domain.RescueUnitMember) (*domain.RescueUnitMember, error) {
	if member.RescueUnitID <= 0 || member.ResponderID <= 0 {
		return nil, domain.ErrInvalidTargetID
	}
	return s.repo.AddRescueUnitMember(ctx, member)
}

func (s *agencyService) ListRescueUnitMembers(ctx context.Context, rescueUnitID int64) ([]*domain.RescueUnitMember, error) {
	if rescueUnitID <= 0 {
		return nil, domain.ErrInvalidTargetID
	}
	return s.repo.ListRescueUnitMembers(ctx, rescueUnitID)
}

func (s *agencyService) RemoveRescueUnitMember(ctx context.Context, rescueUnitID int64, responderID int64) error {
	if rescueUnitID <= 0 || responderID <= 0 {
		return domain.ErrInvalidTargetID
	}
	return s.repo.RemoveRescueUnitMember(ctx, rescueUnitID, responderID)
}
