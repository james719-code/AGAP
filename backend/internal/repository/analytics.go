package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type AnalyticsRepository interface {
	GetTelemetryInsights(ctx context.Context) (*domain.TelemetryInsights, error)
	GetOperationalReport(ctx context.Context) (*domain.OperationalReport, error)
}
