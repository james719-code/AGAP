package service

import (
	"context"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
	"github.com/travelingsalesman/agap/backend/internal/websocket"
)

const (
	defaultAlertLimit = 50
	maxAlertLimit     = 200
)

type AlertService interface {
	CreateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error)
	GetAlertByID(ctx context.Context, id int64, userID *int64) (*domain.AdminAlert, error)
	GetAlertByUUID(ctx context.Context, uuid string, userID *int64) (*domain.AdminAlert, error)
	ListAlerts(ctx context.Context, userID *int64, activeOnly bool, category string, severity string, limit, offset int) ([]*domain.AdminAlert, int, error)
	UpdateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error)
	DeleteAlert(ctx context.Context, id int64) error
	MarkAlertRead(ctx context.Context, alertID int64, userID int64) error
}

type alertService struct {
	repo repository.AlertRepository
	hub  *websocket.Hub
}

func NewAlertService(repo repository.AlertRepository, hub *websocket.Hub) AlertService {
	return &alertService{repo: repo, hub: hub}
}

func (s *alertService) CreateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error) {
	normalizeAlert(alert)
	if err := validateAlert(alert); err != nil {
		return nil, err
	}
	if !alert.IsActive {
		alert.IsActive = true
	}

	created, err := s.repo.CreateAlert(ctx, alert)
	if err != nil {
		return nil, err
	}

	// Broadcast new alert to all connected WebSocket clients
	if s.hub != nil {
		s.hub.Broadcast(websocket.EventAdminAlert, map[string]interface{}{
			"action": "created",
			"alert":  created,
		})
	}

	return created, nil
}

func (s *alertService) GetAlertByID(ctx context.Context, id int64, userID *int64) (*domain.AdminAlert, error) {
	return s.repo.GetAlertByID(ctx, id, userID)
}

func (s *alertService) GetAlertByUUID(ctx context.Context, uuid string, userID *int64) (*domain.AdminAlert, error) {
	return s.repo.GetAlertByUUID(ctx, uuid, userID)
}

func (s *alertService) ListAlerts(ctx context.Context, userID *int64, activeOnly bool, category string, severity string, limit, offset int) ([]*domain.AdminAlert, int, error) {
	if limit <= 0 {
		limit = defaultAlertLimit
	}
	if limit > maxAlertLimit {
		limit = maxAlertLimit
	}
	if offset < 0 {
		offset = 0
	}
	return s.repo.ListAlerts(ctx, userID, activeOnly, category, severity, limit, offset)
}

func (s *alertService) UpdateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error) {
	normalizeAlert(alert)
	if alert.ID <= 0 {
		return nil, domain.ErrNotFound
	}
	if err := validateAlert(alert); err != nil {
		return nil, err
	}

	updated, err := s.repo.UpdateAlert(ctx, alert)
	if err != nil {
		return nil, err
	}

	if s.hub != nil {
		s.hub.Broadcast(websocket.EventAdminAlert, map[string]interface{}{
			"action": "updated",
			"alert":  updated,
		})
	}

	return updated, nil
}

func (s *alertService) DeleteAlert(ctx context.Context, id int64) error {
	if err := s.repo.DeleteAlert(ctx, id); err != nil {
		return err
	}
	if s.hub != nil {
		s.hub.Broadcast(websocket.EventAdminAlert, map[string]interface{}{
			"action": "deleted",
			"id":     id,
		})
	}
	return nil
}

func (s *alertService) MarkAlertRead(ctx context.Context, alertID int64, userID int64) error {
	return s.repo.MarkAlertRead(ctx, alertID, userID)
}

func normalizeAlert(alert *domain.AdminAlert) {
	alert.Title = strings.TrimSpace(alert.Title)
	alert.Body = strings.TrimSpace(alert.Body)
	alert.TargetRegionCode = strings.TrimSpace(alert.TargetRegionCode)
	alert.TargetProvinceCode = strings.TrimSpace(alert.TargetProvinceCode)
	alert.TargetMuniCode = strings.TrimSpace(alert.TargetMuniCode)
	alert.TargetBarangayCode = strings.TrimSpace(alert.TargetBarangayCode)
	if alert.Category == "" {
		alert.Category = domain.AlertCategoryGeneral
	}
	if alert.Severity == "" {
		alert.Severity = domain.AlertSeverityInfo
	}
}

func validateAlert(alert *domain.AdminAlert) error {
	if alert.Title == "" || alert.Body == "" {
		return domain.ErrInvalidAlert
	}
	if !alert.Category.IsValid() {
		return domain.ErrInvalidAlert
	}
	if !alert.Severity.IsValid() {
		return domain.ErrInvalidAlert
	}
	return nil
}
