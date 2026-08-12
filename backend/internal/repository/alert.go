package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type AlertRepository interface {
	// Admin CRUD
	CreateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error)
	GetAlertByID(ctx context.Context, id int64, userID *int64) (*domain.AdminAlert, error)
	GetAlertByUUID(ctx context.Context, uuid string, userID *int64) (*domain.AdminAlert, error)
	ListAlerts(ctx context.Context, userID *int64, activeOnly bool, category string, severity string, limit, offset int) ([]*domain.AdminAlert, int, error)
	UpdateAlert(ctx context.Context, alert *domain.AdminAlert) (*domain.AdminAlert, error)
	DeleteAlert(ctx context.Context, id int64) error

	// Read receipts
	MarkAlertRead(ctx context.Context, alertID int64, userID int64) error
	GetAlertReadCount(ctx context.Context, alertID int64) (int, error)
}
