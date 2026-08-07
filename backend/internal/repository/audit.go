package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type AuditRepository interface {
	LogAction(ctx context.Context, log *domain.AuditLog) error
	ListAuditLogs(ctx context.Context, filter AuditLogFilter) ([]*domain.AuditLog, error)
}

type AuditLogFilter struct {
	EntityType string
	EntityID   int64
	Action     string
	DateFrom   string
	DateTo     string
	Limit      int
}
