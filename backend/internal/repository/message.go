package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type MessageRepository interface {
	ListPredefinedMessages(ctx context.Context, includeInactive bool) ([]*domain.PredefinedMessage, error)
	CreatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	UpdatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	ListIncidentMessages(ctx context.Context, incidentID int64) ([]*domain.IncidentMessage, error)
	CreateIncidentMessage(ctx context.Context, message *domain.IncidentMessage) (*domain.IncidentMessage, error)
}
