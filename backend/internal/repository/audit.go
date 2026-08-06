package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type AuditRepository interface {
	LogAction(ctx context.Context, log *domain.AuditLog) error
	ListAuditLogs(ctx context.Context, entityType string, entityID int64, limit int) ([]*domain.AuditLog, error)
}
