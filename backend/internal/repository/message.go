package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type MessageRepository interface {
	ListPredefinedMessages(ctx context.Context, includeInactive bool) ([]*domain.PredefinedMessage, error)
	CreatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	UpdatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error)
	DeletePredefinedMessage(ctx context.Context, id int64) error
	ListIncidentMessages(ctx context.Context, incidentID int64) ([]*domain.IncidentMessage, error)
	CreateIncidentMessage(ctx context.Context, message *domain.IncidentMessage) (*domain.IncidentMessage, error)
	CanResponderAccessIncidentMessages(ctx context.Context, incidentID, userID int64) (bool, error)
	ListActiveIncidentResponderUserIDs(ctx context.Context, incidentID int64) ([]int64, error)
}
