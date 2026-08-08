package repository

import (
	"context"
	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type ReportRepository interface {
	CreateReport(ctx context.Context, report *domain.IncidentReport) error
	GetReportByUUID(ctx context.Context, uuid string) (*domain.IncidentReport, error)
	GetReportByToken(ctx context.Context, token string) (*domain.IncidentReport, error)
	GetReportsByReporterUserID(ctx context.Context, userID int64) ([]*domain.IncidentReport, error)
	GetReportsByIncidentID(ctx context.Context, incidentID int64) ([]*domain.IncidentReport, error)
	EnrichReport(ctx context.Context, report *domain.IncidentReport) error
}
