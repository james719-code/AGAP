package service

import (
	"context"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type AnalyticsService interface {
	GetTelemetryInsights(ctx context.Context) (*domain.TelemetryInsights, error)
}

type analyticsService struct {
	repo repository.AnalyticsRepository
}

func NewAnalyticsService(repo repository.AnalyticsRepository) AnalyticsService {
	return &analyticsService{repo: repo}
}

func (s *analyticsService) GetTelemetryInsights(ctx context.Context) (*domain.TelemetryInsights, error) {
	insights, err := s.repo.GetTelemetryInsights(ctx)
	if err != nil {
		return nil, err
	}
	insights.GeneratedAt = time.Now().UTC()
	return insights, nil
}
